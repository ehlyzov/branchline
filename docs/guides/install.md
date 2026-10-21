---
title: Install Branchline
---

# Install and run locally

Run the same program and JSON input you used in the browser. The commands below
use the published [v1.0.0-rc.1 release](https://github.com/ehlyzov/branchline/releases/tag/v1.0.0-rc.1).
This is a release candidate; see [release readiness](release-readiness.md) for its scope.

## Prerequisites

Choose either Java 21+ for the JVM CLI or Node 18+ for the Node CLI.
For a new integration, use the Java 21/25 or Node 22/24 targets and check the
OS/CPU acceptance in [Platform Support](platform-support.md). Node 18 is a legacy
compatibility check. The JVM release targets class-file version 65.
The download commands use `curl`; the Node package also needs `tar`.

## Prepare your files

Create a working directory:

```bash
mkdir -p branchline-demo
cd branchline-demo
```

If you used **Download program.bl** and **Download input.json** in the Playground,
put those files in this directory. They contain your current edits.
Otherwise, download the original order example:

```bash
curl -fL -o program.bl https://ehlyzov.github.io/branchline/downloads/prepare-order/program.bl
curl -fL -o input.json https://ehlyzov.github.io/branchline/downloads/prepare-order/input.json
```

You can also save [program.bl](../downloads/prepare-order/program.bl) and
[input.json](../downloads/prepare-order/input.json) from these links.

## JVM CLI

Download the [CLI jar](https://github.com/ehlyzov/branchline/releases/download/v1.0.0-rc.1/branchline-cli-v1.0.0-rc.1-all.jar),
check its version, and run your files:

```bash
curl -fL -o branchline-cli-v1.0.0-rc.1-all.jar https://github.com/ehlyzov/branchline/releases/download/v1.0.0-rc.1/branchline-cli-v1.0.0-rc.1-all.jar
java -jar branchline-cli-v1.0.0-rc.1-all.jar --version
java -jar branchline-cli-v1.0.0-rc.1-all.jar program.bl --input input.json
```

The version command prints `Branchline CLI v1.0.0-rc.1`.

## Node CLI

Download the [Node CLI package](https://github.com/ehlyzov/branchline/releases/download/v1.0.0-rc.1/branchline-cli-js-v1.0.0-rc.1.tgz),
extract it into `branchline-cli`, check its version, and run your files:

```bash
curl -fL -o branchline-cli-js-v1.0.0-rc.1.tgz https://github.com/ehlyzov/branchline/releases/download/v1.0.0-rc.1/branchline-cli-js-v1.0.0-rc.1.tgz
mkdir -p branchline-cli
tar -xzf branchline-cli-js-v1.0.0-rc.1.tgz -C branchline-cli
node branchline-cli/bin/bl.cjs --version
node branchline-cli/bin/bl.cjs program.bl --input input.json
```

The version command prints `Branchline CLI v1.0.0-rc.1`.

## Check the result

For the original downloaded input, either CLI produces:

--8<-- "includes/prepare-order/expected.md"

If you changed PEN's `qty` to `1` before downloading from the Playground, expect:

--8<-- "includes/prepare-order/exercise-expected.md"

Compare JSON values; whitespace and object key order may differ.

If Java reports an unsupported class-file version, use Java 21 or later.
If a command cannot find `program.bl` or `input.json`, run it from the directory
containing those files. For XML input, use `--input-format xml`; these starter files
use JSON.

Continue with [Getting Started](getting-started.md#continue-learning), or see the
[CLI reference](cli.md) for other flags and input formats.
