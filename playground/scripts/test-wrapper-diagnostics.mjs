import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import vm from 'node:vm';
import ts from 'typescript';

const root = dirname(dirname(fileURLToPath(import.meta.url)));
const source = readFileSync(join(root, 'src/wrapper-diagnostics.ts'), 'utf8');
const compiled = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.ES2022, target: ts.ScriptTarget.ES2022 }
}).outputText;
const helpers = await import(`data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`);
const { computeWrapperAdjustment, adjustResultForWrapper, adjustInspectForWrapper } = helpers;

// Use actual raw facade diagnostics so changes to Kotlin's wrapper layout also
// fail this gate, rather than testing hand-constructed offsets against themselves.
const scope = vm.createContext({ console, setTimeout, clearTimeout });
for (const file of [
  'kotlin-kotlin-stdlib.js', 'Kotlin-Immutable-Collections-kotlinx-collections-immutable.js',
  'kotlinx-atomicfu.js', 'kotlinx-coroutines-core.js',
  'kotlinx-serialization-kotlinx-serialization-core.js',
  'kotlinx-serialization-kotlinx-serialization-json.js', 'branchline-interpreter.js'
]) {
  vm.runInContext(readFileSync(join(root, '../interpreter/build/dist/js/productionLibrary', file), 'utf8'), scope);
}
const facade = scope['io.github.ehlyzov.branchline:interpreter'].playground.PlaygroundFacade;
const cases = [
  { code: 'OUTPUT { broken: }', line: 1, column: 18 },
  { code: '   OUTPUT { broken: }', line: 1, column: 21 },
  { code: '\n\n   OUTPUT { broken: }\n', line: 3, column: 21 },
  { code: 'LET x = 1\n  OUTPUT { broken: }', line: 2, column: 20 },
  { code: 'LET x = 1\r\n  OUTPUT { broken: }', line: 2, column: 20 },
  { code: 'OUTPUT {\n    broken:\n}', line: 3, column: 1 },
  { code: 'OUTPUT {', line: 1, column: 9 }
];
for (const fixture of cases) {
  const adjustment = computeWrapperAdjustment(fixture.code, 0, facade.isBodyOnlyProgram(fixture.code));
  const raw = facade.run(fixture.code, '{}', false, false);
  assert.equal(raw.success, false, fixture.code);
  const result = adjustResultForWrapper(raw, adjustment);
  assert.equal(result.line, fixture.line, fixture.code);
  assert.equal(result.column, fixture.column, fixture.code);
  assert.doesNotMatch(result.errorMessage, /TRANSFORM Playground|^\[\d+:\d+\]/);
  for (const shared of [[], [{ name: 'cache', kind: 'MANY' }], [{ name: 'first', kind: 'MANY' }, { name: 'second', kind: 'SINGLE' }]]) {
    const inspected = facade.inspect(fixture.code, shared.length ? JSON.stringify(shared) : null);
    const payload = { diagnostics: JSON.parse(inspected.diagnosticsJson), warnings: JSON.parse(inspected.warningsJson) };
    const offset = shared.length ? shared.length + 1 : 0;
    const adjusted = adjustInspectForWrapper(payload, computeWrapperAdjustment(fixture.code, offset, true));
    assert.ok(adjusted.diagnostics.length > 0, fixture.code);
    const span = adjusted.diagnostics[0].span;
    assert.equal(span.startLine, fixture.line, fixture.code);
    assert.equal(span.startColumn, fixture.column, fixture.code);
    assert.doesNotMatch(adjusted.diagnostics[0].message, /TRANSFORM Playground|^\[\d+:\d+\]/);
  }
}

const fullProgram = 'TRANSFORM Explicit {\n OUTPUT { broken: }\n}';
const rawFull = facade.run(fullProgram, '{}', false, false);
assert.equal(rawFull.line, 2);
assert.equal(rawFull.column, 19);
assert.equal(adjustResultForWrapper(rawFull, computeWrapperAdjustment(fullProgram, 0, false)), rawFull);
const noPosition = { success: false, line: null, column: null, errorMessage: 'Invalid input' };
assert.equal(adjustResultForWrapper(noPosition, computeWrapperAdjustment('OUTPUT {}', 0, true)), noPosition);
console.log('Wrapper run/Inspect coordinates passed: 7 body cases, 3 shared layouts, full-program preservation.');
