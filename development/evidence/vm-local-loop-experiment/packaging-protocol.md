# Local artifact packaging and smoke protocol

Prepared from repository build files, CLI entrypoints/help and existing fixture
tests. **Not executed here:** no build, JVM, Node, extraction, package inspection,
workflow dispatch or publication ran while preparing this note. Parent owns
serialized execution after correctness gates. This protocol verifies local
artifacts and exposes release alignment issues; it does not prepare a release.

## Two observed alignment issues

- `build.gradle:26` declares `v0.11.0-SNAPSHOT`, but
  `cli/src/commonMain/kotlin/io/github/ehlyzov/branchline/cli/CliVersion.kt:4`
  hardcodes `v0.10.0`. `BranchlineCli.kt:1345` prints
  `Branchline CLI ${CliVersion.CURRENT}`. The current-source smoke oracle is
  therefore **`Branchline CLI v0.10.0`**, not the project version. Confirming that
  behavior passes the smoke but does **not** pass release-version alignment.
  Parent must retain this as a release-blocking mismatch until separately fixed.
- `docs/guides/install.md:10` advertises Java 17+, whereas `build.gradle:41,44`
  sets Java language/JVM target 21, with toolchain 21 also at lines 68/77. Use
  Java 21 for the current artifact smoke. Inspect the actual entrypoint class
  major version; Java 17 compatibility is not established and the install
  requirement needs alignment before release.

## Artifact and command provenance

`cli/build.gradle` defines `blShadowJar` as a fat jar with main class
`io.github.ehlyzov.branchline.cli.BlJvmMainKt` and the complete JVM runtime
classpath. `packageJsCli` creates a gzip Tar from `cli/build/cliJsPackage` with
contents directly at archive root: **`bin/bl.cjs`**, **`kotlin/branchline-cli.js`**,
`package.json`, `package-lock.json`, and production `node_modules` installed by
`npm ci --omit=dev --ignore-scripts --no-audit --no-fund` during package preparation.
Do not strip an assumed `package/` directory during extraction. The launcher
requires `../kotlin/branchline-cli.js`; package metadata declares `bl=bin/bl.cjs`.

The source smoke reuses committed fixtures used by `CliIntegrationTest`:

```branchline
TRANSFORM Hello {
    OUTPUT { greeting: "Hello, " + input.name };
}
```

Input is `{"name":"Branchline"}`; independent fixture output is
`{"greeting":"Hello, Branchline"}`. `json-compact` and `compile`/`exec` are
declared by the CLI help/parser; the existing integration tests compile this
fixture, check transform/script identity and execute the compiled artifact.
`JsCliBundleSmokeTest` separately runs the packaged launcher with a hello input.
The protocol below checks extracted release artifacts instead of only prepared
build directories or source classpaths.

## Parent-run commands

Run from repository root using Java 21 and Node 18+ as configured by release CI.
Every build/run/compile/exec command below must exit **0**; save stdout, stderr
and process exits. `ExitCode.SUCCESS` is 0 in `CliFormatting.kt`. A nonzero
command is a failure and must not be ignored because another route succeeded.
The variable values match current source; update them only if the candidate
version/source changes, and record the new binding.

