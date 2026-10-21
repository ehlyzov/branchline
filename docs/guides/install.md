---
title: Install Branchline
---

# Install and run locally

Run the same program and JSON input you used in the browser. The commands below
use the published [v1.0.0-rc.1 release](https://github.com/ehlyzov/branchline/releases/tag/v1.0.0-rc.1).
This is a release candidate; see [release readiness](release-readiness.md) for its scope.

## Quick install (macOS and Linux)

Use an installed **Java 21/25** or **Node 22/24** runtime, then run:

```bash
curl -fsSL https://ehlyzov.github.io/branchline/install.sh | sh
export PATH="$HOME/.local/bin:$PATH"
bl --version
```

The installer downloads **v1.0.0-rc.1**, checks its SHA-256 checksum and CLI
version, and creates `~/.local/bin/bl`. No `sudo` or manual extraction is needed.
It stores packages in `~/.local/share/branchline` and does not modify shell profiles
or install Java/Node. Add the PATH line to your shell profile if you want it in
future terminals. The version command prints `Branchline CLI v1.0.0-rc.1`.

Java 21+ is preferred when available. Otherwise the installer uses Node 22 or 24
and tells you which runtime it selected. To choose explicitly:

```bash
curl -fsSL https://ehlyzov.github.io/branchline/install.sh | sh -s -- --runtime node
# Or: --runtime jvm
```

Java and Node have different [numeric profiles](../language/numeric.md): Node
has no exact-decimal guarantee. Check [Platform Support](platform-support.md)
for qualified OS/CPU/runtime combinations; installer availability does not certify
all macOS/Linux versions or architectures. Windows users can use the manual
packages below; this shell installer does not qualify Windows or WSL.

You need `curl` and `sha256sum` or `shasum`; Node extraction also needs `tar`.
If no suitable runtime is found, install [Java](https://adoptium.net/temurin/releases/)
or [Node](https://nodejs.org/en/download), then rerun. Prefer Java 21/25 or Node 22/24.
You can [inspect the installer](../install.sh) before running it.

## Run your files

Keep the **Download program.bl** and **Download input.json** files from the
Playground together, then run from that directory:

```bash
bl program.bl --input input.json
```

These are your edited files. **Skip the original-example downloads below** if you
already have them. If you changed PEN's quantity to `1`, the result includes both
`BOOK` and `PEN`.

## Update or remove

Rerun the quick-install command to install the version currently recommended by
the site. You can pin another published version using `sh -s -- --version v1.0.0-rc.1`.
A failed download, checksum or version check leaves the active launcher unchanged.
An unrelated existing `bl` command is not overwritten. Previous packages are
retained; the new launcher selects the successfully verified installation.

For a custom location, set `BRANCHLINE_INSTALL_ROOT` and `BRANCHLINE_BIN_DIR` on
the `sh` process; both must be absolute paths. The installer prints the exact
PATH adjustment. To remove the default installation, remove `~/.local/bin/bl`
and `~/.local/share/branchline`. With custom locations, remove only those paths
you selected. Your programs and JSON files remain separate.

## Manual installation

The package commands below are an alternative to the installer; you do not need
them if `bl --version` already works. The JVM jar needs Java 21+ (class-file 65).
The Node package retains Node 18+ launcher compatibility, but Node 18 is legacy;
use Node 22/24 for a new integration.

### Original example files

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

### JVM CLI

Download the [CLI jar](https://github.com/ehlyzov/branchline/releases/download/v1.0.0-rc.1/branchline-cli-v1.0.0-rc.1-all.jar),
check its version, and run your files:

```bash
curl -fL -o branchline-cli-v1.0.0-rc.1-all.jar https://github.com/ehlyzov/branchline/releases/download/v1.0.0-rc.1/branchline-cli-v1.0.0-rc.1-all.jar
java -jar branchline-cli-v1.0.0-rc.1-all.jar --version
java -jar branchline-cli-v1.0.0-rc.1-all.jar program.bl --input input.json
```

The version command prints `Branchline CLI v1.0.0-rc.1`.

### Node CLI

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
