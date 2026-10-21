# 1.0.0-rc.1 package verification

Status: local packaging passed, 2026-10-21. This records local candidate packaging; public
publication and exact-commit CI acceptance belong to the parent release task.

The release version is frozen at `1.0.0-rc.1` in root `build.gradle`. Before the
bump, `git ls-remote --tags origin refs/tags/v1.0.0-rc.1 'refs/tags/v1.0.0-rc.1^{}'`
returned no refs, and `gh release view v1.0.0-rc.1` returned `release not found`.
The remote is `git@github.com:ehlyzov/branchline.git`.

The entry base HEAD was `9de86a0cec25328896a98f909fceec73df0da781`; accepted dirty
source files were present. That HEAD alone does not identify candidate source.
The parent owns selected-path commit and precise source provenance. Local checks
here neither qualify stable 1.0 nor establish an operational pilot.

## Planned local gate

```sh
CHROME_BIN='/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' ./gradlew :cli:blShadowJar :cli:packageJsCli :interpreter:jvmJar :vm:jvmJar :interpreter:packageInterpreterJs :cli:jvmTest :cli:jsNodeTest --console=plain
```

These tasks are defined by the repository and match the full package tasks in
`.github/workflows/release-artifacts.yml`. The five package files are the JVM CLI,
Node CLI, interpreter JVM library, VM JVM library and interpreter Node library.
The workflow renames them using a `v`-prefixed tag, checks actual JVM/Node CLI
versions and npm metadata, then emits commit metadata and SHA256SUMS. No local
release metadata will pretend that the pre-commit HEAD includes dirty source.

Host start: macOS, Temurin OpenJDK 21.0.11+10-LTS; Node v25.3.0; 5.9 GiB free.
Retain at least 3 GiB and do not prune user Docker data.

## Observed local result

The exact gate above exited zero: `BUILD SUCCESSFUL in 13s`, 56 actionable tasks,
33 executed and 23 up-to-date. Raw output:
`output/rc-1.0.0-2026-10-21/packaging-gradle.log`.

JUnit XML was inspected independently of Gradle status and copied under
`output/rc-1.0.0-2026-10-21/test-results/`: CLI JVM 73 tests and CLI Node 26 tests,
zero failures, errors or skips in either suite. NodeFileIoJsTest contributed
three passing tests. No filtered or zero-test Node result is used.

Both `java -jar` on the built standalone JVM jar and `node bin/bl.cjs` on a fresh
tarball extraction exited zero and printed exactly `Branchline CLI v1.0.0-rc.1`.
The extracted Node CLI package.json, package-lock.json and lock root package all
declare `1.0.0-rc.1`; the interpreter JS tarball package.json also declares it.
The three JVM jar ZIP CRC checks passed. Full verification results, host identity,
artifact bytes and suite counts are in
`output/rc-1.0.0-2026-10-21/package-verification.json`. Disk free after packaging:
6,261,055,488 bytes, above the 3 GiB reserve.

| Local candidate artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `cli/build/libs/branchline-cli-1.0.0-rc.1-all.jar` | 25,987,397 | `84eaf83599b9c060f2b9f39543199d610e3359c0e28ecc6f511c836bfd25de67` |
| `cli/build/distributions/branchline-cli-js-1.0.0-rc.1.tgz` | 1,055,656 | `c885e6f56f979556567b32174af24e57ec653efca58ea0b04f5e3728836a8c45` |
| `interpreter/build/libs/interpreter-jvm-1.0.0-rc.1.jar` | 1,446,845 | `ba88c672efd0dc8799fc46a4ce0e07c7ddace8922fddffe38d7b96a3ad171558` |
| `vm/build/libs/vm-jvm-1.0.0-rc.1.jar` | 514,871 | `5848ef37fa882fcd3c6526343c90bbf533035fbc1fa4e71e6ff9a0b60153066f` |
| `interpreter/build/distributions/branchline-interpreter-1.0.0-rc.1-js.tgz` | 799,331 | `6f629c07b0f8d4e0290d4784acef9201e5a6191360bc898e283b611b2b221618` |

These are local package hashes, also saved to
`output/rc-1.0.0-2026-10-21/local-package-SHA256SUMS`. Public workflow builds may
have different archive hashes; verify published assets against their published
SHA256SUMS and exact release commit. Local archives have no fabricated release
commit metadata. CI qualification, public asset verification, Pages deployment
and browser acceptance remain parent-owned gates.