```bash
set -euo pipefail
packaging_root="$PWD"
packaging_version='v0.11.0-SNAPSHOT'
packaging_cli_version='Branchline CLI v0.10.0'
packaging_tmp="$(mktemp -d /tmp/branchline-artifact-smoke.XXXXXX)"
packaging_jar="$packaging_root/cli/build/libs/branchline-cli-${packaging_version}-all.jar"
packaging_tgz="$packaging_root/cli/build/distributions/branchline-cli-js-${packaging_version}.tgz"
packaging_fixture="$packaging_root/cli/src/jvmTest/resources/fixtures/cli"

./gradlew :cli:blShadowJar :cli:packageJsCli --console=plain
test -f "$packaging_jar"
test -f "$packaging_tgz"
mkdir "$packaging_tmp/node"
tar -tzf "$packaging_tgz" > "$packaging_tmp/tar-contents.txt"
tar -xzf "$packaging_tgz" -C "$packaging_tmp/node"
test -x "$packaging_tmp/node/bin/bl.cjs"

java -jar "$packaging_jar" --version > "$packaging_tmp/jvm-version.txt" 2> "$packaging_tmp/jvm-version.err"
node "$packaging_tmp/node/bin/bl.cjs" --version > "$packaging_tmp/node-version.txt" 2> "$packaging_tmp/node-version.err"
java -jar "$packaging_jar" "$packaging_fixture/hello.bl" --input "$packaging_fixture/input.json" --output-format json-compact > "$packaging_tmp/jvm-source.json" 2> "$packaging_tmp/jvm-source.err"
node "$packaging_tmp/node/bin/bl.cjs" "$packaging_fixture/hello.bl" --input "$packaging_fixture/input.json" --output-format json-compact > "$packaging_tmp/node-source.json" 2> "$packaging_tmp/node-source.err"

java -jar "$packaging_jar" compile "$packaging_fixture/hello.bl" --output "$packaging_tmp/jvm.blc" --output-format json-compact > "$packaging_tmp/jvm-compile.txt" 2> "$packaging_tmp/jvm-compile.err"
node "$packaging_tmp/node/bin/bl.cjs" compile "$packaging_fixture/hello.bl" --output "$packaging_tmp/node.blc" --output-format json-compact > "$packaging_tmp/node-compile.txt" 2> "$packaging_tmp/node-compile.err"
java -jar "$packaging_jar" exec "$packaging_tmp/jvm.blc" --input "$packaging_fixture/input.json" --output-format json-compact > "$packaging_tmp/jvm-own.json" 2> "$packaging_tmp/jvm-own.err"
node "$packaging_tmp/node/bin/bl.cjs" exec "$packaging_tmp/node.blc" --input "$packaging_fixture/input.json" --output-format json-compact > "$packaging_tmp/node-own.json" 2> "$packaging_tmp/node-own.err"
java -jar "$packaging_jar" exec "$packaging_tmp/node.blc" --input "$packaging_fixture/input.json" --output-format json-compact > "$packaging_tmp/jvm-from-node.json" 2> "$packaging_tmp/jvm-from-node.err"
node "$packaging_tmp/node/bin/bl.cjs" exec "$packaging_tmp/jvm.blc" --input "$packaging_fixture/input.json" --output-format json-compact > "$packaging_tmp/node-from-jvm.json" 2> "$packaging_tmp/node-from-jvm.err"
```

The two crossed executions test the current writer/reader pair on a small common
numeric-free fixture. They do not establish old-release compatibility or new
PRIVATE_* opcode compatibility; those remain separate release gates.

## Artifact and output oracles

Run the following Python checks in the same shell after the commands above. They
do not invoke Java/Node, contact a registry or run npm publication. They verify
actual ZIP contents, Node package/lock/installed-dependency metadata, compiled
artifact structure and independently expected output. The experimental classes
are expected in the current ordinary VM/CLI artifact because no exclusion/module
split has been made; if release scope changes, update that decision explicitly.

