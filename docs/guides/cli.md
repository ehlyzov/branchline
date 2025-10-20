---
title: Branchline CLI
---

# Branchline CLI

The Branchline CLI module ships multiplatform entry points that wrap the interpreter, compiler, and VM APIs. Both the JVM and Node bundles share the same command surface:

- `run` (default) — execute a `.bl` script directly.
- `compile` — produce a bytecode artifact that embeds the source and selected transform.
- `exec` — evaluate a compiled artifact through the VM without recompiling the source.

All commands accept structured inputs via `--input <path>` and support XML payloads with `--input-format xml`.

## JVM binaries

Run the Gradle helpers to launch the JVM entry points:

- `./gradlew :cli:runBl --args "path/to/program.bl --input sample.json"`
- `./gradlew :cli:runBlc --args "path/to/program.bl --output build/program.blc"`
- `./gradlew :cli:runBlvm --args "build/program.blc --input sample.json"`

Gradle compiles the CLI on demand and wires the correct classpath for each command. Passing `--stacktrace` or `--info` helps diagnose failing scripts during development.

## Node bundle

To work with the Node build directly, package the compiled JavaScript output and run it with Node:

1. Build the bundle (installs dependencies automatically): `./gradlew :cli:prepareJsCliPackage`
2. Execute scripts: `node cli/build/cliJsPackage/bin/bl.cjs path/to/program.bl --input sample.json`

The `bl.cjs` launcher delegates to the compiled Kotlin/JS runtime and respects the same arguments as the JVM version. Use `BRANCHLINE_CLI_BIN` to point automation at a custom bundle if the default location differs.

## Distribution archive

`./gradlew :cli:packageJsCli` produces a gzipped tarball under `cli/build/distributions/`. The archive includes the CLI entry point, compiled Kotlin/JS artifacts, the `package.json` manifest, and the `node_modules/` tree so consumers do not need to install dependencies separately.

## CI smoke test

The CLI JVM test suite now runs a Node-based smoke test to ensure the packaged bundle executes JSON inputs successfully. This guards against regressions in the packaging flow and verifies the Node wrapper stays in sync with the shared CLI surface.
