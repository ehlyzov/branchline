# Branchline: speed, clarity, and tracing for structured data

[![Interpreter Test](https://byob.yarr.is/ehlyzov/branchline/interpreter-test/badges)](https://github.com/ehlyzov/branchline/actions/workflows/tests.yml)
[![VM Test](https://byob.yarr.is/ehlyzov/branchline/vm-test/badges)](https://github.com/ehlyzov/branchline/actions/workflows/tests.yml)
[![Conformance Test](https://byob.yarr.is/ehlyzov/branchline/conformance-test/badges)](https://github.com/ehlyzov/branchline/actions/workflows/tests.yml)
[![CLI Test](https://byob.yarr.is/ehlyzov/branchline/cli-test/badges)](https://github.com/ehlyzov/branchline/actions/workflows/tests.yml)

DeepWiki: [ehlyzov/branchline](https://deepwiki.com/ehlyzov/branchline)

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

**Quick install (macOS/Linux)** — no preinstalled Java or Node required:

```bash
curl -fsSL https://ehlyzov.github.io/branchline/install.sh | sh
export PATH="$HOME/.local/bin:$PATH"
bl --version
bl program.bl --input input.json
```

The installer downloads a private Temurin 21 JRE and the published RC, verifies
checksums and versions, and installs `bl` without sudo or global runtime changes.
To download a private Node 24 runtime instead,
append `-s -- --runtime node` after `sh`. Runtime numeric guarantees differ.
See [installation, updates and manual packages](https://ehlyzov.github.io/branchline/guides/install/).

Both runtimes accept `--input-format xml` for XML payloads.

**Release artifacts**

- CLI: `branchline-cli-js-<tag>.tgz` (Node), `branchline-cli-<tag>-all.jar` (JVM, fat jar).
- Libraries: `branchline-interpreter-<tag>-jvm.jar`, `branchline-vm-<tag>-jvm.jar`, plus `branchline-interpreter-<tag>-js.tgz` for Node/JS runtimes.
- Download from GitHub Releases (assets attached per tag).

## Project Status

> **Release candidate v1.0.0-rc.1:** the documented candidate surface is available for evaluation. Stable 1.0 adoption and production readiness remain open; the external operational pilot has not started. Stability boundaries and remaining gates are documented in the [Release Readiness & Stability guide](docs/guides/release-readiness.md).

## Release Readiness Gate

Branchline uses a readiness gate before labeling a release **Production Ready**. The gate includes:

- A fixed compatibility corpus that runs in the conformance test suite (JVM + JS).
- Explicit prerelease RC status and a bounded candidate 1.0 compatibility policy.
- Numeric profiles and executable introductory examples on advertised runtimes.
- Independent operational pilots with predeclared resource limits and rollback gates.

See the [Release Readiness & Stability guide](docs/guides/release-readiness.md) for the full checklist, versioning policy, and deprecation plan.
The [API/CLI allowlist](docs/guides/api-support.md) and [complete stdlib inventory](docs/language/stdlib-support.md) define the stable-candidate surface. The [operational pilot plan](docs/guides/pilot-plan.md) defines the remaining field evidence.

## Performance Testing

Branchline uses JMH benchmarks for the interpreter and VM with shared datasets.

- Release summaries are attached to GitHub Releases when available.
- Methodology and comparison guidance: `docs/benchmarks.md`.
- Planned automation: publish per-release results and highlight regressions
  without blocking the release.

JSONata comparisons are tracked alongside the JMH summaries in releases.



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
2. **Multiplatform implementation.** The lexer, parser, interpreter, compiler, and VM share Kotlin code and conformance checks. JVM and JS have distinct numeric limits and platform integrations; the [numeric contract](docs/language/numeric.md) and [platform acceptance](docs/guides/platform-support.md) define the tested boundaries. Distributions include their declared dependencies, including ASM for the experimental JVM backend.
3. **Static contracts (in research).** With reasonable limits on expressiveness (for example, forbidding glob queries) we should infer input/output contracts, determine program compatibility ahead of time, and highlight conflicts automatically.
4. **Shared memory as a first-class concept.** The `SHARED` construct signals that a program uses shared state, enabling straightforward integrations such as database or HTTP access, or waiting for specific keys before resuming execution.
5. **Serializable execution.** Programs can suspend via `SUSPEND`, park execution until an event occurs, and even export bytecode for step-by-step evaluation in other runtimes.
6. **Efficient data handling.** The VM is designed to optimize bytecode and manipulate complex structures with minimal overhead.
7. **Straightforward transformations.** Accessing fields in incoming documents and composing outgoing payloads should be effortless.
8. **Built-in common operations.** Frequently used transformation patterns belong in the language so users do not have to reinvent them.

## Resources

- 📚 Documentation (WIP): https://ehlyzov.github.io/branchline/
- 🧪 Playground (WIP): https://ehlyzov.github.io/branchline/playground/

## License

Branchline Public is released under the [Apache License 2.0](LICENSE).
