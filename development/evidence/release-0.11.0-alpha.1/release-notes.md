Branchline v0.11.0-alpha.1 remains an Alpha prerelease.

- Isolates compiled CLI VM workers under `--jobs`, preserves ordered results and
  failure cleanup, and synchronizes the bounded JVM regex cache.
- Propagates VM runtime errors once; compatibility fallback is limited to
  compilation failure before execution. Includes numeric/domain and deterministic
  subset fixes plus ordinary VM scaling and tracing improvements.
- Adds explicitly unstable opt-in VM research APIs. Ordinary VM remains default.
  JVM generated-loop previews include ASM 9.9; preparation and retained-memory
  tradeoffs remain. No general advantage over the interpreter, JSONata or Kotlin
  is claimed.
- Aligns CLI/package/tag versions. JVM distributions require Java 21+, Node18+.
- Writes compiled format2 with a required marker; accepts the tested legacy v1
  surface. Older runtimes reject new files: retain source and recompile for target
  versions. See the migration guide.
- Binds published assets and Pages to the built commit, with checksums and public
  deployment metadata.

Local acceptance: 1,236 tests passed, one existing skip, including 62 Chrome tests;
JVM/Node packaged source and compiled roundtrips, independent expected outputs,
legacy fixture, numeric observer regression and Kotlin opt-in boundary passed.
The actual MkDocs site and Diplodoc documentation builds passed.

Remote CI and public playground acceptance are recorded below after publication.
