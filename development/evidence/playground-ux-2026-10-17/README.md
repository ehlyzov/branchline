# Playground UX audit — 2026-10-17

Scope: the published Branchline editor, first-use → edit/run → recover, plus
independent assessment of 1.0 readiness. Baseline: commit
`449f559ece55ef14657c34a56266e472bd22d665`, public URL
https://ehlyzov.github.io/branchline/playground/. Fresh screenshots and interaction
observations use the Codex in-app browser at 1280×720 and 390×844. Synthetic input
only; all execution remains in the browser. No full WCAG claim or user study.

## Questions raised by the critique

| Priority | User question | Reproduced baseline | Intended correction |
| --- | --- | --- | --- |
| P1 | Will filtering erase my work? | After editing `OUTPUT { amount: input.n * 2 }` and `{"n":11}`, Category → Getting Started silently replaced both with Hello/Dana. | Filtering preserves the selected draft; switching keeps per-example session drafts; explicit reset protects modified work. |
| P1 | Does this output correspond to my current input? | Run n=9 → amount=18; change to n=11 without Run. Output still says 18 without a stale indicator; Inspect stays compatible. | Mark results/inspection outdated after code, input or execution-option changes. |
| P1 | Can I trust a successful XML result? | `OUTPUT input` with XML `<root><amount>25</root>` succeeds and returns a root/amount tree. | Validate well-formed single-root XML before conversion; retain valid mappings. |
| P1 | How do I recover a stuck run? | Source has no worker error/messageerror handler or Stop; no browser hang induced in baseline. | Explicit Stop/recreate and recoverable worker failure handling, verified after implementation. |
| P2 | Where do I start? | Desktop first viewport has no Run/editor. Mobile intro occupies almost a screen; stacked controls leave ~200px blank gaps. | Put Example/Run first, disclose advanced controls, fix column flex sizing. |
| P2 | Which input variable and program shape are supported? | Help says only a TRANSFORM body and `msg`, while actual examples use `input` and full TRANSFORM declarations. | Accurate concise help tied to executable behavior. |
| P2 | Which editor/control has focus? | Both Monaco editors have the same generic accessible name; Inspect tabs lack complete keyboard/relationship semantics. | Distinct editor names, linked tabs with keyboard navigation, announced result/error status. |
| P2 | Does opening a new tab transfer my changes? | Link opens only original selected example, not edited content. | Label the action explicitly as opening the original example. |

Strengths retained: 40 runnable examples, JSON/XML formats, inferred contracts,
structured inspection and trace, executable default example, local processing,
keyboard Run. The baseline Cmd+Enter also worked from the input editor; absence
of a direct input command in source was not treated as proof of a missing shortcut.

Baseline screenshot files (local artifacts): `output/ux-2026-10-17/before-editors.png`,
`before-stale-output.png`, `before-mobile.png`, `before-mobile-controls.png`.
The current run captured and inspected each image. Screenshots support visual
claims; browser AX state and actual outputs support interaction claims.

## Readiness

See [independent readiness review](readiness-review.md). UX fixes do not close
the external operational pilot or define a stable support/compatibility contract.
The selected target remains Alpha; publishing 1.0 is outside this change.

## Local acceptance

Parent reviewed the changes and actual browser behavior. Accepted steps:

1. Entry: healthy — Example and Run plus editor headings appear within the
   1280×720 first viewport. At 390×844 Run is visible without scrolling; settings
   no longer have tall empty gaps. Measured document width equals viewport (390).
2. Edit/run: healthy — n=7 produces 14; changing n to 11 marks output and Inspect
   out of date. Choosing compact JSON then keyboard Run produces `{"amount":22}`.
3. Preservation: healthy — Getting Started filter keeps the custom customer
   draft; switching to Hello and back restores both source and n=11. Cancelling
   Reset keeps edits. Original-example links explicitly exclude transferring edits.
4. Recovery: healthy — malformed XML reports an input error; fixing the closing
   tag yields the independently expected root/amount mapping. Syntax error
   `OUTPUT { broken: }` is now line 1, column 18 in Run and Inspect; fixing it to
   `OUTPUT { value: "TRANSFORM" }` succeeds.
5. Stop: healthy in the final clean-build repeat — nested 10,000×10,000 loops
   were interrupted; replacing the code with `OUTPUT { recovered: 42 }` and
   clicking Run returned 42. An earlier intermediate-build keyboard sequence
   stalled after Stop; a subsequent Stop recovered it. Both final clean-build
   button and keyboard sequences passed; the keyboard repeat returned
   `{"recovered":11}` after replacing code/input. No console errors were recorded
   in that final local tab. Do not infer stress-test coverage from these repeats.
6. Accessibility: improved, bounded check — distinct editor names, status/alert
   semantics, ArrowRight changes Inspect tab and focus, correct error position.
   Full screen-reader/contrast certification remains outside this audit.

Final `CHROME_BIN=... ./gradlew playgroundBuildAssets --console=plain` and pinned
MkDocs build passed; logs are in `output/ux-2026-10-17/assets-final.log` and
`mkdocs-final.log`. `npm test` includes actual-runtime wrapper-coordinate checks
and independent XML expectations. JVM facade 16 tests and unfiltered interpreter
Node 64 tests pass; see [worker evidence](worker-and-wrapper-checks.md). TypeScript
and contour audit also passed. No performance-sensitive engine change was made.

After screenshots: `after-desktop.png`, `after-mobile.png`,
`after-mobile-settings.png`, `after-stale-output.png`, `after-correct-error.png`
under `output/ux-2026-10-17/`. Dark-theme stale-status visibility was also inspected;
Monaco retains its existing light editor appearance. Session drafts intentionally
expire on reload. This is a scoped usability improvement, not a full redesign.

## Publication

Publish this accepted commit through the existing Pages workflow, then independently
verify public metadata and browser behavior. The existing Alpha release tag/assets
remain immutable; this is a follow-up editor/site update, not a 1.0 release.
