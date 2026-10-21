---
status: Implemented
depends_on: []
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-10-21
---
# Published documentation and examples UX audit

User requests critical UX research and a proposal for positioning and site
recomposition after RC publication. Scope: expert walkthrough of the live home,
first-run/editor, learning/install paths and representative released examples;
source-backed content inventory, positioning hypotheses, proposed information
architecture and measurable next-step acceptance. This is not a recruited user
study or authorization to implement/publish a redesign.

Capture fresh screenshots in the in-app browser. Preserve original site and user
localhost tab. Evidence and standalone report live under output/ux-docs-2026-10-21.
Compare public documentation claims with supported surfaces. Separate observable
friction from hypotheses; no invented adoption/speed/productivity claims.

## Completed research

- Fresh public-site walkthrough captured seven flow steps (1280x720 and
  610x915 windows); browser customer-profile execution succeeded.
- Inventoried 40 example files and 45 navigation destinations. Twenty-two
  examples have no category; catalog fallback is Uncategorized.
- Confirmed P1 onboarding defect: First Steps recommends INPUT/$, while
  published RC accepts input.name and rejects INPUT.name / $.name.
- Four representative published examples matched expected JSON in the released
  JVM CLI. Three proposed hero-example inputs also passed. These bounded probes
  are not a complete requalification of the catalog or platform matrix.
- Delivered positioning hypotheses, proposed site architecture, six task-based
  recipes, staged acceptance criteria and a five-participant usability protocol.

Report: [UX audit and site proposal](../../output/ux-docs-2026-10-21/report.html).
Evidence: output/ux-docs-2026-10-21/{capture-manifest,root-access-probes,
representative-example-checks,proposed-hero-checks,example-inventory}.json.

No product implementation, deployment, participant research, conversion claim
or full accessibility audit is included. Recommended implementation begins with
the incorrect root-access guidance and one complete first-success journey.
