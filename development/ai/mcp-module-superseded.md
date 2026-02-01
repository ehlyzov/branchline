---
status: Superseded
depends_on: []
blocks: []
supersedes: []
superseded_by: ['ai/llm-pipelines']
last_updated: 2026-02-01
changelog:
  - date: 2026-02-01
    change: "Migrated from research/mcp.md; superseded by ai/llm-pipelines."
  - date: 2026-02-01
    change: "Marked superseded and pointed to ai/llm-pipelines."
---
# Goal (DRAFT)

## Status (as of 2026-01-31)
- Stage: Superseded.
- Only high-level requirements are captured; no module structure or APIs implemented.
- Next: use `development/ai/llm-pipelines.md` as the active plan.

> Superseded by `development/ai/llm-pipelines.md`. This file is retained for historical context only.

Make a module branchline-mcp where implement everything related to MCP (Multi-Channel Protocol) in Branchline.
What should be provided:
- A method BranchlineMcp::start that starts the MCP server.
- A method BranchlineMcp::stop that stops the MCP server.
- An instrument to describe Branchline language, its syntax and its semantics in a compact way. After that explanation capable model should be able to generate branchline code.
- An instrument to explain branchline cli options and its usage.
- An instrument to interpret branchline code with given input with optionally enabled tracing and explanations.
- An instrument to generate input and output contracts for branchline code.
