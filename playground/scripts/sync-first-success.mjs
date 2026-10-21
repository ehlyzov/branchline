import assert from 'node:assert/strict';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = dirname(dirname(dirname(fileURLToPath(import.meta.url))));
const check = process.argv.includes('--check');
const fixturePath = 'playground/examples/prepare-order.json';
const fixture = JSON.parse(await readFile(join(root, fixturePath), 'utf8'));
assert.equal(fixture.id, 'prepare-order');
assert.match(fixture.program.join('\n'), /^TRANSFORM Shipment \{[\s\S]*\n\}$/);
assert.equal(fixture.input.items[1].qty, 0);
assert.deepEqual(fixture.exercise.patch, { path: ['items', 1, 'qty'], value: 1 });
const project = (input) => ({
  order_id: input.id,
  city: input.shipping?.city ?? 'Not provided',
  skus: input.items.filter((item) => item.qty > 0).map((item) => item.sku)
});
assert.deepEqual(fixture.expectedOutput, project(fixture.input));
const editedInput = structuredClone(fixture.input);
editedInput.items[1].qty = fixture.exercise.patch.value;
assert.deepEqual(fixture.exercise.expectedOutput, project(editedInput));
assert.deepEqual(fixture.expectedOutput.skus, ['BOOK']);
assert.deepEqual(fixture.exercise.expectedOutput.skus, ['BOOK', 'PEN']);

const json = (value) => `${JSON.stringify(value, null, 2)}\n`;
// Compact lines keep the before/after readable together on the home page.
const inputSnippet = `{
  "id": ${JSON.stringify(fixture.input.id)},
  "shipping": ${JSON.stringify(fixture.input.shipping)},
  "items": [
    ${fixture.input.items.map((item) => JSON.stringify(item)).join(',\n    ')}
  ]
}\n`;
const outputSnippet = (value) => `{
  "order_id": ${JSON.stringify(value.order_id)},
  "city": ${JSON.stringify(value.city)},
  "skus": ${JSON.stringify(value.skus)}
}\n`;
const program = `${fixture.program.join('\n')}\n`;
const fenced = (language, content) => `<!-- Generated from ${fixturePath}; run npm run sync:first-success in playground/. -->\n\n\`\`\`${language}\n${content}\`\`\`\n`;
const outputs = new Map([
  ['docs/downloads/prepare-order/program.bl', program],
  ['docs/downloads/prepare-order/input.json', json(fixture.input)],
  ['docs/downloads/prepare-order/expected.json', json(fixture.expectedOutput)],
  ['docs/includes/prepare-order/program.md', fenced('branchline', program)],
  ['docs/includes/prepare-order/input.md', fenced('json', inputSnippet)],
  ['docs/includes/prepare-order/expected.md', fenced('json', outputSnippet(fixture.expectedOutput))],
  ['docs/includes/prepare-order/exercise-expected.md', fenced('json', outputSnippet(fixture.exercise.expectedOutput))]
]);
for (const [relativePath, expected] of outputs) {
  const absolutePath = join(root, relativePath);
  if (check) {
    const actual = await readFile(absolutePath, 'utf8').catch(() => null);
    assert.equal(actual, expected, `${relativePath} is stale; run npm run sync:first-success`);
  } else {
    await mkdir(dirname(absolutePath), { recursive: true });
    await writeFile(absolutePath, expected);
  }
}
if (check) {
  const guide = await readFile(join(root, 'docs/guides/getting-started.md'), 'utf8');
  for (const id of ['hello', 'defaults', 'available-items']) {
    for (const kind of ['program', 'input', 'output']) {
      assert.equal(guide.split(`<!-- onboarding:${id}:${kind} -->`).length - 1, 1);
    }
  }
  assert.equal((guide.match(/^```branchline$/gm) ?? []).length, 3,
    'Keep the three executable onboarding programs; include the canonical starter from its generated snippet');
  for (const name of ['program', 'input', 'expected', 'exercise-expected']) {
    assert.ok(guide.includes(`includes/prepare-order/${name}.md`), `Missing canonical ${name} include`);
  }
  assert.ok(guide.includes('## Run locally'));
  const install = await readFile(join(root, 'docs/guides/install.md'), 'utf8');
  for (const filename of ['program.bl', 'input.json']) {
    assert.ok(install.includes(`/downloads/prepare-order/${filename}`));
  }
  for (const asset of ['branchline-cli-v1.0.0-rc.1-all.jar', 'branchline-cli-js-v1.0.0-rc.1.tgz']) {
    assert.ok(install.includes(`/v1.0.0-rc.1/${asset}`), `Missing pinned release asset ${asset}`);
  }
  assert.ok(!install.includes('<tag>'));
  for (const path of [
    'guides/getting-started.md', 'guides/first-steps.md', 'guides/install.md',
    'learn/index.md', 'learn/hello-transform.md', 'learn/paths-and-reshaping.md',
    'learn/control-flow.md', 'learn/error-handling.md'
  ]) {
    const text = await readFile(join(root, 'docs', path), 'utf8');
    assert.doesNotMatch(text, /`INPUT`|`\$`|\bINPUT\s*\.|\$\.[A-Za-z_]/,
      `${path} must teach the supported input root`);
  }
}
console.log(`${check ? 'Checked' : 'Generated'} first-success fixture, ${outputs.size} downloads/snippets and default/exercise expectations.`);
