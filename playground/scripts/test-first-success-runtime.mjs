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
let count = 0;
function check(label, program, input, expected) {
  const result = facade.run(program, JSON.stringify(input), false, false);
  assert.equal(result.success, true, `${label}: ${result.errorMessage}`);
  assert.deepEqual(JSON.parse(result.outputJson), expected, label);
  count++;
}
const fixture = JSON.parse(readFileSync(join(root, 'playground/examples/prepare-order.json'), 'utf8'));
const program = fixture.program.join('\n');
check('starter', program, fixture.input, fixture.expectedOutput);
const edited = structuredClone(fixture.input);
edited.items[1].qty = fixture.exercise.patch.value;
check('starter exercise', program, edited, fixture.exercise.expectedOutput);
check('starter empty', program, { ...fixture.input, items: [] }, { order_id: 'A-104', city: 'London', skus: [] });
check('starter missing city', program, { ...fixture.input, shipping: {} }, { order_id: 'A-104', city: 'Not provided', skus: ['BOOK'] });

for (const name of ['paths-and-reshaping', 'control-flow', 'error-handling']) {
  const text = readFileSync(join(root, `docs/learn/${name}.md`), 'utf8');
  const programs = [...text.matchAll(/```branchline\n([\s\S]*?)\n```/g)];
  const json = [...text.matchAll(/```json\n([\s\S]*?)\n```/g)];
  assert.equal(programs.length, 1, name);
  assert.equal(json.length, 2, name);
  const source = programs[0][1];
  check(name, source, JSON.parse(json[0][1]), JSON.parse(json[1][1]));
  if (name === 'paths-and-reshaping') {
    check('paths missing leaf', source, { order: { customer: {}, shipping: {} } }, { orderId: null, customerId: null, shipTo: null, items: null });
  } else if (name === 'control-flow') {
    check('control success', source, { build: { status: 'success' } }, { deploy: true, status: 'success' });
    check('control missing status', source, { build: {} }, { deploy: false, status: null, reason: null });
  } else {
    check('guard keeps zero', source, { payload: { value: 0 } }, { value: 0, guard: true });
    check('guard catches null', source, { payload: { value: null } }, { value: null, guard: { ok: false, error: 'Missing value' } });
  }
}
console.log(`First-success and tour runtime checks passed: ${count} actual playground executions.`);
