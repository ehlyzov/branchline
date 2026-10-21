# Independent RC release review — 2026-10-21

Scope: read-only release review of the accepted 68-path platform source set,
current release/test/deploy workflows, RC version identity and public claims.
The user cancelled integrator-kit preparation and authorized public
`v1.0.0-rc.1`; the scope correction in the RC planning record governs this review.
No builds, tests, workflow dispatches, Git mutations or product edits were run.
The historical semantic/platform acceptance was not repeated.

## Result

No new P0/P1 defect found in the reviewed release path. This is permission to
proceed to qualification, not a claim that this precise RC commit or its packages
have already passed qualification. Exact-commit Test Suite, exact-version package
smoke, published checksum/metadata verification, Pages metadata and public browser
acceptance remain parent-owned release gates.

## Release controls and required acceptance observations

- Root `build.gradle` now sets `1.0.0-rc.1`; subprojects inherit it. Existing CLI
  generated-version, archive-version and release-version tasks consume that value.
  `release-artifacts.yml` checks exact tag `v1.0.0-rc.1` against that value, binds
  checkout/tag/build SHA, and treats the hyphenated version as a prerelease.
- Use workflow dispatch with `full_release=true`, `run_jmh=false`,
  `run_jsonata=false` and exact tag `v1.0.0-rc.1`. Publishing a GitHub release
  first invokes the release-event branch, which forces JMH on. The intended
  dispatch builds before publishing a draft.
- Full release captures five exact-version packages: standalone JVM CLI, Node
  CLI tarball, interpreter JVM, VM JVM and interpreter JS tarball, plus
  `release-metadata.json` and `SHA256SUMS`. Both CLI launchers and Node package
  metadata are compared against canonical version before publication.
- Pages is invoked with the exact built SHA and expected package version. It
  validates those values and emits `release-metadata.json` and build-info.
  Public acceptance must inspect those values, rather than assume the site
  corresponds to current `main`.
- Existing Test Suite uses `continue-on-error: true` on Gradle test commands and
  gates jobs on aggregate JUnit status. A Gradle failure after some passing XML
  could leave a passing summary. Inspect the actual `Run … tests` step outcomes,
  required suite presence/counts and zero failure/error XML totals. A green job
  alone cannot override a failed build/test step. This is a pre-existing workflow
  limitation; this review does not propose expanding the RC implementation scope.
- The new conformance tests read checked-out docs and source inventories. Their
  Gradle inputs declare these files, and JVM/Node readers walk parent directories
  from the task working directory. Include the docs and all actual implementations
  in the commit. Legacy-v2 JVM resource and its common fixture are both required.

## Public claims

The API and stdlib pages are bounded candidate allowlists, explicitly excluding
unlisted public declarations, general binary ABI, research backends and unsupported
numeric/platform domains. Their consumer/inventory tests do not claim exhaustive
coverage. The operational pilot remains open; an RC tag does not adopt stable 1.0
compatibility or close the pilot.

At review time README/release-readiness/platform pages still describe Alpha
maturity; the platform page's “current candidate” record identifies old local
`0.11.0-alpha.1` artifacts rather than the newly versioned RC. Preserve that
historical provenance but make the new release notes explicit about RC identity,
source SHA, version-only evidence reuse, freshly verified packages and remaining
pilot/OS/browser limitations. Do not relabel old artifact hashes as RC evidence.
The conservative Alpha maturity wording is not a false production-readiness claim.

## Selected commit paths

Use the exact 68 baseline paths below, plus `build.gradle`,
`development/planning/one-zero-rc-pilot-2026-10-21.md`, this review, and individually
reviewed new RC evidence/notes. The parent owns the index update and any public
status edits. These additions require explicit final diff review; do not add a
whole directory to include them by accident.

