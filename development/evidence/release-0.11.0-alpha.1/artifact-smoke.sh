set -euo pipefail
packaging_root="$PWD"
packaging_version='0.11.0-alpha.1'
packaging_cli_version='Branchline CLI v0.11.0-alpha.1'
packaging_tmp="$(mktemp -d /tmp/branchline-artifact-smoke.XXXXXX)"
packaging_jar="$packaging_root/cli/build/libs/branchline-cli-${packaging_version}-all.jar"
packaging_tgz="$packaging_root/cli/build/distributions/branchline-cli-js-${packaging_version}.tgz"
packaging_fixture="$packaging_root/cli/src/jvmTest/resources/fixtures/cli"

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
    assert artifact['version'] == 2 and artifact['format'] == 'branchline-vm' and artifact['transform'] == 'Hello', name
    assert artifact['script'].strip() == source and artifact['bytecode'], name
print(json.dumps({'smoke': 'PASS', 'jvmClassMajor': major,
                  'packageVersion': version, 'observedCliVersion': cli_version,
                  'releaseVersionAligned': cli_version == 'Branchline CLI v' + version,
                  'evidenceDirectory': str(work)}, indent=2))
PY
