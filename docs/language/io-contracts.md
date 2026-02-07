---
title: I/O Contracts Audit
---

# I/O Contracts Audit

This audit lists known JSON/XML conversions that can reduce fidelity relative to in-memory Branchline values. The CLI reports these as conversion warnings on stderr.

| Loss point | Trigger | Warning behavior | Mitigation |
| --- | --- | --- | --- |
| XML comments dropped | XML input contains `<!-- ... -->` | Warns that comments are dropped during parsing. | Preserve comments in source artifacts outside parsed payloads. |
| XML processing instructions dropped | XML input contains non-declaration processing instructions (`<?...?>`) | Warns that processing instructions are dropped during parsing. | Keep processing instructions out of data payloads consumed by Branchline. |
| XML mixed-content ordering collapse on input | XML input has both element children and text segments in one node | Warns that mixed-content ordering is not preserved in map form. | Use explicit fields instead of mixed text + element interleaving when order matters. |
| JSON bytes become base64 text | JSON/json-canonical output includes `ByteArray` | Warns that bytes are base64-encoded as JSON strings. | Decode base64 at consumers that require raw bytes. |
| json-canonical key reordering | Output format is `json-canonical` and object keys are not already lexicographic | Warns that keys are sorted lexicographically. | Use `json`/`json-compact` when source insertion order must be preserved in text output. |
| Extended JSON numeric precision risk | Output format is JSON/json-canonical with `--json-numbers extended` and large numeric values | Warns that downstream JSON parsers may lose precision. | Prefer `--json-numbers safe` (default) for precision-preserving string emission. |
| XML mixed-content ordering collapse on output | XML output includes both text markers (`$`, `$n`) and element children in one node map | Warns that exact interleaving cannot be reconstructed from map form. | Model interleaved data explicitly before XML output, or avoid mixed-content nodes. |

## Notes
- Warnings are diagnostics; they do not fail execution.
- Contract strictness (`--contracts strict`) is separate from conversion-loss warnings.
