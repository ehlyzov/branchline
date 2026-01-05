---
title: Branchline CLI
---

# Branchline CLI

The Branchline CLI wraps the interpreter, compiler, and VM APIs. Both JVM and Node bundles expose the same commands:

- `run` (default) — compile and execute a `.bl` script directly.
- `compile` — produce a bytecode artifact (.blc) that embeds the source and selected transform.
- `exec` — evaluate a compiled artifact through the VM without recompiling the source.
- `inspect` — report explicit signatures vs inferred contracts (including mismatch warnings).

Calling a CLI with no arguments now prints a full help screen (`-h`/`--help` also work). All commands accept structured inputs via `--input <path>` and support XML payloads with `--input-format xml`.

**CLI version:** `v0.10.0`.

## Stable flags (output, tracing, errors)

These flags are part of the stable CLI surface for automation and CI:

- `--output-format json|json-compact` — choose pretty or compact JSON for CLI output (applies to `run`, `exec`, and `compile`).
- `--trace` — emit a trace summary to stderr after execution.
- `--trace-format text|json` — format the trace summary (`text` for human-readable, `json` for tooling).
- `--error-format text|json` — format errors for logs or CI parsers.
- `--version` — print the CLI version.

## Command modes and when to use them

- **run**: quickest way to try a Branchline script; compiles + executes in one step. Use in dev loops or CI smoke tests.
- **compile**: produces a portable `.blc` artifact with bytecode + embedded source/transform. Use when you want to freeze a script at build time or ship it separately.
- **exec**: runs a `.blc` through the VM. Use when the source is already compiled, or when you want to lock a transform/version while letting inputs vary. `--transform` overrides the embedded transform at execution time.
- **inspect**: compares explicit transform signatures to inferred contracts. Use to validate input/output expectations and review contract warnings.

Transform selection: all modes default to the first `TRANSFORM` block. Pass `--transform <name>` to pick another.

Input handling: `--input <path>` reads JSON/XML; `--input -` reads stdin so you can pipe data into the CLI. `--input-format xml` parses XML into objects (attributes as `@attr`, text as `#text`).

Output handling: `--output-format json` emits pretty JSON and `--output-format json-compact` emits minified JSON for CI diffs.

### Output selection and file emission

- `--output-path <path>` selects a nested value from the JSON output (ex: `summary.status`, `files[0].name`).
- `--output-raw` prints the selected value without JSON encoding (useful for plain strings).
- `--output-file <path>` writes the selected output to a file (path may be a file or directory; directory defaults to `output.json`).
- `--output-lines <path>` writes output as JSON Lines (one line per list entry or one line for scalars; directory defaults to `output.jsonl`).
- `--write-output` writes files described by `OUTPUT.files` (object map or list of `{ name, contents }`), paired with `--write-output-dir` or `--write-output-file`.
- `--write-output-dir <dir>` writes `OUTPUT.files` entries into a directory by name.
- `--write-output-file <name>=<path>` maps a specific `OUTPUT.files` entry to a path (repeatable).

### Shared IO and fan-out

The CLI can seed `SHARED` resources from files, directories, or globs and run per-file transforms in parallel.

- `--shared-file <resource>=<path>` maps a single file into a shared resource.
- `--shared-dir <resource>=<dir>` maps all files under a directory.
- `--shared-glob <resource>=<glob>` maps files matched by a glob.
- `--shared-format json|xml|text` parses shared files before storing (default: `text`).
- `--shared-key relative|basename|custom` controls shared key naming (use `resource=key=path` when `custom`).
- `--jobs <n>` enables fan-out parallelism across shared inputs.
- `--summary-transform <name>` runs a summary transform with `{ reports, manifest }`.

Example: summarize JUnit reports in parallel and write the summary to a file:

```bash
java -jar branchline-cli.jar .github/scripts/junit-summary.bl \
  --transform FileSummary \
  --summary-transform Summary \
  --shared-glob "reports=interpreter/build/test-results/**/*.xml" \
  --shared-format xml \
  --shared-key relative \
  --jobs 4 \
  --output-file build/branchline-junit-summary.json
```

