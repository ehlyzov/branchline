type InputFormat = 'json' | 'xml';

type SharedStorageSpec = {
  name: string;
  kind: 'SINGLE' | 'MANY';
};

type WorkerRequest = {
  code: string;
  input: string;
  trace: boolean;
  inputFormat: InputFormat;
  shared: SharedStorageSpec[];
};

type WorkerResponse = {
  success: boolean;
  outputJson: string | null;
  errorMessage: string | null;
  line: number | null;
  column: number | null;
  explainJson: string | null;
  explainHuman: string | null;
};

import kotlinStdlibUrl from '../../interpreter/build/dist/js/productionLibrary/kotlin-kotlin-stdlib.js?url';
import serializationCoreUrl from '../../interpreter/build/dist/js/productionLibrary/kotlinx-serialization-kotlinx-serialization-core.js?url';
import serializationJsonUrl from '../../interpreter/build/dist/js/productionLibrary/kotlinx-serialization-kotlinx-serialization-json.js?url';
import interpreterUrl from '../../interpreter/build/dist/js/productionLibrary/branchline-interpreter.js?url';
import { XMLParser } from 'fast-xml-parser';

type PlaygroundFacade = {
  run(program: string, inputJson: string, enableTracing: boolean): WorkerResponse;
  runWithShared?(
    program: string,
    inputJson: string,
    enableTracing: boolean,
    sharedJsonConfig: string | null
  ): WorkerResponse;
};

const INTERPRETER_GLOBAL = 'io.github.ehlyzov.branchline-public:interpreter';

let facadePromise: Promise<PlaygroundFacade> | null = null;
const loadedScripts = new Set<string>();

async function loadScript(url: string): Promise<void> {
  if (loadedScripts.has(url)) {
    return;
  }

  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`Failed to load interpreter bundle: ${url}`);
  }

  const source = await response.text();
  (0, eval)(source);
  loadedScripts.add(url);
}

function loadFacade(): Promise<PlaygroundFacade> {
  if (!facadePromise) {
    facadePromise = (async () => {
      const globalScope = self as typeof self & Record<string, unknown>;

      if (!globalScope[INTERPRETER_GLOBAL]) {
        await loadScript(kotlinStdlibUrl);
        await loadScript(serializationCoreUrl);
        await loadScript(serializationJsonUrl);
        await loadScript(interpreterUrl);
      }

      const moduleRoot = globalScope[INTERPRETER_GLOBAL] as Record<string, unknown> | undefined;
      if (!moduleRoot) {
        throw new Error('Branchline interpreter module failed to initialize.');
      }

      const playgroundNamespace = (moduleRoot['playground'] ?? moduleRoot) as Record<string, unknown>;
      const facadeEntry = playgroundNamespace['PlaygroundFacade'];

      if (facadeEntry && typeof facadeEntry === 'object' && typeof (facadeEntry as PlaygroundFacade).run === 'function') {
        return facadeEntry as PlaygroundFacade;
      }

      if (typeof facadeEntry === 'function') {
        try {
          const maybeInstance = (facadeEntry as () => PlaygroundFacade)();
          if (maybeInstance && typeof maybeInstance.run === 'function') {
            return maybeInstance;
          }
        } catch (_error) {
          // ignore and fall back to getter resolution
        }
      }

      const getter = playgroundNamespace['PlaygroundFacade_getInstance'];
      if (typeof getter === 'function') {
        const instance = (getter as () => PlaygroundFacade)();
        if (instance && typeof instance.run === 'function') {
          return instance;
        }
      }

      throw new Error('Unable to resolve Branchline playground facade.');
    })();
  }
  return facadePromise;
}

