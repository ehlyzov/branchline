# Installation at the first encounter

Status: published; local and public newcomer journeys accepted.

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

Public acceptance on 2026-10-21:

- Commit `0e5430236c22ef27626499e024c70b9140166bbd` published by
  [Pages run 39668651824](https://github.com/ehlyzov/branchline/actions/runs/39668651824).
  Clean Linux installation, build and deployment all passed. Public
  `release-metadata.json` identifies that exact SHA/run and v1.0.0-rc.1.
- Initial dispatch 39668514368 failed at checkout because the orchestrator passed
  a short SHA. No site change occurred; the successful dispatch used the full SHA.
- Fresh public home -> Get started -> Playground navigation exposes installation
  and PATH/version instructions, runs BOOK baseline and BOOK/PEN after editing.
  Actual public browser downloads executed with the freshly installed private
  JVM CLI and matched `{"order_id":"A-104","city":"London","skus":["BOOK","PEN"]}`.
- Public home checked at 1280px and 390px; the command wraps without document
  overflow. No browser warnings/errors were captured during the public journey.
- Copy button rendered and showed its success toast; its DOM target contains
  the exact single-line install command. Automated clipboard roundtrip was not
  established: the in-app browser clipboard API/paste retained earlier test input.
  This result does not establish the cause or certify system-clipboard behavior.
- Installer/runtime code and release packages are unchanged. These checks are
  an acceptance walkthrough, not a study with independent first-time users.

Acceptance record may follow the deployed source without rebuilding unchanged
product pages. Screenshots, downloaded files, command results and deployment JSON
remain under the evidence directory above.
