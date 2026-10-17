import assert from 'node:assert/strict';
import { mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import ts from 'typescript';

const root = dirname(dirname(fileURLToPath(import.meta.url)));
const sourcePath = join(root, 'src', 'xml-input.ts');
const tempDir = join(root, '.tmp-xml-tests');
const tempModule = join(tempDir, 'xml-input.mjs');

await mkdir(tempDir, { recursive: true });
try {
  const source = await readFile(sourcePath, 'utf8');
  const output = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.ES2022, target: ts.ScriptTarget.ES2022, strict: true },
    fileName: sourcePath
  });
  await writeFile(tempModule, output.outputText);
  const { prepareXmlInput } = await import(`${pathToFileURL(tempModule).href}?v=${Date.now()}`);

  for (const input of [
    '', '   ', 'hello', '<!-- no root -->', '<root><amount>25</root>',
    '<root>unclosed', '<first>1</first><second>2</second>', '<first/><second/>',
    '<first/>text', 'text<first/>', '<root enabled/>'
  ]) {
    assert.throws(() => prepareXmlInput(input), /Invalid XML input/, input);
  }
  assert.throws(() => prepareXmlInput('<root>\n<amount>25</root>'), /line 2, column \d+/);

  const normalized = (input) => JSON.parse(prepareXmlInput(input));
  assert.deepEqual(normalized('<root/>'), { root: '' });
  assert.deepEqual(normalized('<root id="007"><value>001</value><value>2</value></root>'), {
    root: { '@id': '007', value: [{ $: '001', '#text': '001' }, { $: '2', '#text': '2' }] }
  });
  assert.deepEqual(normalized('<root xmlns="urn:default" xmlns:n="urn:n"><n:item ok="true">text</n:item></root>'), {
    root: { '@xmlns': { $: 'urn:default', n: 'urn:n' }, 'n:item': { '@ok': 'true', $: 'text', '#text': 'text' } }
  });
  assert.deepEqual(normalized('<?xml version="1.0"?><?ready now?><!-- note --><root> before <b>bold</b> after </root>'), {
    root: { b: { $: 'bold', '#text': 'bold' }, $1: 'before', $2: 'after' }
  });
  assert.deepEqual(normalized('<root><![CDATA[ a < b ]]></root>'), {
    root: { $: 'a < b', '#text': 'a < b' }
  });
  assert.deepEqual(normalized('\uFEFF<?xml version="1.0"?><root>text</root>'), {
    root: { $: 'text', '#text': 'text' }
  });
  assert.deepEqual(normalized('<!DOCTYPE root [<!ENTITY value "001">]><root>&value; &amp; &#65;</root>'), {
    root: { $: '001 & &#65;', '#text': '001 & &#65;' }
  });

  // Every checked-in XML example must still normalize to an object.
  const { readdir } = await import('node:fs/promises');
  for (const file of await readdir(join(root, 'examples'))) {
    if (!file.endsWith('.json')) continue;
    const example = JSON.parse(await readFile(join(root, 'examples', file), 'utf8'));
    if (example.inputFormat === 'xml') {
      assert.equal(typeof normalized(example.input), 'object', file);
    }
  }
  console.log('XML input validation and normalization checks passed.');
} finally {
  await rm(tempDir, { recursive: true, force: true });
}