self.onmessage = async (event: MessageEvent<WorkerRequest>) => {
  const { code, input, trace, inputFormat, shared } = event.data;
  const sharedOffset = shared.length ? shared.length + 1 : 0;
  const wrapperAdjustment = computeWrapperAdjustment(code, sharedOffset);
  try {
    const runner = await loadFacade();
    const payload = prepareInput(input, inputFormat);
    const sharedJson = shared.length ? JSON.stringify(shared) : null;
    const result = runner.runWithShared
      ? runner.runWithShared(code, payload, trace, sharedJson)
      : runner.run(code, payload, trace);
    const adjusted = adjustResultForWrapper(result, wrapperAdjustment);
    self.postMessage(adjusted satisfies WorkerResponse);
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    const fallback: WorkerResponse = {
      success: false,
      outputJson: null,
      errorMessage: message,
      line: null,
      column: null,
      explainJson: null,
      explainHuman: null
    };
    self.postMessage(fallback);
  }
};

export {};

const xmlParser = new XMLParser({
  ignoreAttributes: false,
  attributeNamePrefix: '@',
  textNodeName: '#text',
  trimValues: true,
  parseTagValue: false,
  parseAttributeValue: false
});

function prepareInput(raw: string, format: InputFormat): string {
  if (format === 'xml') {
    if (!raw.trim()) {
      return '{}';
    }

    let parsed: unknown;
    try {
      parsed = xmlParser.parse(raw);
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      throw new Error(`Failed to parse XML input: ${message}`);
    }

    const normalized = normalizeXml(parsed);
    if (!normalized || Array.isArray(normalized) || typeof normalized !== 'object') {
      throw new Error('XML input must produce an object at the top level.');
    }

    return JSON.stringify(normalized);
  }

  return raw;
}

function normalizeXml(value: unknown): unknown {
  if (value === null || value === undefined) {
    return null;
  }
  if (Array.isArray(value)) {
    return value.map((item) => normalizeXml(item));
  }
  if (typeof value === 'object') {
    const entries = Object.entries(value as Record<string, unknown>).map(([key, entryValue]) => [
      key,
      normalizeXml(entryValue)
    ]);
    return Object.fromEntries(entries);
  }
  if (typeof value === 'number' || typeof value === 'boolean') {
    return String(value);
  }
  return value;
}

type WrapperAdjustment = {
  lineOffset: number;
  lineMap: number[];
  indentedLines: boolean[];
  originalLineCount: number;
  lastContentLine: number;
};

function computeWrapperAdjustment(code: string, sharedOffset: number): WrapperAdjustment | null {
  if (/\bTRANSFORM\b/i.test(code)) {
    return null;
  }

  const originalLines = code.split(/\r?\n/);

  let firstContent = 0;
  while (firstContent < originalLines.length && originalLines[firstContent].trim() === '') {
    firstContent += 1;
  }

  let lastContent = originalLines.length - 1;
  while (lastContent >= firstContent && originalLines[lastContent].trim() === '') {
    lastContent -= 1;
  }

  const hasContent = firstContent <= lastContent;
  const trimmedLines = hasContent
    ? originalLines.slice(firstContent, lastContent + 1)
    : ([] as string[]);
  const lineMap = trimmedLines.map((_, index) => firstContent + index + 1);
  const indentedLines = trimmedLines.map((line) => line.trim().length > 0);

  return {
    lineOffset: 3 + sharedOffset,
    lineMap,
    indentedLines,
    originalLineCount: originalLines.length || 1,
    lastContentLine: hasContent ? lastContent + 1 : 1
  };
}

function adjustResultForWrapper(
  result: WorkerResponse,
  adjustment: WrapperAdjustment | null
): WorkerResponse {
  if (!adjustment || result.success || result.line == null) {
    return result;
  }

  const relativeLine = result.line - adjustment.lineOffset;
  if (relativeLine < 1) {
    return result;
  }

  const trimmedIndex = relativeLine - 1;
  let line = result.line;
  let column = result.column;

  if (trimmedIndex >= 0 && trimmedIndex < adjustment.lineMap.length) {
    line = adjustment.lineMap[trimmedIndex];
    if (column != null && adjustment.indentedLines[trimmedIndex]) {
      column = Math.max(1, column - 4);
    }
  } else {
    line = Math.min(adjustment.originalLineCount, adjustment.lastContentLine);
  }

  return {
    ...result,
    line,
    column
  };
}