## Copy/paste quickstart

Create `hello.bl`:

```branchline
TRANSFORM Hello {
    OUTPUT { greeting: "Hello, " + input.name };
}
```

Create `input.json`:

```json
{ "name": "Branchline" }
```

- Run directly (JVM): `./gradlew :cli:runBl --args "hello.bl --input input.json"`
- Compile: `./gradlew :cli:runBlc --args "hello.bl --output build/hello.blc"`
- Execute compiled artifact: `./gradlew :cli:runBlvm --args "build/hello.blc --input input.json"`
- Inspect contracts: `./gradlew :cli:runBl --args "inspect hello.bl --contracts"`
- Pipe stdin: `cat input.json | ./gradlew :cli:runBl --args "hello.bl --input -" --quiet`

## JVM binaries

Run the Gradle helpers to launch the JVM entry points (each helper sets the default command automatically):

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

`./gradlew :cli:packageJsCli` produces a gzipped tarball under `cli/build/distributions/`. The archive includes the CLI entry point, compiled Kotlin/JS artifacts, the `package.json` manifest, and the `node_modules/` tree so consumers do not need to install dependencies separately. `./gradlew :cli:blShadowJar` creates a standalone JVM jar (`branchline-cli-<tag>-all.jar`) runnable with `java -jar`.

## Release artifacts for apps and automation

- **CLI**: `branchline-cli-js-<tag>.tgz` (Node) and `branchline-cli-<tag>-all.jar` (JVM).
- **Libraries**:
  - JVM: `branchline-interpreter-<tag>-jvm.jar`, `branchline-vm-<tag>-jvm.jar`.
  - JS/Node: `branchline-interpreter-<tag>-js.tgz`, `branchline-vm-<tag>-js.tgz` (installable via `npm install ./branchline-interpreter-<tag>-js.tgz`).
- GitHub Releases publish all of the above via the `release-artifacts` workflow. Build locally with:

```bash
./gradlew :cli:packageJsCli :cli:blShadowJar \
    :interpreter:jvmJar :vm:jvmJar \
    :interpreter:jsNodeProductionLibraryDistribution :vm:jsNodeProductionLibraryDistribution
```

### Embedding the libraries

- **Kotlin/JVM apps**: add the JARs to your classpath, then use the `v2` interpreter/VM APIs directly.
- **Node/JS apps**: unpack the `*-js.tgz` tarball and import from `kotlin/branchline-interpreter.js` or `kotlin/branchline-vm.js`, or point `npm install` at the tarball path to pull it into `node_modules/`.

### JVM fat jar
- Build a standalone jar you can run with `java -jar` (no Gradle on the consumer side):
  ```bash
  ./gradlew :cli:blShadowJar
  java -jar cli/build/libs/branchline-cli-all.jar path/to/program.bl --input data.json
  ```
- The jar sets `io.branchline.cli.BlJvmMain` as the entry point. Use `--input-format xml` for XML.

### Using the CLI without Gradle on consumers
- Download or produce the tarball once (from CI artifacts or a release), unpack it, and run the launcher directly:
  ```bash
  tar -xzf branchline-cli-js-*.tgz -C ./branchline-cli
  ./branchline-cli/bin/bl.cjs path/to/program.bl --input data.json
  ```
- Check the unpacked bundle into a Docker image or internal artifact store so downstream users do not need the Gradle build.
- You can host both the JS tarball and the JVM fat jar on GitHub Releases; consumers download the asset they need and run it directly.

## CI smoke test

The CLI JVM test suite now runs a Node-based smoke test to ensure the packaged bundle executes JSON inputs successfully. This guards against regressions in the packaging flow and verifies the Node wrapper stays in sync with the shared CLI surface.

## Exit codes

The CLI uses stable exit codes so CI scripts can distinguish failure modes:

| Exit code | Meaning |
| --- | --- |
| 0 | Success |
| 64 | Usage error (invalid flags, missing arguments) |
| 65 | Input error (invalid script, schema, or input data) |
| 70 | Runtime error (execution or unexpected failure) |
| 74 | I/O error (file or stdin/stdout issues) |
