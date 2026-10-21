import assert from 'node:assert/strict';
import { mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import ts from 'typescript';

const root = dirname(dirname(fileURLToPath(import.meta.url)));
const tempDir = join(root, '.tmp-journey-tests');
await mkdir(tempDir, { recursive: true });
try {
  const source = await readFile(join(root, 'src/first-success.ts'), 'utf8');
  const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ES2022 } });
  const modulePath = join(tempDir, 'first-success.mjs');
  await writeFile(modulePath, compiled.outputText);
  const { currentJourneyFiles, journeyGuideUrl, journeyInstruction, RECIPE_EXAMPLE_IDS } = await import(pathToFileURL(modulePath).href);
  assert.deepEqual([...RECIPE_EXAMPLE_IDS], [
    'prepare-order', 'recipe-customer', 'recipe-sales-summary', 'recipe-incomplete-data', 'recipe-test-report', 'recipe-pipeline-guard'
  ], 'only the six complete recipes may offer CLI exports');
  for (const id of RECIPE_EXAMPLE_IDS) {
    const example = JSON.parse(await readFile(join(root, `examples/${id}.json`), 'utf8'));
    const program = Array.isArray(example.program) ? example.program.join('\n') : example.program;
    const input = structuredClone(example.input);
    const { path, value } = example.exercise.patch;
    let target = input;
    for (const key of path.slice(0, -1)) target = target[key];
    target[path.at(-1)] = value;
    const editedInput = `${JSON.stringify(input, null, 4)}\n`;
    const editedProgram = `${program}\n// My current draft\n`;
    const files = currentJourneyFiles(id, editedProgram, editedInput, 'json');
    assert.deepEqual(files.map(({ name }) => name), ['program.bl', 'input.json']);
    assert.equal(files[0].content, editedProgram, `${id}: program download must preserve current edits exactly`);
    assert.equal(files[1].content, editedInput, `${id}: input download must preserve current edits exactly`);
    assert.deepEqual(JSON.parse(files[1].content), input, `${id}: download must contain the exercise patch`);
    assert.equal(currentJourneyFiles(id, editedProgram, '{broken', 'json'), null, `${id}: invalid JSON disables downloads`);
    assert.equal(currentJourneyFiles(id, editedProgram, '<order/>', 'xml'), null);
    assert.equal(currentJourneyFiles(id, 'TRANSFORM Broken {', '{}', 'json')[0].content, 'TRANSFORM Broken {', 'exporting must not silently repair or replace program edits');
    assert.equal(journeyInstruction(id, example.exercise), example.exercise.instruction);
    assert.equal(journeyGuideUrl(id), `../recipes/${id}/`);
    assert.equal(journeyInstruction(id), null);
    assert.ok('expectedOutput' in example.exercise || 'expectedError' in example.exercise, `${id}: exercise has a documented result`);
  }
  assert.equal(currentJourneyFiles('customer-profile', 'OUTPUT input', '{}', 'json'), null, 'body-only catalog examples must not be exported as complete CLI programs');
  assert.equal(currentJourneyFiles('unknown-recipe', 'TRANSFORM Example { OUTPUT input }', '{}', 'json'), null);
  assert.equal(journeyInstruction('customer-profile', { instruction: 'Order-only instruction' }), null);
  assert.equal(journeyGuideUrl('unknown-recipe'), null);
  console.log('Six-recipe exercise and current-edit download checks passed.');
} finally {
  await rm(tempDir, { recursive: true, force: true });
}
