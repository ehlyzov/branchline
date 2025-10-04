# Branchline: speed, clarity, and tracing for structured data

[![Interpreter Test](https://raw.githubusercontent.com/ehlyzov/branchline-public/badges/interpreter-test.svg)](https://github.com/ehlyzov/branchline-public/actions/workflows/tests.yml)
[![VM Test](https://raw.githubusercontent.com/ehlyzov/branchline-public/badges/vm-test.svg)](https://github.com/ehlyzov/branchline-public/actions/workflows/tests.yml)
[![Conformance Test](https://raw.githubusercontent.com/ehlyzov/branchline-public/badges/conformance-test.svg)](https://github.com/ehlyzov/branchline-public/actions/workflows/tests.yml)

Branchline is an experimental data transformation language for building efficient pipelines in low-code environments. It focuses on converting one JSON-like document into another while offering tooling to make the process observable, predictable, and fast. Comparisons with JSONATA, JOLT, and similar technologies are planned for the future.

In a practical case (a program of several thousand lines originally written in JSONata), Branchline showed a performance improvement of roughly 30× compared to the excellent [dashjoin/jsonata-java](https://github.com/dashjoin/jsonata-java) implementation. However, this result comes from a single test case; comprehensive benchmarks have not been conducted, so it is difficult to make general statements about overall performance. Theoretically, Branchline should be faster by design, but practical numbers will be published once systematic measurements are carried out.

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

Branchline ships with experimental CLI helpers for both JVM and Node runtimes.

- Run a script (JVM): `./gradlew :cli:runBl --args "path/to/program.bl --input sample.json"`
- Compile to bytecode: `./gradlew :cli:runBlc --args "path/to/program.bl --output build/program.blc"`
- Execute bytecode (VM): `./gradlew :cli:runBlvm --args "build/program.blc --input sample.json"`
- Run the Node build: `./gradlew :cli:jsNodeProductionRun --args="path/to/program.bl --input sample.json"`
- Generate a distributable Node package: `./gradlew :cli:packageJsCli` (outputs `cli/build/distributions/branchline-cli-js-<version>.tgz`)
- Both binaries accept `--input-format xml` for XML payloads.

## Project Status

> **Alpha:** the language is evolving quickly and backwards compatibility is not guaranteed yet.

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
