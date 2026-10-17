import { XMLParser, XMLValidator } from 'fast-xml-parser';

const xmlParser = new XMLParser({
  preserveOrder: true,
  ignoreAttributes: false,
  attributeNamePrefix: '@',
  textNodeName: '#text',
  trimValues: true,
  parseTagValue: false,
  parseAttributeValue: false,
  ignoreDeclaration: true,
  ignorePiTags: true
});

export function prepareXmlInput(raw: string): string {
  const validation = XMLValidator.validate(raw);
  if (validation !== true) {
    const { msg, line, col } = validation.err;
    const location = col == null ? `line ${line}` : `line ${line}, column ${col}`;
    throw new Error(`Invalid XML input (${location}): ${msg}`);
  }

  // The parser otherwise discards text outside the root, and XMLValidator accepts
  // multiple self-closing roots. A synthetic container makes both observable.
  const parsed: unknown = xmlParser.parse(`<branchline-document>${raw}</branchline-document>`);
  const document = asDynamicArray(parsed)?.[0] as Record<string, unknown> | undefined;
  const entries = asDynamicArray(document?.['branchline-document']) ?? [];
  const hasOutsideText = entries.some((entry) => {
    const text = entry != null && typeof entry === 'object'
      ? dynamicString((entry as Record<string, unknown>)['#text'])
      : null;
    return text != null && text.trim().length > 0;
  });
  const roots = entries
    .map(parseOrderedXmlEntry)
    .filter((root): root is XmlElementNode => root != null);
  if (roots.length !== 1 || hasOutsideText) {
    throw new Error('Invalid XML input: provide exactly one root element.');
  }
  return JSON.stringify(mapXmlInput(roots[0]));
}

type XmlElementNode = {
  name: string;
  attributes: Record<string, string>;
  namespaces: Record<string, string>;
  children: XmlNodeChild[];
};

type XmlNodeChild =
  | { kind: 'element'; value: XmlElementNode }
  | { kind: 'text'; value: string };

type ParsedXmlAttributes = {
  attributes: Record<string, string>;
  namespaces: Record<string, string>;
};

function parseOrderedXmlEntry(entry: unknown): XmlElementNode | null {
  if (entry == null || typeof entry !== 'object') {
    return null;
  }
  const dynamicEntry = entry as Record<string, unknown>;
  const keys = Object.keys(dynamicEntry);
  let elementName: string | null = null;
  for (const key of keys) {
    if (key === ':@' || key === '#text') continue;
    elementName = key;
    break;
  }
  if (elementName == null) {
    return null;
  }
  const parsedAttributes = parseOrderedXmlAttributes(dynamicEntry[':@']);
  const children = parseOrderedXmlChildren(dynamicEntry[elementName]);
  return {
    name: elementName,
    attributes: parsedAttributes.attributes,
    namespaces: parsedAttributes.namespaces,
    children
  };
}

function parseOrderedXmlChildren(value: unknown): XmlNodeChild[] {
  const entries = asDynamicArray(value);
  if (entries == null) {
    return [];
  }
  const children: XmlNodeChild[] = [];
  for (const entry of entries) {
    if (entry == null || typeof entry !== 'object') continue;
    const dynamicEntry = entry as Record<string, unknown>;
    const text = dynamicString(dynamicEntry['#text']);
    if (text != null) {
      children.push({ kind: 'text', value: text });
      continue;
    }
    const element = parseOrderedXmlEntry(dynamicEntry);
    if (element != null) {
      children.push({ kind: 'element', value: element });
    }
  }
  return children;
}

function parseOrderedXmlAttributes(value: unknown): ParsedXmlAttributes {
  const attributes: Record<string, string> = {};
  const namespaces: Record<string, string> = {};
  if (value == null || typeof value !== 'object') {
    return { attributes, namespaces };
  }
  const dynamicValue = value as Record<string, unknown>;
  for (const key of Object.keys(dynamicValue)) {
    const attrName = key.startsWith('@') ? key.substring(1) : key;
    const attrValue = dynamicString(dynamicValue[key]) ?? '';
    if (attrName === 'xmlns') {
      namespaces['$'] = attrValue;
    } else if (attrName.startsWith('xmlns:')) {
      namespaces[attrName.substring('xmlns:'.length)] = attrValue;
    } else {
      attributes[attrName] = attrValue;
    }
  }
  return { attributes, namespaces };
}

function mapXmlInput(root: XmlElementNode): Record<string, unknown> {
  return {
    [root.name]: mapXmlElement(root)
  };
}

function mapXmlElement(node: XmlElementNode): unknown {
  const result: Record<string, unknown> = {};
  if (Object.keys(node.namespaces).length > 0) {
    result['@xmlns'] = { ...node.namespaces };
  }
  for (const [name, value] of Object.entries(node.attributes)) {
    result[`@${name}`] = value;
  }
  const textSegments: string[] = [];
  let hasElementChildren = false;
  for (const child of node.children) {
    if (child.kind === 'element') {
      hasElementChildren = true;
      appendElementValue(result, child.value.name, mapXmlElement(child.value));
      continue;
    }
    const normalized = child.value.trim();
    if (normalized.length > 0) {
      textSegments.push(normalized);
    }
  }
  if (hasElementChildren) {
    appendMixedText(result, textSegments);
  } else if (textSegments.length > 0) {
    appendPureText(result, textSegments);
  }
  return Object.keys(result).length === 0 ? '' : result;
}

function appendElementValue(result: Record<string, unknown>, name: string, value: unknown): void {
  const existing = result[name];
  if (existing == null) {
    result[name] = value;
    return;
  }
  if (Array.isArray(existing)) {
    existing.push(value);
    return;
  }
  result[name] = [existing, value];
}

function appendMixedText(result: Record<string, unknown>, segments: string[]): void {
  let index = 1;
  for (const segment of segments) {
    result[`$${index}`] = segment;
    index += 1;
  }
}

function appendPureText(result: Record<string, unknown>, segments: string[]): void {
  const value = segments.length === 1 ? segments[0] : segments.join('');
  result['$'] = value;
  result['#text'] = value;
}

function asDynamicArray(value: unknown): unknown[] | null {
  return Array.isArray(value) ? value : null;
}

function dynamicString(value: unknown): string | null {
  if (value == null) return null;
  return String(value);
}
