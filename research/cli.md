# Branchline CLI Rollout

This document tracks the plan for introducing dedicated command-line tools that wrap the existing Branchline interpreter, compiler, and VM.

## Goals
- Provide a single entry point (`bl`) that can run Branchline scripts on both JVM and JS targets.
- Ship JVM-first helpers (`blc`, `blvm`) that respectively compile Branchline source to bytecode and execute precompiled bytecode.
- Allow the CLIs to accept structured inputs in JSON out of the box and add an XML ingestion path.
- Make the tools scriptable in CI so workflows no longer need to wire Gradle tasks directly.

## Execution Plan

1. **Survey existing entry points**
   - Confirm reusable parsing/IR/VM APIs in `interpreter` and `vm` modules.
   - Identify helper utilities for JSON conversion that can be reused by the CLI layer.

2. **Create a multiplatform CLI module**
   - Add a new Gradle module (working name `cli`) registered in `settings.gradle`.
   - Configure Kotlin Multiplatform with `jvm` + `js(IR)` targets and wire dependencies on `:interpreter` and `:vm`.
   - Produce binaries:
     - `bl` as a common entry point distributed for both targets.
     - `blc`/`blvm` as JVM-only binaries living in the same module for now.

3. **Implement command dispatch**
   - Introduce a lightweight argument parser (custom for now; consider `kotlinx-cli` later) with a shared `BranchlineCli` runner.
   - Support subcommands: `run` (default), `compile`, `exec-bytecode`.

4. **Hook into interpreter/compiler/VM**
   - For `run`, compile + execute a script and stream the resulting JSON to stdout.
   - For `compile`, emit bytecode artifacts to a file or stdout along with metadata.
   - For `exec-bytecode`, load the produced artifact and execute it through the VM pipeline.

5. **Input handling**
   - Accept `--input` pointing to a file (default JSON) or `--input-format xml`.
   - Reuse JSON conversion helpers from `PlaygroundFacade` or extract them into a shared utility.
   - Provide a JVM XML parser implementation now; document the JS follow-up requirement.

6. **Testing**
   - Add unit coverage for argument parsing and error messaging.
   - Add Gradle integration tests (`:cli:jvmTest` + Node tests) with sample programs under `test-fixtures/`.

7. **Documentation & CI updates**
   - Update README/docs with usage examples (**TODO**).
   - Teach GitHub Actions to invoke `bl run` or the CLI tests once the toolchain is stable (CLI job added; monitor & expand coverage).

## Usage Notes (WIP)

- JVM entry points:
  - `./gradlew :cli:runBl --args "path/to/script.bl --input sample.json"`
  - `./gradlew :cli:runBlc --args "path/to/script.bl --output build/program.blc"`
  - `./gradlew :cli:runBlvm --args "build/program.blc --input sample.json"`
- JS entry point: `./gradlew :cli:jsNodeProductionRun --args="path/to/script.bl --input sample.json"`
- Tests: `./gradlew :cli:jvmTest :cli:jsNodeTest`
- XML input is supported on JVM/JS through `--input-format xml` (JS parser uses `fast-xml-parser`).

## Current Status

- [x] Module scaffolded
- [x] Shared CLI utilities extracted
- [x] JVM binaries implemented (`bl`, `blc`, `blvm`)
- [x] JS binaries implemented (package Node executable / npm distribution via `:cli:packageJsCli`)
- [x] XML input handler (JVM)
- [x] XML input handler (JS)
- [x] Tests in place
- [ ] CI migrated to CLI (CLI job exists; interpreter/vm jobs still using Gradle tasks)

## Follow-ups

- Evaluate distributing the CLI as a separate artifact (e.g., via GitHub Releases).
- Consider packaging JS CLI via npm for easier consumption.
- Investigate a shared XML parser that works seamlessly across JVM/JS targets (current JS implementation uses `fast-xml-parser`).
- Document CLI usage in README and developer guides.
- Decide when to switch interpreter/vm CI jobs to call the CLI instead of module-specific Gradle tasks.

## CI Migration Roadmap (draft)

1. Add a smoke test matrix that runs `bl run` against representative `.bl` fixtures to supplement interpreter/vm module suites.
2. Once stable, replace the interpreter/vm Gradle invocations in `.github/workflows/tests.yml` with CLI equivalents (or keep Gradle as a fallback invoked by the CLI).
3. Track CLI badge trends to ensure coverage parity before removing legacy steps.
