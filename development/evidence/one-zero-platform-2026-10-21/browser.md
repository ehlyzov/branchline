# Browser platform acceptance — 2026-10-21

Fresh local candidate passes all 14 browser acceptance cases in each of Chrome
155.0.8059.40 and Playwright WebKit 26.6 on macOS 27.0.1 (26A434), arm64.
This establishes 28 successful checks for these two engines on this host. Firefox
was not accepted: the installed runtime could not establish a usable session.
Real Safari, Windows, Linux desktop browsers, and mobile devices remain untested.
WebKit is an engine check, not evidence that Safari itself was exercised.

## Candidate and execution identity

- URL: `http://127.0.0.1:18765/playground/`.
- Frozen site: `/tmp/branchline-platform-site`, built by the parent using fresh
  `./gradlew playgroundBuildAssets`, followed by `mkdocs build --strict`.
- Server: `python3 -m http.server 18765 --bind 127.0.0.1 --directory /tmp/branchline-platform-site`.
- Working-tree candidate based on `main`; exact base HEAD and 144 frozen-site file
  hashes are recorded in `output/playwright/platform-2026-10-21/asset-manifest.json`.
  All 100 files shared with `docs/assets` matched byte for byte; no mismatches.
  The parent owns the complete source manifest for the uncommitted candidate.
- Playwright CLI 0.1.22, invoked through the skill's `playwright_cli.sh`, Node
  v25.3.0. Sessions were isolated as `platform-chrome` and `platform-webkit`.
  Neither the ambient port 8765 site nor the public deployment was used.
- `assets/playground.js` SHA-256:
  `6949bd7ef51d1be8289922404ac7890b30dd926aca01f85ad101c23369f149b4`.
- `assets/branchline-interpreter-DQLDaDbS.js` SHA-256:
  `ced50a036dcf030e50380204c33facb4bf5b7c3352abe56ecdff4525245ea13d`.
- `assets/worker-B_rem5Xk.js` SHA-256:
  `5426ea05b4991632e8c420a939a37e1e11628c8106fc9d0e1800b43bc2f5d95f`.

## Accepted cases

Both engines passed the same independent assertions in `acceptance.js`; exact
expected/actual JSON, raw output, engine version, user agent, timestamps and network
observations are in `chromium-acceptance.json` and `webkit-acceptance.json`.

| Case | Checked result |
| --- | --- |
| Default customer profile | Ada Lovelace, id 42, standard loyalty, London |
| Numeric catalog example | Power 8, root 3, decimal power string `1.953125` |
| Hello catalog example | `Hello, Dana` |
| Independent transform | Input 7 becomes 14, label retained |
| Stale output and Inspect | Editing marks both out of date; previous output remains visible |
| Edit program and rerun | Multiplier 3 produces 21 |
| Edit input and rerun | Input 11 produces 33; edited label retained |
| Switch example | Numeric example loads and runs after an edited draft |
| Restore draft | Switching back restores edited code and input, producing 33 |
| Independent filter/projection | Active rows a/c retained, totals 12/10; inactive row omitted |
| Numeric regression | Ordinary floor/ceil -2/2; explicit results safe-encoded as strings; ties [2,4,-2,-4]; hundreds [200,-200,400,-400]; input fraction and 7/2 both 3.5; explicit decimal string `1.25` |
| Syntax failure | Nonempty parser diagnostic for missing output expression |
| Recovery | Valid edited program runs; error panel clears; result 14 |
| Local resources and runtime | No local HTTP errors, failed local requests, or uncaught page errors |

The numeric oracle follows documented half-even arithmetic and safe JSON encoding:
explicit integer/decimal kinds are strings even when mathematically small. Ordinary
floating results remain JSON numbers. Constants are asserted independently rather
than copied from runtime output.

Screenshots for projection, numeric behavior, syntax failure and recovery are saved
for each engine under `output/playwright/platform-2026-10-21/`. The parent inspected
the Chromium recovery screenshot; the browser agent inspected WebKit numeric output.
The checked catalog had 40 selectable examples; this is a sampled execution gate,
not an assertion that every catalog entry was run.

## Console, resources, and launch limitations

On the first navigation, both engines requested
`https://api.github.com/repos/ehlyzov/branchline/releases/latest` and received HTTP
404; the subsequent repository metadata request returned HTTP 200. Initial console
logs retain that resource error. This is an external documentation metadata request,
separate from the local playground assets and interpreter workers. The visible
repository header rendered, and the playground worked. No claim of an entirely
error-free first navigation is made. During the final harness reload, no console
errors were emitted (WebKit emitted one font preconnect information message);
metadata caching means that clean reload does not erase the earlier 404. Both
`*-initial-console.log` and cumulative `*-network.log` preserve that distinction.

Firefox 156.0 (Playwright build 1553, application BuildID 20260927183637) downloaded
successfully through the CLI. Headless launches failed with `Could not find profile
folder`, including an explicit profile. A headed retry using an existing `/tmp`
profile timed out after 180 seconds and logged macOS `sandbox_extension_issue_file_to_process`
`Operation not permitted` plus GPU/helper errors. These are launch failures, not
Branchline test failures or passes. Exact logs are `firefox-launch.log` and
`firefox-headed-launch.log`. No OS security settings were changed. Safari acceptance
and a working Firefox run are still admission obligations if those browsers are
included in the published support matrix.

Browser runtime installations encountered DNS failure on the first download mirror;
the documented CDN fallback succeeded. Installation logs are preserved.

## Harness corrections and reproducibility

Earlier attempts are retained as `*-harness-*-attempt.log` and
`*-oracle-explicit-integer-attempt.log`. They are not accepted product failures:
replacing selected Monaco text without first clearing it retained auto-closing tokens;
inserting multiline code simulated typing and added a closing brace; flattening a
LET/OUTPUT separator invalidated that program. The final harness clears the editor
with Select All/Backspace, enters single-line programs through keyboard input, and
checks the exact editor value before Run. Its projection uses one OUTPUT expression.
The initial numeric oracle also incorrectly expected JSON numbers for explicit
FLOOR/CEIL results; the final case separately covers ordinary and explicit domains.

Run the CLI with a fresh named browser session, navigate to the frozen candidate,
take a snapshot, and execute:

```sh
/Users/eugene/.codex/skills/playwright/scripts/playwright_cli.sh -s=platform-chrome run-code --filename output/playwright/platform-2026-10-21/acceptance.js
```

Replace the session with an opened WebKit session for the second engine. The harness
asserts through the real UI and records screenshots; it does not call interpreter
internals. Expected output constants are contained in the harness. The accepted
records are the final `*-acceptance.json` files, not earlier exploratory attempts.
No product code, release, commit, push, deployment, or field-pilot claim is part of
this browser evidence.
