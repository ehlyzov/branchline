---
status: In Progress
depends_on: ['planning/next-release-scope']
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-10-17
---
# v0.11.0-alpha.1 release execution

Authorized: finish release issues with GPT-6.1 Sol agents, independently verify,
commit, push, deploy, and test the public playground. Preserve unrelated
deep-research-report*.md files. Existing implementation/evidence changes belong
to this chat's accepted work and form the release candidate.

## Work and acceptance

1. Align Gradle, generated CLI version, packages and release tag; Java 21 docs.
   Bind manual workflow checkout, release assets and deployment to one commit.
2. Explicit unstable opt-in preview boundary for shipped research APIs; pinned
   ASM disclosure and fix the legacy custom-bignum observation risk. Ordinary VM
   remains default. No default performance promotion.
3. Define compiled artifact version compatibility, add old writer fixtures and
   reject unsupported new formats with actionable migration guidance.
4. Parent review, JVM/Node gates, distributions and documentation build, packaged
   source/compiled smoke; remote CI and browser checks against exact candidate.
5. Commit selected project work, push main, publish alpha prerelease assets and
   deploy Pages from that SHA. No unrelated reports or generated binary evidence.
6. Fresh public-browser acceptance: examples, independent transforms, edits,
   syntax error and recovery, resource and console checks. Record SHA/URL/time;
   disable the waiting heartbeat once complete.

Remote release/publication failures are blockers, not successful completion.
Keep measured performance claims scoped to the existing recorded experiments.
