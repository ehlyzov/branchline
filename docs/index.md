---
title: Branchline DSL
description: Official documentation for the Branchline domain specific language.
---

# Branchline DSL Documentation

Branchline is a Kotlin-based DSL for transforming structured data. These docs cover language features, guides, and an interactive playground. For a formal reference, see the [language grammar](language/grammar.md).

## What is Branchline?
Branchline is a compact language for reshaping structured data (JSON, XML, more coming) with built-in tracing. It aims for clarity, composability, and debuggability while remaining fast across JVM and JS runtimes.

## Try it now
- Open the [embedded playground](playground.md) and load an example such as `customer-profile` or `pipeline-health-gating`.
- Toggle tracing to see `EXPLAIN(...)` output; edit JSON/XML input and rerun with Cmd/Ctrl + Enter.
- Use `?example=stdlib-hof-overview` (or any ID) in the URL to preload a scenario.

## Quickstart (local)
```bash
# 1) Clone and verify toolchain
git clone https://github.com/ehlyzov/branchline-public.git
cd branchline-public
./gradlew --version

# 2) Run a Branchline program on JVM
./gradlew :cli:runBl --args "path/to/program.bl --input sample.json"

# 3) Run via Node
./gradlew :cli:jsNodeProductionRun --args="path/to/program.bl --input sample.json"
```
Need more detail? See [Install Branchline](guides/install.md) and [First steps](guides/first-steps.md).

## Use Branchline in your project
- **CLI (JVM/Node):** Use the Gradle helpers above during development or CI. Keep your `.bl` scripts in your repo and call the CLI from your build/test steps.
- **JS tarball:** `./gradlew :cli:packageJsCli` produces `cli/build/distributions/branchline-cli-js-<version>.tgz`. Add it to your build artifacts, unpack it in CI, and run `bin/bl.cjs` with `--input`/`--input-format`.
- **Vendoring:** You can vendor the packaged CLI into your own tools or Docker images; no Maven/npm packages are published yet, so bundling the tarball (or building from source) is the current path.

### Simplest program
```branchline
TRANSFORM Hello {
    OUTPUT { greeting: "Hello, " + input.name };
}
```
Run it on JVM:
```bash
./gradlew :cli:runBl --args "hello.bl --input sample.json"
```
Or on Node:
```bash
./gradlew :cli:jsNodeProductionRun --args="hello.bl --input sample.json"
```

## Language highlights
- **Straightforward data paths:** `$` or `INPUT` to navigate payloads; dot, slice, predicate, wildcard support.
- **Built-in tracing:** `EXPLAIN`, `CHECKPOINT`, and `ASSERT` surface provenance.
- **Rich stdlib:** arrays, aggregation, text utilities, higher-order functions, time, and shared-memory helpers.
- **Multiplatform parity:** JVM and JS runtimes share the same interpreter/VM code.

## Learn the language
- Start with the [Language Overview](language/index.md) and the [Getting Started guide](guides/getting-started.md).
- Explore the standard library pages for runnable examples linked to the playground.
- Review constraints and performance guidance in [Declarations](language/declarations.md), [Expressions](language/expressions.md), and [Statements](language/statements.md).
- Use the playground to tweak code and inputs without installing anything.

## Production and migration
- Follow the [Production Use guide](guides/production-use.md) for operational best practices, error-handling recipes, and host integration patterns.
- Track compatibility changes in the [Migration Guides](guides/migrations.md) once releases begin to carry upgrade notes.

## Release readiness
- Review the [Release Readiness & Stability guide](guides/release-readiness.md) for versioning policy, stability levels, compatibility checks, and SLA-style targets.
- Check [benchmark results](benchmarks.md) and the [release benchmark history](benchmarks/releases/) for performance tracking.

## What you can build
- [Normalize XML test reports](playground.md?example=junit-badge-summary){ target="_blank" } into JSON summaries and badges.
- [Enrich customer/order payloads](playground.md?example=customer-profile){ target="_blank" } with fallbacks and computed fields.
- [Gate deployments](playground.md?example=pipeline-health-gating){ target="_blank" } using CHECKPOINT/ASSERT plus traces.
