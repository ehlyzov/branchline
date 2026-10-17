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
