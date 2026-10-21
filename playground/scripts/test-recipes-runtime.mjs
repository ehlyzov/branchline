import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import vm from 'node:vm';

const root = dirname(dirname(dirname(fileURLToPath(import.meta.url))));
const scope = vm.createContext({ console, setTimeout, clearTimeout });
for (const file of [
  'kotlin-kotlin-stdlib.js', 'Kotlin-Immutable-Collections-kotlinx-collections-immutable.js',
  'kotlinx-atomicfu.js', 'kotlinx-coroutines-core.js',
  'kotlinx-serialization-kotlinx-serialization-core.js',
  'kotlinx-serialization-kotlinx-serialization-json.js', 'branchline-interpreter.js'
]) {
  vm.runInContext(readFileSync(join(root, 'interpreter/build/dist/js/productionLibrary', file), 'utf8'), scope);
}
const facade = scope['io.github.ehlyzov.branchline:interpreter'].playground.PlaygroundFacade;
const ids = ['recipe-customer', 'prepare-order', 'recipe-sales-summary', 'recipe-incomplete-data', 'recipe-test-report', 'recipe-pipeline-guard'];
let count = 0;
let rejected = 0;
function check(label, program, input, expectation) {
  const result = facade.run(program, JSON.stringify(input), false, false);
  if (Object.hasOwn(expectation, 'expectedError')) {
    assert.equal(result.success, false, `${label}: expected rejection`);
    assert.ok(result.errorMessage.includes(expectation.expectedError), `${label}: ${result.errorMessage}`);
    assert.ok(!result.outputJson, `${label}: rejected input must not produce a payload`);
    rejected++;
  } else {
    assert.equal(result.success, true, `${label}: ${result.errorMessage}`);
    assert.deepEqual(JSON.parse(result.outputJson), expectation.expectedOutput, label);
  }
  count++;
}
for (const id of ids) {
  const fixture = JSON.parse(readFileSync(join(root, 'playground/examples', `${id}.json`), 'utf8'));
  const program = fixture.program.join('\n');
  assert.match(program, /^TRANSFORM \w+ \{[\s\S]*\n\}$/, `${id}: use a complete transform`);
  const inspected = facade.inspect(program, null);
  assert.equal(inspected.success, true, `${id}: ${inspected.diagnosticsJson}`);
  assert.equal(inspected.subsetCompatibility, 'COMPATIBLE', `${id}: advertised AI subset`);
  check(id, program, fixture.input, fixture);
  const edited = structuredClone(fixture.input);
  const path = fixture.exercise.patch.path;
  let target = edited;
  for (const part of path.slice(0, -1)) target = target[part];
  target[path.at(-1)] = fixture.exercise.patch.value;
  check(`${id}: exercise`, program, edited, fixture.exercise);
  const edges = fixture.edgeCases ?? [
    { name: 'empty items', input: { ...fixture.input, items: [] }, expectedOutput: { order_id: 'A-104', city: 'London', skus: [] } },
    { name: 'missing city', input: { ...fixture.input, shipping: {} }, expectedOutput: { order_id: 'A-104', city: 'Not provided', skus: ['BOOK'] } },
    { name: 'null city', input: { ...fixture.input, shipping: { city: null } }, expectedOutput: { order_id: 'A-104', city: 'Not provided', skus: ['BOOK'] } }
  ];
  assert.ok(edges.length > 0, `${id}: edge cases required`);
  for (const edge of edges) check(`${id}: ${edge.name}`, program, edge.input, edge);
}
console.log(`Recipe runtime checks passed: ${count} actual playground executions (${rejected} expected rejections).`);
