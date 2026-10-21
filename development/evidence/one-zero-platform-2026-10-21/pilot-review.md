---
status: Reviewed
depends_on: ['planning/one-zero-platform-2026-10-21']
last_updated: 2026-10-21
---
# Platform policy and external pilot admission review

Independent document review of the current candidate working tree. This report
does not run builds, certify sibling agents' pending platform results, or amend
the existing product contracts. Parent acceptance must bind final results to the
actual artifact/source hashes. The published `0.11.0-alpha.1` and this candidate
are different identities; prior publication cannot qualify the new v3 writer.

## Defensible platform policy

`install.md` currently says Java 21+ and Node 18+; release-readiness explicitly
calls these prerequisites, not evidence for every newer runtime or OS. Neither
document freezes an OS/CPU matrix or support window. A passing macOS result
cannot close Linux installation or operational admission.

Recommended candidate policy, subject to actual passing results:

| Surface | Intended runtime scope | Admission boundary |
| --- | --- | --- |
| JVM CLI and embedding | Java 21 and 25 LTS; exact vendor/update recorded | macOS arm64 is an available development cell. The selected Linux pilot distribution/CPU is a separate required cell, still to be fixed and exercised if unavailable locally. Java 21 class-file compatibility alone proves neither cell. |
| Node CLI | Node 22 and 24 LTS; exact updates recorded | Record each tested OS/CPU. Node 18 is a legacy launch-compatibility check; Node 25 is an incidental compatibility result. Neither is a maintained-runtime recommendation. |
| Browser playground | Exact real browser/engine versions recorded in this slice | Freeze the tested candidate assets and actual editor/run/error/recovery results. Playwright Chromium/Firefox/WebKit results name those engines; WebKit automation does not certify the Safari application. |
| Other runtime majors, OSes or CPUs | Unadmitted until explicitly qualified | Do not imply Windows, Intel macOS, all Linux variants or arbitrary future Java/Node/browser releases passed. |

As of this review, Java 21/25 are LTS according to the
[Oracle roadmap](https://www.oracle.com/java/technologies/java-se-support-roadmap.html).
The [Node release table](https://nodejs.org/en/about/previous-releases) lists
22/24 as LTS and 18/20/25 as EOL. Runtime vendor maintenance is separate from
Branchline compatibility and does not establish a Branchline security-support SLA.

For this Alpha candidate, support can mean: exact published cells are tested and
eligible for reported-regression investigation; unlisted cells are best effort.
Publish vendor/update versions, artifact hashes and any unavailable required cell.
Before 1.0, freeze the maintained-major window and its EOL transition rule, and
reconcile it with the existing promise that raising an admitted minimum during
1.x requires a major release. Do not silently reinterpret `18+` as an indefinite
maintenance promise, or drop an admitted 1.x guarantee through an EOL footnote.

## Material issues and gate disposition

1. **Public parity overclaim:** `README.md:103` says the JVM/JS runtimes behave
   identically and have no external dependencies. That exceeds the explicit
   numeric platform limits and ignores the pinned ASM dependency of the JVM
   preview backends. Narrow this wording to the declared common profile and
   tested cases before using the README as the candidate support statement.
2. **Platform scope remains a policy decision:** do not declare general platform
   acceptance from macOS checks alone. If Linux execution cannot be obtained,
   record the failed availability check once and keep the intended Linux/pilot
   cell open. A platform slice can close its completed cells while the wider
   release gate remains open.
3. **Candidate identity and release qualification remain distinct:** existing
   local evidence is a dirty working tree based on `9de86a0`, not a release of
   that commit. Freeze an immutable candidate identity, CLI/package version,
   source manifest and distribution/asset checksums before external installation.
   A stable release additionally needs its own required release/publication gates.
4. **No new numeric parity blocker within the declared boundary:** the API,
   stdlib and numeric documents consistently distinguish bounded common integers,
   JVM exact explicit numbers and limited JS storage. `DEC` does not grant JS
   arbitrary precision; safe JSON emits explicit numbers as strings; artifact v3
   has its own reader admission. These are explicit limits to carry into the
   pilot, not reasons to claim universal JVM/JS equivalence.
5. **Experimental exclusions remain enforceable boundaries:** RANGE/PAD,
   untrusted arbitrary programs, shared/tracing/reentrant facade use, generated
   execution backends and broad snapshot/ABI portability are outside the admitted
   profile. Do not use their mere presence in a package to widen platform support.

The preceding inventory/codec acceptance already records the bounded semantic
gates; this review requests no repeat of unchanged JVM/Node semantic suites.
Actual fresh distribution/browser evidence belongs to the platform agents and
the parent's acceptance report. It is not fabricated here.

## External pilot blockers — all still open

The [pilot plan](../../../docs/guides/pilot-plan.md) and
[run-record template](../../planning/one-zero-pilot-run-record.md) agree on the
following requirements. The template is unfilled; no independent participation,
representative traffic or elapsed observation is established by it.

| Blocker | Accountable owner | Evidence required to close |
| --- | --- | --- |
| Two independent integrations | Integrators A/B, named before admission | Two separately owned installations of the same frozen candidate; introductory examples plus their own fixtures; commands, failures, assistance and maintenance effort recorded. Two author/agent runs do not qualify. |
| Immutable candidate and environment | Release reviewer + each integrator | Release/version, source SHA or immutable manifest, artifact checksums, transform/schema hashes, engine/fallback mode, JVM/vendor/update, OS/CPU/heap and host configuration. Retain source for recompilation; do not rely on all-1.x `.blc` portability. |
| Representative data and business oracle | Domain owner | Approved provenance/redaction, corpus hash, real sizes/skew/peaks, malformed/null/missing/duplicate/order cases; independent expected output/error hash authored before replay. Choose cents or decimal-string wire profile explicitly and prove intermediate numeric bounds. |
| Frozen operational thresholds | Operations owner | Numeric byte/row/concurrency/queue/memory/timeout/p95/p99 ceilings, baseline regression budget, alerts and retention. Host-enforced breach behavior and representative replay must pass with no unexplained difference, silent drop or duplicate effect. |
| Tested rollback | Operations owner + integrator | Pinned previous baseline/artifacts/configuration; recovery-time objective; switch/drain/quarantine and idempotent in-flight reconciliation; measured recovery within objective and zero lost/duplicate effects. A restart of the in-memory demo is insufficient. |
| Independent maintenance | Each integrator + domain owner | v2 currency and independently specified field/default exercises with held-out oracles; elapsed effort, assistance, redeployment and rollback recorded. |
| Two completed observation windows | Integrators + operations owner | At least 14 consecutive representative days per integration, including peaks; reconciled daily dispositions, correctness/error summaries, latency, allocation, heap-after-GC, RSS, queue/restarts and incident closure. Zero traffic does not count. |
| Go/no-go decision | Named release reviewer | Both accepted records, frozen-threshold comparison, explicit excluded workloads and signed evidence-linked decision, plus remaining release gates. |

A correctness or limit failure blocks promotion. After correction, rerun the
relevant replay/rollback gates and restart the 14-day window for the changed
candidate; never adjust thresholds after observing failure. Shadows must not
acknowledge, update the primary store or publish real events. A VM runtime failure
must not automatically replay effects in the interpreter.

The nearest external result is two completed admission records with named owners,
fixed candidate/data/oracles/limits and successful rollback drills. Local platform
acceptance cannot supply these people, datasets or elapsed days. Passing those
records starts observation; it does not immediately approve stable 1.0.
