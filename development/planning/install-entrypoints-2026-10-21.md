# Installation at the first encounter

Status: implemented; local newcomer journey accepted, publication pending.

Expose the existing public one-command installer on the home page and at the
start of Get started. Keep the browser example available without installation.
Use one shared install snippet, state macOS/Linux and automatic private runtime,
then show PATH/version verification. Preserve the canonical order example and
its expected values. Make the transition from edited browser files to local
execution explicit, including the expected BOOK/PEN result.

Acceptance: build MkDocs, run existing playground/example checks, inspect home
and Get started in a real browser at desktop and narrow widths, follow the order
example, edit PEN quantity, download both current files and execute them with a
fresh private-runtime CLI. Compare with independently expected JSON. Publish
the exact accepted commit through Pages, check public metadata, inspect both
public entrypoints and repeat the published newcomer path. Installer/runtime
implementation and language semantics are unchanged. Preserve unrelated audit
changes; user approved a separate INDEX addition while preserving the audit edit.

Local acceptance on 2026-10-21:

- Existing `npm test` passes, including executable intro examples, current-edit
  exports and all 36 recipe cases (13 expected errors). MkDocs builds; only
  pre-existing benchmark link warnings remain.
- Home and Get started inspected at 1280px and 390px. Long install command now
  wraps inside its own block, with its copy button visible; no document overflow.
- Followed home -> Get started -> order Playground, ran BOOK baseline, edited
  PEN quantity to 1 and got BOOK/PEN. Downloaded actual current program/input
  through browser controls; a fresh private JRE installation ran those files and
  matched independent expected JSON exactly. Repeated download suffixes were
  observed and the guide now explains restoring the documented filenames.
- Evidence and screenshots: `output/install-entrypoints-2026-10-21/`.
