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
  const { currentJourneyFiles, journeyInstruction } = await import(pathToFileURL(modulePath).href);
  const example = JSON.parse(await readFile(join(root, 'examples/prepare-order.json'), 'utf8'));
  const program = Array.isArray(example.program) ? example.program.join('\n') : example.program;
  const input = structuredClone(example.input);
  input.items[1].qty = 1;
  const editedInput = `${JSON.stringify(input, null, 4)}\n`;
  const editedProgram = `${program}\n// My current draft\n`;
  const files = currentJourneyFiles('prepare-order', editedProgram, editedInput, 'json');
  assert.deepEqual(files.map(({ name }) => name), ['program.bl', 'input.json']);
  assert.equal(files[0].content, editedProgram, 'program download must preserve current edits exactly');
  assert.equal(files[1].content, editedInput, 'input download must preserve current edits exactly');
  assert.equal(JSON.parse(files[1].content).items[1].qty, 1, 'download must contain the exercised quantity');
  assert.equal(currentJourneyFiles('prepare-order', editedProgram, '{broken', 'json'), null);
  assert.equal(currentJourneyFiles('prepare-order', editedProgram, '<order/>', 'xml'), null);
  assert.equal(currentJourneyFiles('customer-profile', 'OUTPUT input', '{}', 'json'), null, 'body-only catalog examples must not be exported as complete CLI programs');
  assert.equal(currentJourneyFiles('prepare-order', 'TRANSFORM Broken {', '{}', 'json')[0].content, 'TRANSFORM Broken {', 'exporting must not silently repair or replace program edits');
  assert.equal(journeyInstruction('prepare-order', example.exercise), example.exercise.instruction);
  assert.equal(journeyInstruction('customer-profile', example.exercise), null, 'starter instruction must disappear on another example');
  console.log('First-success exercise and current-edit download checks passed.');
} finally {
  await rm(tempDir, { recursive: true, force: true });
}
