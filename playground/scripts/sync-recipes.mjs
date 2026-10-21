import assert from 'node:assert/strict';
import { mkdir, readFile, readdir, writeFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = dirname(dirname(dirname(fileURLToPath(import.meta.url))));
const check = process.argv.includes('--check');
const ids = ['recipe-customer', 'prepare-order', 'recipe-sales-summary', 'recipe-incomplete-data', 'recipe-test-report', 'recipe-pipeline-guard'];
const orderGuide = {
  problem: 'Prepare a shipment preview containing an order identifier, a city, and only the SKUs whose quantities are positive.',
  prerequisites: ['id is an order identifier. shipping must be an object; city is optional text or null.', 'items is an array of objects with sku text and numeric qty. The input producer supplies these required fields.'],
  explanation: ['order_id copies input.id. The output explicitly selects three fields, leaving other order data out.', 'The comprehension keeps only items with qty > 0 and returns their sku values in input order.', 'An absent or null city becomes the display label "Not provided". The label is not a deliverable address; the payload is a shipment preview.'],
  limits: ['This starter assumes valid item objects and a shipping object; it does not validate them.', 'Zero and negative quantities are excluded. Duplicate SKUs are preserved, and quantities are not included in the result.', 'Validate a real shipping address before using the result for fulfillment.']
};
const json = (value) => `${JSON.stringify(value, null, 2)}\n`;
const fence = (language, content) => `\`\`\`${language}\n${content}\`\`\`\n`;
const outputs = new Map();
function output(path, content) {
  assert.ok(!outputs.has(path), `Duplicate generated path: ${path}`);
  outputs.set(path, content);
}
function expectation(value, label) {
  assert.equal(Object.hasOwn(value, 'expectedOutput') !== Object.hasOwn(value, 'expectedError'), true,
    `${label}: require exactly one expectedOutput or expectedError`);
  if (Object.hasOwn(value, 'expectedError')) assert.equal(typeof value.expectedError, 'string', label);
}
for (const id of ids) {
  const source = `playground/examples/${id}.json`;
  const fixture = JSON.parse(await readFile(join(root, source), 'utf8'));
  assert.equal(fixture.id, id);
  assert.match(fixture.program.join('\n'), /^TRANSFORM \w+ \{[\s\S]*\n\}$/);
  assert.ok(fixture.program.every((line) => !line.includes(';')), `${id}: semicolon-free program`);
  assert.ok(Object.hasOwn(fixture, 'expectedOutput'), `${id}: base result required`);
  expectation(fixture, id);
  assert.ok(fixture.exercise.instruction.length > 0, `${id}: exercise instruction required`);
  expectation(fixture.exercise, `${id}: exercise`);
  const edited = structuredClone(fixture.input);
  const { path, value } = fixture.exercise.patch;
  assert.ok(Array.isArray(path) && path.length > 0, `${id}: patch path required`);
  let target = edited;
  for (const part of path.slice(0, -1)) {
    assert.ok(target !== null && Object.hasOwn(target, part), `${id}: patch parent must exist`);
    target = target[part];
  }
  target[path.at(-1)] = value;
  assert.notDeepEqual(edited, fixture.input, `${id}: exercise must change the input`);
  const metadata = fixture.recipe ?? orderGuide;
  for (const key of ['problem', 'prerequisites', 'explanation', 'limits']) assert.ok(metadata[key]?.length, `${id}: ${key} required`);
  const edges = fixture.edgeCases ?? [
    { name: 'Empty items', input: { ...fixture.input, items: [] }, expectedOutput: { order_id: 'A-104', city: 'London', skus: [] } },
    { name: 'Missing city', input: { ...fixture.input, shipping: {} }, expectedOutput: { order_id: 'A-104', city: 'Not provided', skus: ['BOOK'] } },
    { name: 'Null city', input: { ...fixture.input, shipping: { city: null } }, expectedOutput: { order_id: 'A-104', city: 'Not provided', skus: ['BOOK'] } }
  ];
  assert.ok(edges.length, `${id}: edge cases required`);
  for (const edge of edges) {
    assert.ok(edge.name?.length && Object.hasOwn(edge, 'input'), `${id}: named input edge required`);
    expectation(edge, `${id}: ${edge.name}`);
  }
  const program = `${fixture.program.join('\n')}\n`;
  const header = `<!-- Generated from ${source}; run npm run sync:recipes in playground/. -->\n\n`;
  const snippets = {
    program: fence('branchline', program),
    input: fence('json', json(fixture.input)),
    expected: fence('json', json(fixture.expectedOutput)),
    'exercise-input': fence('json', json(edited)),
    'exercise-expected': Object.hasOwn(fixture.exercise, 'expectedError')
      ? `The run must fail with this message and produce no result payload:\n\n${fence('text', `${fixture.exercise.expectedError}\n`)}`
      : fence('json', json(fixture.exercise.expectedOutput)),
    'edge-cases': edges.map((edge) => `- **${edge.name}:** ${Object.hasOwn(edge, 'expectedError') ? `reject with \`${edge.expectedError}\`.` : `produce \`${JSON.stringify(edge.expectedOutput)}\`.`}`).join('\n') + '\n'
  };
  for (const [name, body] of Object.entries(snippets)) output(`docs/includes/recipes/${id}/${name}.md`, header + body);
  if (id !== 'prepare-order') {
    output(`docs/downloads/${id}/program.bl`, program);
    output(`docs/downloads/${id}/input.json`, json(fixture.input));
    output(`docs/downloads/${id}/expected.json`, json(fixture.expectedOutput));
    output(`docs/downloads/${id}/exercise-input.json`, json(edited));
    output(`docs/downloads/${id}/edge-cases.json`, json(edges));
    if (Object.hasOwn(fixture.exercise, 'expectedError')) output(`docs/downloads/${id}/exercise-error.txt`, `${fixture.exercise.expectedError}\n`);
    else output(`docs/downloads/${id}/exercise-expected.json`, json(fixture.exercise.expectedOutput));
  }
  const include = (name) => `--8<-- "includes/recipes/${id}/${name}.md"`;
  const bullets = (values) => values.map((text) => `- ${text}`).join('\n');
  output(`docs/recipes/${id}.md`, `---
title: ${JSON.stringify(fixture.title)}
---

${header}# ${fixture.title}

${metadata.problem}

[Open this recipe in the Playground](../playground.md?example=${id}){ .md-button }

Prerequisites:

${bullets(metadata.prerequisites)}

## Program

Save this complete transform as [program.bl](../downloads/${id}/program.bl):

${include('program')}

## Input example

Save this JSON as [input.json](../downloads/${id}/input.json):

${include('input')}

## Expected output

${include('expected')}

Compare JSON values; whitespace and object key order can differ.
[Download the expected result](../downloads/${id}/expected.json).

## Why this works

${bullets(metadata.explanation)}

## Validation commands

In the Playground, select **Run** and compare the Result with the expected output.
Then try the edit below. Enable tracing only if you want to inspect execution.

First follow the [one-command installation guide](../guides/install.md).
**No preinstalled Java or Node is needed:** it downloads a private runtime and the
published **v1.0.0-rc.1** CLI as \`bl\`, and explains runtime/platform limits.

**Already downloaded your edited files from the Playground?** Keep
\`program.bl\` and \`input.json\` together, skip the original downloads below, and run:

\`\`\`bash
bl program.bl --input input.json
\`\`\`

Use the expected result for your edit (see **Try one edit**), rather than the
original result above. For an ASSERT rejection, the CLI exits with an error and
emits no result JSON.

### Optional: get the original example

Only if you have no files yet, download fresh copies into a new directory.
These commands overwrite files with the same names:

\`\`\`bash
curl -fL -o program.bl https://ehlyzov.github.io/branchline/downloads/${id}/program.bl
curl -fL -o input.json https://ehlyzov.github.io/branchline/downloads/${id}/input.json
bl program.bl --input input.json
\`\`\`

With these original files, compare with **Expected output** above.

## Try one edit

${fixture.exercise.instruction}
Keep the program unchanged and use this edited input:

${include('exercise-input')}

Expected result after the edit:

${include('exercise-expected')}

${id === 'prepare-order' ? 'Edit the JSON input in the browser, or save the edited JSON over input.json and rerun the same local command.' : `You can also download [exercise-input.json](../downloads/${id}/exercise-input.json)
and replace \`--input input.json\` with \`--input exercise-input.json\` in either command.`}

## Edge cases and limits

${include('edge-cases')}

${bullets(metadata.limits)}

[Choose another recipe](../learn/recipes.md) · [Browse all examples](../guides/examples.md)
`);
}
// Detect forgotten or renamed recipe pages/includes/downloads as well as changed contents.
const walk = async (dir) => {
  const entries = await readdir(join(root, dir), { withFileTypes: true }).catch((error) => {
    if (error.code === 'ENOENT') return [];
    throw error;
  });
  const files = [];
  for (const entry of entries) {
    const path = `${dir}/${entry.name}`;
    if (entry.isDirectory()) files.push(...await walk(path));
    else files.push(path);
  }
  return files;
};
for (const dir of ['docs/recipes', 'docs/includes/recipes', ...ids.filter((id) => id !== 'prepare-order').map((id) => `docs/downloads/${id}`)]) {
  for (const file of await walk(dir)) assert.ok(outputs.has(file), `Unexpected generated recipe file: ${file}`);
}
for (const [path, expected] of outputs) {
  const absolute = join(root, path);
  if (check) {
    const actual = await readFile(absolute, 'utf8').catch((error) => {
      if (error.code === 'ENOENT') return null;
      throw error;
    });
    assert.equal(actual, expected, `${path} is stale; run npm run sync:recipes`);
  } else {
    await mkdir(dirname(absolute), { recursive: true });
    await writeFile(absolute, expected);
  }
}
console.log(`${check ? 'Checked' : 'Generated'} ${ids.length} recipes and ${outputs.size} documentation/download files.`);