```text
README.md
cli/src/commonMain/kotlin/io/github/ehlyzov/branchline/cli/BranchlineRuntime.kt
cli/src/commonTest/kotlin/io/github/ehlyzov/branchline/cli/CompiledArtifactCompatibilityTest.kt
cli/src/commonTest/kotlin/io/github/ehlyzov/branchline/cli/LegacyV2ArtifactFixture.kt
cli/src/jsMain/kotlin/io/github/ehlyzov/branchline/cli/JsPlatform.kt
cli/src/jsTest/kotlin/io/github/ehlyzov/branchline/cli/NodeFileIoJsTest.kt
cli/src/jvmTest/kotlin/io/github/ehlyzov/branchline/cli/CompiledArtifactCompatibilityJvmTest.kt
cli/src/jvmTest/kotlin/io/github/ehlyzov/branchline/cli/CompiledNumericRoundtripJvmTest.kt
cli/src/jvmTest/resources/fixtures/compatibility/legacy-v2.blc
conformance-tests/build.gradle
conformance-tests/src/commonTest/kotlin/io/github/ehlyzov/branchline/conformance/ConformNumericContractTest.kt
conformance-tests/src/commonTest/kotlin/io/github/ehlyzov/branchline/conformance/GettingStartedExamplesTest.kt
conformance-tests/src/commonTest/kotlin/io/github/ehlyzov/branchline/conformance/StableApiConsumerTest.kt
conformance-tests/src/commonTest/kotlin/io/github/ehlyzov/branchline/conformance/StdlibSupportContractTest.kt
conformance-tests/src/jsTest/kotlin/io/github/ehlyzov/branchline/conformance/GettingStartedGuideJs.kt
conformance-tests/src/jsTest/kotlin/io/github/ehlyzov/branchline/conformance/NumericJsContractTest.kt
conformance-tests/src/jsTest/kotlin/io/github/ehlyzov/branchline/conformance/StdlibSupportInventoryJs.kt
conformance-tests/src/jvmTest/kotlin/io/github/ehlyzov/branchline/conformance/GettingStartedGuideJvm.kt
conformance-tests/src/jvmTest/kotlin/io/github/ehlyzov/branchline/conformance/NumericJvmContractTest.kt
conformance-tests/src/jvmTest/kotlin/io/github/ehlyzov/branchline/conformance/StableApiInventoryJvmTest.kt
conformance-tests/src/jvmTest/kotlin/io/github/ehlyzov/branchline/conformance/StdlibSupportInventoryJvm.kt
development/INDEX.md
development/evidence/one-zero-contracts-2026-10-18.md
development/evidence/one-zero-platform-2026-10-21/README.md
development/evidence/one-zero-platform-2026-10-21/browser.md
development/evidence/one-zero-platform-2026-10-21/cli-matrix.py
development/evidence/one-zero-platform-2026-10-21/cli.md
development/evidence/one-zero-platform-2026-10-21/docker-recovery.md
development/evidence/one-zero-platform-2026-10-21/linux-harness.py
development/evidence/one-zero-platform-2026-10-21/linux-runtime-config.json
development/evidence/one-zero-platform-2026-10-21/linux.md
development/evidence/one-zero-platform-2026-10-21/pilot-review.md
development/evidence/one-zero-surface-2026-10-18.md
development/planning/one-zero-contracts-2026-10-18.md
development/planning/one-zero-pilot-run-record.md
development/planning/one-zero-platform-2026-10-21.md
development/planning/one-zero-surface-codec-2026-10-18.md
development/service/SERVICE_MAP.md
development/service/VERIFY.md
development/service/generated/change-surface.json
development/service/generated/health-report.json
development/service/knowledge-gaps.yaml
docs/guides/api-support.md
docs/guides/getting-started.md
docs/guides/install.md
docs/guides/migrations.md
docs/guides/pilot-plan.md
docs/guides/platform-support.md
docs/guides/production-use.md
docs/guides/release-readiness.md
docs/language/numeric.md
docs/language/stdlib-support.md
docs/toc.yaml
docs/vm.md
interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/json/JsonOutput.kt
interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/runtime/Numeric.kt
interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/std/StdNumericModule.kt
interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/std/StdStringsModule.kt
interpreter/src/commonTest/kotlin/io/github/ehlyzov/branchline/json/JsonOutputNumericTest.kt
interpreter/src/jvmMain/kotlin/io/github/ehlyzov/branchline/runtime/bignum/BigNumJvm.kt
mkdocs.yml
vm/src/commonMain/kotlin/io/github/ehlyzov/branchline/vm/BytecodeIO.kt
vm/src/commonMain/kotlin/io/github/ehlyzov/branchline/vm/Compiler.kt
vm/src/commonTest/kotlin/io/github/ehlyzov/branchline/vm/BytecodeNumericRoundtripTest.kt
vm/src/jsMain/kotlin/io/github/ehlyzov/branchline/vm/BytecodeNumbersJs.kt
vm/src/jsTest/kotlin/io/github/ehlyzov/branchline/vm/BytecodeNumericAdmissionJsTest.kt
vm/src/jvmMain/kotlin/io/github/ehlyzov/branchline/vm/BytecodeNumbersJvm.kt
vm/src/jvmTest/kotlin/io/github/ehlyzov/branchline/vm/BytecodeExactNumbersJvmTest.kt
```

Exclude the following unrelated or host-generated paths unless separately
reviewed for an expressly required public evidence contribution:

- `.playwright-cli/`, `deep-research-report.md`, `deep-research-report-2.md`.
- Broad `output/` trees: runtime installations/downloads, generated executables,
  local jars, candidate packages, private machine/session/browser logs and
  recordings must not enter source control via directory-wide staging.
- Historical untracked `.class`, JFR trees and `.json.gz` profile samples under
  `development/evidence/vm-*-experiment/`.
- `development/experiments/authoring-2026-10-17/aborted-001/raw-transcripts.tar.gz`,
  `reproduction/frozen-runtime.tar.gz`, and `results/raw-transcripts.tar.gz`.

Do not omit necessary untracked product/test paths simply because they were not
in the tracked diff. The manifest is the selected-path authority, not `git diff
--name-only`. Do not stage the manifest's containing `output/` directory wholesale.
A small reviewed source-hash manifest may be copied into RC evidence if durable
public provenance is needed; do not pretend excluded local raw paths are included
in the public repository.

## Review evidence and limits

Read AGENTS, the RC scope correction, canonical verification contract, release /
Pages / Test Suite workflows, root and module version/packaging wiring, relevant
compatibility and inventory test readers, and changed numeric/codec/Node-I/O
surfaces. Ran read-only `git status`, tracked diff inspection, path searches and a
credential-pattern and absolute-host-path scan of all 68 selected paths.
The scan found no credential material; this is not a blanket certification of
unselected raw outputs. The only `/Users` or `/home` hit is browser.md line 106,
a local Playwright skill path containing the public account name. No container
names or credentials appear in the Docker recovery report; it does contain
nonsecret host disk/volume counts. Replacing the generated change-surface overlay
with an explicitly labelled accepted-RC-path overlay avoids publishing a large
incidental inventory of downloaded runtimes and unselected local evidence. The
parent will preserve the full prior overlay in the excluded local output audit. The Git index was empty when inspected. Root version and
new RC output changes were expected concurrent packaging work.

No new service-contour trigger arises from this review-only artifact. The
accepted implementation's existing contour updates remain part of the baseline.
