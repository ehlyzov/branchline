---
status: Implemented; public acceptance pending
last_updated: 2026-10-17
---

# Playground UX and 1.0 readiness

User-authorized follow-up to the published 0.11.0-alpha.1 release. Audit the actual
public interface, fix confirmed friction, and assess 1.0 independently of UI polish.

## Observed baseline

Fresh public browser capture on 2026-10-17: first 1280x720 viewport contains no
editor or Run control. Multiple introductions, catalog filters and execution
options precede the main task. Help says body-only/msg while the example uses
input; both Monaco editors expose identical generic accessible names. Source
inspection identifies an initial-render Run shortcut closure and automatic
example resets on catalog filtering. Verify interaction consequences independently.

## Priority and scope

1. P1: preserve edited work across example/filter/reset actions; distinguish
   current versus outdated results; ensure keyboard Run uses current options.
   Add Stop/recreate and worker failure recovery. Reject malformed XML before
   parsing (see playground-ux-xml-2026-10-17.md for the dedicated validation slice).
2. P2: task-first controls, accurate help, unique accessible editor names,
   announced results/errors and keyboard-operable Inspect controls. Preserve
   existing design and every example/advanced capability.
3. Independent 1.0 readiness: map supported contracts and operational gaps to
   explicit release criteria. No 1.0 publication or universal performance claim.

## Acceptance

- Record current screenshots and reproducible issues before changing UI.
- Build playground assets, exercise meaningful state regressions, and verify
  actual browser editing/run/error/recovery, switching and keyboard scenarios.
- Confirm layout and control access at narrow width where browser permits.
- Save evidence and report unresolved accessibility/operational limits honestly.
- Parent reviews changes and evidence; only accepted changes may be published.

Evidence: `development/evidence/playground-ux-2026-10-17/` and local screenshots
under `output/ux-2026-10-17/`.

Parent local acceptance and independent 1.0 decision are recorded in the evidence
README. Final actual-runtime worker diagnostics also correct generated-wrapper
coordinates; see playground-ux-coordinates-2026-10-17.md. Publication remains a
separate acceptance step; no 1.0 tag is authorized by this readiness assessment.
