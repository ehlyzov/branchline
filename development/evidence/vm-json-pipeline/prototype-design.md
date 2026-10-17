# Experimental selective JSON prototype

The opt-in programmatic entry point is
`io.github.ehlyzov.branchline.cli.ExperimentalJsonPipeline.prepare(source, transformName, options)`.
This adds no CLI flag and changes no default input behavior. `execute(jsonText)` returns compact
JSON after ordinary environment construction, contract handling, strict VM bytecode execution
and output formatting. Preparation/compilation occurs once, outside execution. `materialize`
exposes the same parse step for independent detached-tree retention measurements.

`ExperimentalJsonPipelineOptions` contains `forceEager`, `jsonOptions` and `contractsMode`.
Force-eager and selective input share the same execution/formatting path. A report exposes
`effectiveMode` (`SELECTIVE` or `EAGER`), a stable reason string and a defensive snapshot of
`selectedRootKeys`. Selection keys and parser outputs retain no source text. Prepared instances
are sequential and stateful, matching ordinary VM adapters; the VM retains its last environment
and outputs. That last-input retention must be included consistently in both memory measurements.

## Supported access proof

The planner accepts sequential distinct LET bindings and one final OUTPUT. Expressions may use
literals, literal-property objects, arrays, scalar unary operators (`MINUS` and `BANG`), binary
operators, earlier LET locals and static
access paths rooted in `input`, `row` or earlier locals. Root access paths start with a string
field. Each referenced top-level field is retained in full, including all nested arrays, keys,
objects and nulls. `LENGTH` with one supported expression is the only accepted call. Functions,
free field identifiers, dynamic indexing, whole-root input values, mutations, control flow,
shared declarations and adapters request eager materialization. Local bindings named `LENGTH`,
`input` or `row` also request eager input. Any user FUNC declaration requests eager input,
including declarations overriding the builtin LENGTH.

Unary syntax alone does not establish scalar behavior: `SUSPEND` serializes a complete VM
environment snapshot, exposing otherwise unused input keys. The planner therefore explicitly
admits only `MINUS`/`BANG` unary tokens and rejects orchestration tokens (`SUSPEND`, `AWAIT`),
including inside LET initializers and nested supported expressions. Common regression tests
require eager fallback and exact force-eager snapshot-output parity for direct and LET-bound
`SUSPEND 0` with input `{ "a": 1, "unused": 2 }`. This fix changes source only while the parent
measurement run is active; the current measurement jar is preserved until the parent rebuilds.

An actual JSON field named `input` shadows the default alias because ordinary `buildEnv` inserts
the alias before copying input fields. The planner always retains that field in full. Compatibility
alias `row` is installed after the field copy. Null/missing values, selected field types and object
ordering are preserved; the runtime continues to determine access and arithmetic errors.

Contracts WARN/STRICT and numeric-key mode request explicit eager fallback. Ambient `Debug.tracer`
also forces eager materialization, checked on every call, including tracing enabled after
preparation. `report` is an immutable snapshot of the effective plan for the current ambient
context (not the last invocation); reading the getter after enabling/disabling the ambient tracer
reflects that change. Existing eager reasons, including `FORCE_EAGER`, keep their original reason.
Tracing-enabled selection reports `AMBIENT_TRACING` and an empty selected-key set; disabling tracing
restores the prepared selective plan. Previously obtained snapshots stay unchanged. A common
test toggles tracing after preparation and verifies custom trace read callbacks observe unused
nested input values as well as selected fields. The API accepts no explicit tracer, shared seeding,
jobs or summary configuration. These configuration surfaces are outside this prototype.
Fallback concerns materialization only: bytecode compilation/runtime errors still propagate,
and execution never silently replays through the interpreter.

## Complete JSON validation

The existing parser handles both modes. The eager read path stays in place. The selected path
uses recursive skip routines for unused values; it still decodes all object keys, keeps per-object
duplicate sets and scans all strings/literals/delimiters. Duplicate values are validated before
duplicate-key errors, preserving existing error precedence and code-unit positions. Numeric
values use the existing full number conversion even when discarded, preserving precision/range
and platform conversion failures. Skipped strings share Unicode escape validation with eager
strings. Existing acceptance of lone escaped low surrogates and raw surrogate code units is
preserved; this is not a JSON policy tightening.

The low-level `parseJsonObjectInputSelected` API is an unchecked projection and explicitly
requires callers to prove omitted keys unobservable. The prepared pipeline derives its plan
from the parsed program automatically; users never select input fields manually.

Validation time remains O(input text size). Retained materialization changes from the full input
tree to selected complete subtrees plus key sets for active objects and parser recursion. Key
decoding, duplicate bookkeeping and numeric conversion still allocate. Giant flat objects can
retain substantial duplicate-key state. This prototype does not claim streaming input or nested
projection.

## Verification

Common interpreter tests compare eager/selected values, original key order, missing/null values,
fresh trees, malformed skipped objects/arrays/strings/literals/numbers, escaped duplicate keys,
duplicate error precedence, every truncated prefix of nested fixtures, Unicode surrogate cases,
all numeric modes and numeric-key conversion. Common CLI tests compare exact compact output and
error messages through the same strict VM path, alias collisions, repeated execution, plan
snapshot isolation, whole-root/dynamic/function/shared/contract/numeric-key fallback.

Focused commands:

```sh
./gradlew :interpreter:jvmTest --tests '*JsonInputSelectionTest' \
  :cli:jvmTest --tests '*ExperimentalJsonPipelineTest'
./gradlew :interpreter:jsNodeTest :cli:jsNodeTest
```

No performance claim follows from these tests. Parent acceptance owns full-pipeline timing,
allocation, live heap and RSS evidence. No benchmark runs concurrently with build/tests.

On 2026-10-17 the focused JVM command passed (`BUILD SUCCESSFUL in 3s`):
five interpreter tests and four pipeline tests, zero failures/errors/skips.
The first combined command also supplied JVM-style `--tests` filters to the Node tasks;
report inspection showed these selected zero Node tests, so they are not counted as Node coverage.
Unfiltered Node suites are required to establish actual execution of the new common tests.
The first unfiltered run exposed a test-comparison issue: JS big-number wrappers lack Kotlin
structural equality even when their values match. Numeric differential assertions now compare
ordinary JSON serialization. The subsequent JVM parser plus unfiltered Node run passed
(`BUILD SUCCESSFUL in 2s`): Node executed 62 interpreter tests and 11 CLI tests, including all
five parser/planner and four pipeline tests, with zero failures/errors/skips in XML reports.
`git diff --check` passed for the modified tracked parser/runtime files. Broader gates and
independent performance/retention acceptance are owned by the parent task.