```bash
python3 - "$packaging_jar" "$packaging_tmp" "$packaging_version" "$packaging_cli_version" "$packaging_fixture" <<'PY'
import json, pathlib, sys, zipfile
jar_path, work, version, cli_version, fixture = sys.argv[1:]
work, fixture = pathlib.Path(work), pathlib.Path(fixture)
with zipfile.ZipFile(jar_path) as jar:
    names = set(jar.namelist())
    required = {
        'io/github/ehlyzov/branchline/cli/BlJvmMainKt.class',
        'org/objectweb/asm/ClassWriter.class',
        'io/github/ehlyzov/branchline/vm/JvmLoopCompiler.class',
        'io/github/ehlyzov/branchline/vm/LoopBodyPlan.class',
        'io/github/ehlyzov/branchline/vm/JvmLocalLoopCompiler.class',
        'io/github/ehlyzov/branchline/vm/LocalLoopPlan.class',
    }
    assert required <= names, sorted(required - names)
    manifest = jar.read('META-INF/MANIFEST.MF').decode('utf-8')
    assert 'Main-Class: io.github.ehlyzov.branchline.cli.BlJvmMainKt' in manifest
    entry = jar.read('io/github/ehlyzov/branchline/cli/BlJvmMainKt.class')
    assert entry[:4] == bytes.fromhex('cafebabe')
    major = int.from_bytes(entry[6:8], 'big')
    assert major == 65, ('expected Java 21 class major', major)

node_root = work / 'node'
package = json.loads((node_root / 'package.json').read_text())
lock = json.loads((node_root / 'package-lock.json').read_text())
assert package['name'] == '@ehlyzov/branchline-cli'
assert package['version'] == version
assert package['type'] == 'commonjs'
assert package['bin']['bl'] == 'bin/bl.cjs'
assert package['publishConfig']['registry'] == 'https://npm.pkg.github.com'
assert lock['name'] == package['name'] and lock['version'] == version
assert lock['packages']['']['version'] == version
assert (node_root / 'kotlin/branchline-cli.js').is_file()
assert package['dependencies']['fast-xml-parser'] == '^4.3.5'
assert lock['packages']['node_modules/fast-xml-parser']['version'] == '4.3.5'
installed = json.loads((node_root / 'node_modules/fast-xml-parser/package.json').read_text())
assert installed['version'] == '4.3.5'

for name in ('jvm-version.txt', 'node-version.txt'):
    assert (work / name).read_text().strip() == cli_version, name
expected = json.loads((fixture / 'expected.json').read_text())
assert expected == {'greeting': 'Hello, Branchline'}
for name in ('jvm-source.json', 'node-source.json', 'jvm-own.json', 'node-own.json',
             'jvm-from-node.json', 'node-from-jvm.json'):
    assert json.loads((work / name).read_text()) == expected, name
source = (fixture / 'hello.bl').read_text().strip()
for name in ('jvm.blc', 'node.blc'):
    artifact = json.loads((work / name).read_text())
    assert artifact.get('version', 1) == 1 and artifact['transform'] == 'Hello', name
    assert artifact['script'].strip() == source and artifact['bytecode'], name
print(json.dumps({'smoke': 'PASS', 'jvmClassMajor': major,
                  'packageVersion': version, 'observedCliVersion': cli_version,
                  'releaseVersionAligned': cli_version == 'Branchline CLI ' + version,
                  'evidenceDirectory': str(work)}, indent=2))
PY
```

With current source the final `releaseVersionAligned` value must be **false**.
Do not reinterpret smoke success as permission to release with stale version
output. Preserve the temporary evidence directory for the parent's final record.
Inspect stderr files even on success; this fixture should not need numeric/XML
conversion warnings. Default `run` is interpreter execution; `exec` uses the
compiled VM path. This smoke does not invoke the opt-in local compiler directly.
Presence of its class and ASM proves packaging, not local-kernel correctness.

This Gradle Tar is the documented self-contained release download, not proof of
the exact file set that `npm publish` would upload or what a registry install
would resolve. No registry/authentication, Maven consumer, Java 17 launch,
cross-version artifact, production, or performance claim is covered here.

## Parent-observed artifact result

The parent subsequently built the artifacts and ran the saved commands in
`artifact-smoke.sh`: all command exits were 0 and stderr files were empty. The
first Python oracle failed with `KeyError: version`; this was an oracle mistake,
not an artifact execution failure. `CompiledArtifact.version` defaults to 1 in
`BranchlineRuntime.kt`, and ArtifactCodec's default kotlinx serialization omits
default-valued fields. The current JVM/Node artifacts both omit the version
field. The corrected check above accepts that declared default while still
rejecting an explicitly incompatible version.

Only the corrected Python oracle was rerun against the preserved directory
`/tmp/branchline-artifact-smoke.lqkeH8`; no build or artifact command was repeated.
`artifact-oracles.py` and `artifact-oracles.log` record PASS, entrypoint major 65,
package version `v0.11.0-SNAPSHOT`, observed CLI version `Branchline CLI v0.10.0`,
and **`releaseVersionAligned: false`**. Both documented release alignment issues
remain open. The initial paragraph records what ran during protocol preparation;
this section records the later parent-owned execution.
