# Branchline: speed, clarity, and tracing for structured data

[![Interpreter Test](https://byob.yarr.is/ehlyzov/branchline-public/interpreter-test/badges)](https://github.com/ehlyzov/branchline-public/actions/workflows/tests.yml)
[![VM Test](https://byob.yarr.is/ehlyzov/branchline-public/vm-test/badges)](https://github.com/ehlyzov/branchline-public/actions/workflows/tests.yml)
[![Conformance Test](https://byob.yarr.is/ehlyzov/branchline-public/conformance-test/badges)](https://github.com/ehlyzov/branchline-public/actions/workflows/tests.yml)
[![CLI Test](https://byob.yarr.is/ehlyzov/branchline-public/cli-test/badges)](https://github.com/ehlyzov/branchline-public/actions/workflows/tests.yml)

DeepWiki: [ehlyzov/branchline-public](https://deepwiki.com/ehlyzov/branchline-public)

Branchline is an experimental data transformation language for building efficient pipelines in low-code environments. It focuses on converting one JSON-like document into another while offering tooling to make the process observable, predictable, and fast. Comparisons with JSONATA, JOLT, and similar technologies are planned for the future.


Performance is measured with a JVM JMH suite for the interpreter and VM using shared datasets. Results are published per release when available; there are no JS runtime benchmarks yet.

## Quick Example

```branchline
LET key = "Hello";
LET loud = (x) -> UPPER(JOIN(x));
LET world = ["w", "o", "r", "l", "d", "!"];

OUTPUT {
    [key]: loud(world)
}
```

## CLI

Branchline ships with experimental CLI helpers for JVM and Node runtimes. Running any CLI with no arguments now prints a detailed help screen (`-h`/`--help` also supported).

**Quick usage (JVM)**

- Run a script: `./gradlew :cli:runBl --args "examples/hello.bl --input fixtures/hello.json"`
- Compile to bytecode: `./gradlew :cli:runBlc --args "examples/hello.bl --output build/hello.blc"`
- Execute bytecode (VM): `./gradlew :cli:runBlvm --args "build/hello.blc --input fixtures/hello.json"`
- Pipe input via stdin: `cat fixtures/hello.json | ./gradlew :cli:runBl --args "examples/hello.bl --input -" --quiet`

**Node bundle**

1. Build the package (installs dependencies automatically): `./gradlew :cli:prepareJsCliPackage`
2. Run the CLI: `node cli/build/cliJsPackage/bin/bl.cjs path/to/program.bl --input sample.json`

The Gradle build also produces a distributable archive via `./gradlew :cli:packageJsCli` (tarball under `cli/build/distributions/`) that bundles the CLI entry point, compiled Kotlin/JS artifacts, and the packaged `node_modules/` dependencies.

Both runtimes accept `--input-format xml` for XML payloads.

**Release artifacts**

- CLI: `branchline-cli-js-<tag>.tgz` (Node), `branchline-cli-<tag>-all.jar` (JVM, fat jar).
- Libraries: `branchline-interpreter-<tag>-jvm.jar`, `branchline-vm-<tag>-jvm.jar`, plus JS packages `branchline-interpreter-<tag>-js.tgz` and `branchline-vm-<tag>-js.tgz` for Node/JS runtimes.
- Download from GitHub Releases or build locally with `./gradlew :cli:packageJsCli :cli:blShadowJar :interpreter:jvmJar :vm:jvmJar :interpreter:jsNodeProductionLibraryDistribution :vm:jsNodeProductionLibraryDistribution`.

## Project Status

> **Alpha:** the language is evolving quickly and backwards compatibility is not guaranteed yet. Stability levels and the production readiness gate are documented in the [Release Readiness & Stability guide](docs/guides/release-readiness.md).

## Release Readiness Gate

Branchline uses a readiness gate before labeling a release **Production Ready**. The gate includes:

- A fixed compatibility corpus that runs in the conformance test suite (JVM + JS).
- Explicit stability levels (Stable/Beta/Experimental) for language features.
- SLA-style targets for error rate and performance regression thresholds.

See the [Release Readiness & Stability guide](docs/guides/release-readiness.md) for the full checklist, versioning policy, and deprecation plan.

## Performance Testing

Branchline uses JMH benchmarks for the interpreter and VM with shared datasets.

- Run locally: `./gradlew :interpreter-benchmarks:jmh :vm-benchmarks:jmh`.
- Results: `interpreter-benchmarks/build/results/jmh/results.json` and
  `vm-benchmarks/build/results/jmh/results.json`.
- Release summary: `./gradlew :cli:runBl --args ".github/scripts/jmh-report.bl --shared-file jmh=interpreter-benchmarks/build/results/jmh/results.json --shared-file jmh=vm-benchmarks/build/results/jmh/results.json --shared-format json --shared-key basename --write-output --write-output-dir build/benchmarks"`.
- Methodology and comparison guidance: `docs/benchmarks.md`.
- Planned automation: publish per-release results and highlight regressions
  without blocking the release.

## Motivation

I created Branchline while exploring alternative ways to describe integration flows. I needed a language that:

1. Includes built-in primitives for debugging.
2. Stays approachable for non-programmers who will author transformations.
3. Is deterministic enough for AI systems to generate correct code.
4. Delivers solid performance.
5. Can be measured and optimized with clear feedback loops.
6. Works across diverse data structures—JSON, XML, CSV, and beyond.
7. Operates reliably in shared-memory, multi-threaded environments.
8. Remains open enough to satisfy my curiosity for language and platform design research.

A key goal of the project is to offload as much coding as possible to AI agents (primarily Codex with a Plus subscription). This lets me focus on language- and platform-design experiments during evenings and weekends while agents handle the bulk of implementation work. Spoiler: the results have exceeded my expectations.

## Guiding Principles

Branchline aims to provide several qualities that are often missing in industrial integration tooling:

1. **End-to-end tracing.** Every program should be able to explain how outputs were produced, exposing that trace in a machine-readable form.
2. **Multiplatform parity.** The lexer, parser, interpreter, compiler, and VM are implemented in Kotlin without external dependencies, ensuring the JVM and JS runtimes behave identically.
3. **Static contracts (in research).** With reasonable limits on expressiveness (for example, forbidding glob queries) we should infer input/output contracts, determine program compatibility ahead of time, and highlight conflicts automatically.
4. **Shared memory as a first-class concept.** The `SHARED` construct signals that a program uses shared state, enabling straightforward integrations such as database or HTTP access, or waiting for specific keys before resuming execution.
5. **Serializable execution.** Programs can suspend via `SUSPEND`, park execution until an event occurs, and even export bytecode for step-by-step evaluation in other runtimes.
6. **Efficient data handling.** The VM is designed to optimize bytecode and manipulate complex structures with minimal overhead.
7. **Straightforward transformations.** Accessing fields in incoming documents and composing outgoing payloads should be effortless.
8. **Built-in common operations.** Frequently used transformation patterns belong in the language so users do not have to reinvent them.

## Resources

- 📚 Documentation (WIP): https://ehlyzov.github.io/branchline-public/
- 🧪 Playground (WIP): https://ehlyzov.github.io/branchline-public/playground/

## License

Branchline Public is released under the [Apache License 2.0](LICENSE).
