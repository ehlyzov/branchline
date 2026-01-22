# Task: Branchline LLM Interaction Pipelines (Planitforme-inspired)

## Goal
Enable Branchline to **describe, execute, and visualize** LLM interaction logic similar to the app. The end state should let a user design a pipeline (prompts + tool calls + schema validation), publish it to a JVM engine, and invoke it via REST while also giving a clean UI for prompt inspection/editing and quick experiments.

## Source Analysis (app repo)
Key capabilities to match or improve:
- **Prompt-driven ingest**: `backend/prompts.py` defines structured prompts that return JSON with either `type=direct` or `type=plan`, plus a short declarative pipeline list.
- **Plan execution**: `backend/main.py::_execute_ingest_plan` runs a sequential plan where each step is `tool` or `llm`, validates output against JSON schema, and attempts **LLM repair** on schema mismatch.
- **Tool registry**: `backend/mcp_registry.py` enumerates tools, plus a prompt-friendly “tool list” string inserted into system prompts.
- **MCP server**: `mcp/server.js` exposes tool metadata and `tools/call` over MCP, proxying to REST.
- **LLM providers**: `backend/llm_client.py` supports OpenAI/Anthropic/OpenRouter with JSON response formatting and tool definitions.
- **Debug/tracing**: ingestion and plan execution store request/response payloads and token usage; `trace_id` links multi-step interactions.
- **Agent/tool scoping**: `MCP_TOOLS_FEATURE.md` + agent CRUD store **available tools per agent** and expose them in the UI.

## Decisions (from user)
- Pipeline definitions stored in **YAML** (JSON allowed). Files are git-tracked.
- Prompt Studio is a **separate UI package** (standalone product). MVP: view/edit/run.
- **Sequential execution only** for now.
- **Trace retention**: store full prompts + full outputs in debug mode. Prompts are editable; traces must capture the exact prompt content + parameters used.
- UI is **DSL-first**: UI edits update YAML, then render visualization (Mermaid/PlantUML style).
- Single mutable pipeline version is OK, but support **prompt variants** for experimentation.
- Repair is enabled by default, but explicitly logged and metered (repairs per model/prompt).
- **Hybrid integration**: REST powers Prompt Studio, publishing, and JVM execution; MCP wraps “run pipeline as tool” + “list pipeline tools” for external agents.
- Per-agent tool allowlist is **optional** (default allow-all).
- LLM providers: **OpenAI, Anthropic, OpenRouter** (match the app).
- **Template semantics**: hybrid; default string injection, `{{{var}}}` or `{{var|json}}` for JSON insertion.
- **`when` language**: minimal boolean now, designed to extend later (see grammar below).
- **Prompt composition**: shared rules allowed; rendered as `<sharedRule name="...">...</sharedRule>` in final text; include syntax `{{> common_policy}}`.
- **Repair policy**: dedicated repair model allowed, with retry count and per-step opt-out.
- **Publish gate**: block on schema errors/missing prompts; warn on softer issues.
- **Trace redaction**: raw by default in debug, with configurable redaction paths.
- **Versioning signal**: store pipeline hash; add git commit hash when available.
- **Templating mode**: lenient by default (warn on missing vars), with per-step strict option.
- **Storage**: file-only for pipelines and prompts.
- **Prompt evaluation**: manual for now.
- **Schema system**: JSON Schema (runtime + UI validation).

## Proposed Branchline Capabilities
### 1) Pipeline Description Model (YAML-first)
Support a first-class pipeline schema with:
- **Step types**: `llm`, `tool`, `transform` (Branchline function), `validate`, `repair` (optional).
- **I/O contracts**: JSON Schema or Branchline types for each step; auto-validate at runtime.
- **Context object**: shared `context` for prior step outputs and user metadata.
- **Prompt templates**: named prompt fragments with variables and versioning.
- **Prompt variants**: allow multiple prompt versions/variants under a stable prompt name.
- **Tool registry hooks**: MCP tool metadata injected into prompts and made available for runtime calling.

### 2) JVM Execution Engine
- Execute pipelines deterministically on JVM.
- LLM step supports:
  - provider selection and per-step parameters
  - JSON-only mode and schema validation
  - automatic **repair loop** when schema mismatch occurs (bounded retries, opt-out per step)
- Tool step supports:
  - MCP calls (std.io or HTTP)
  - REST calls for non-MCP tools
- Traceable execution with per-step inputs/outputs and timing.
- **Repair metrics**: capture repair counts and rates per prompt/model.

### 3) REST Service (Branchline Runtime API)
Expose endpoints to:
- **Register/publish pipelines** (by name + version).
- **Execute pipeline** with input + optional overrides.
- **Fetch traces** and outputs for debugging.

### 4) Pipeline UI (Prompt Studio)
A thin web UI (separate package) that:
- **Visualizes pipeline DAG** (sequential to start; allow branching later) with Mermaid/PlantUML-like style.
- DSL-first: **editing updates YAML**, then re-renders visualization.
- Shows all prompts used (system/user/tool list) in a clean, editable view.
- **Runs experiments** with test input, displays raw LLM responses, and highlights schema violations.
- Supports **prompt variants** and simple A/B comparisons.
- Can **publish** to the JVM engine via REST.

### 5) branchline-mcp module (new)
Scope the module to:
- Start/stop MCP server that exposes Branchline pipelines as tools.
- Provide “tool manifest” describing pipeline inputs/outputs.
- Offer an adapter to call external MCP tools during pipeline execution.

### 6) LLM-Generated Branchline Programs
Enable LLMs to generate Branchline DSL for data shaping tasks, e.g.:
- “Find tasks matching a filter for the next two weeks.”
- LLM outputs DSL, Branchline runs it, returns the filtered list.

## Prompt Storage Proposal (inline first, optional file path)
Default to **inline** variants in `prompt.yaml` for simplicity. Allow `path` for larger prompts.

- `prompts/<prompt_id>/prompt.yaml`

Inline example:
```yaml
id: routine_ingest
label: Routine ingest
owner: core
variants:
  - id: A
    label: baseline
    inline: |
      You are a routine structurer...
  - id: B
    label: shorter system prompt
    inline: |
      Structure routines and return JSON...
shared_rules:
  - id: common_policy
    inline: |
      Always return JSON only.
```

Optional file-based variants (for long prompts or better diffs):
```yaml
variants:
  - id: A
    label: baseline
    path: A.md
```

Notes:
- Variant **A** is the default unless overridden.
- Traces store full prompt text + parameters + a hash to identify the exact revision used.

## Prompt Parameterization
Variables are resolved at runtime from `input` + `context` + system-reserved values.

Suggested syntax: `{{var}}` with dot-paths for nested values.
- `{{input.user_text}}`
- `{{context.user.timezone}}`
- `{{tools.list}}` (for tool registry injection)
JSON insertion (object/array) uses `{{{var}}}` or `{{var|json}}`.

Resolution order:
1) explicit `params` passed to the step
2) `input` payload
3) `context` (prior step outputs + user metadata)
4) reserved/system values (`tools`, `model`, `pipeline`)

If a variable is missing:
- default behavior: **warn and set empty string** (lenient)
- optional: per-step `strict: true` to fail validation on missing vars
- optional: allow `{{var | default:""}}` for safe fallback

## Prompt Composition (Shared Rules)
Shared rules are reusable blocks injected into prompts. They are rendered as tagged blocks in the final prompt text to preserve explainability, e.g.:
```
<sharedRule name="common_policy">
Always return JSON only.
</sharedRule>
```
Include syntax (chosen):
- `{{> common_policy}}` (handlebars-like)
UI should display shared rules as distinct sections and allow editing in place.

## Minimal Expression Grammar (`when`)
Target is intentionally small and safe. Example expressions:
- `{{steps.build.output.type}} == "plan"`
- `exists(steps.build.output.plan)`
- `exists(input.user_id) && {{steps.build.output.type}} != "direct"`

EBNF (v0):
```
expr      := or_expr
or_expr   := and_expr ("||" and_expr)*
and_expr  := cmp_expr ("&&" cmp_expr)*
cmp_expr  := value (("==" | "!=") value)?
value     := string | number | boolean | null | path | exists
exists    := "exists" "(" path ")"
path      := ident ("." ident)*
ident     := /[A-Za-z_][A-Za-z0-9_]*/
string    := '"' .* '"'
```

Notes:
- `path` resolves against `input`, `context`, and `steps.<id>.output`.
- Only `==` and `!=` are supported in v0.
- Extend later with `>`, `<`, `in`, or functions as needed.

## Example Pipeline (ingest-like)
```yaml
id: routine_ingest
label: Routine ingest
version: 0.1.0
inputs:
  schema:
    type: object
    properties:
      user_text: { type: string }
      user_id: { type: integer }
    required: [user_text]
steps:
  - id: build_prompt
    type: llm
    prompt_id: routine_structurer
    prompt_variant: A
    model:
      provider: openai
      name: gpt-4o-mini
      temperature: 0.2
    params:
      user_text: "{{input.user_text}}"
      timezone: "{{context.user.timezone}}"
      tool_list: "{{tools.list}}"
    expects:
      schema:
        type: object
        properties:
          type: { type: string, enum: [direct, plan] }
          pipeline: { type: array, items: { type: string } }
          direct: { type: object }
          plan: { type: object }
        required: [type]
    repair:
      enabled: true
      max_attempts: 2
      model:
        provider: anthropic
        name: claude-3-5-sonnet-20241022

  - id: run_plan
    type: transform
    when: "{{steps.build_prompt.output.type}} == 'plan'"
    function: execute_ingest_plan
    input:
      plan: "{{steps.build_prompt.output.plan}}"
      context:
        user_text: "{{input.user_text}}"
        user: "{{context.user}}"

  - id: normalize_direct
    type: transform
    when: "{{steps.build_prompt.output.type}} == 'direct'"
    function: coerce_routine_payload
    input:
      direct: "{{steps.build_prompt.output.direct}}"

outputs:
  schema:
    type: object
    properties:
      routine: { type: object }
      routines: { type: array, items: { type: object } }
      questions: { type: array, items: { type: object } }
```

## Pipeline YAML Schema (skeleton)
```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "type": "object",
  "required": ["id", "steps"],
  "properties": {
    "id": { "type": "string" },
    "label": { "type": "string" },
    "version": { "type": "string" },
    "inputs": { "type": "object" },
    "steps": {
      "type": "array",
      "items": { "$ref": "#/$defs/step" }
    },
    "outputs": { "type": "object" }
  },
  "$defs": {
    "step": {
      "type": "object",
      "required": ["id", "type"],
      "properties": {
        "id": { "type": "string" },
        "type": { "enum": ["llm", "tool", "transform", "validate"] },
        "when": { "type": "string" },
        "prompt_id": { "type": "string" },
        "prompt_variant": { "type": "string" },
        "model": { "$ref": "#/$defs/model" },
        "params": { "type": "object" },
        "expects": { "type": "object" },
        "repair": { "$ref": "#/$defs/repair" },
        "function": { "type": "string" },
        "input": { "type": "object" },
        "tool": { "type": "string" }
      }
    },
    "model": {
      "type": "object",
      "required": ["provider", "name"],
      "properties": {
        "provider": { "enum": ["openai", "anthropic", "openrouter"] },
        "name": { "type": "string" },
        "temperature": { "type": "number" }
      }
    },
    "repair": {
      "type": "object",
      "properties": {
        "enabled": { "type": "boolean" },
        "max_attempts": { "type": "integer", "minimum": 0 },
        "model": { "$ref": "#/$defs/model" }
      }
    }
  }
}
```

## Prompt Manifest Schema (skeleton)
```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "type": "object",
  "required": ["id", "variants"],
  "properties": {
    "id": { "type": "string" },
    "label": { "type": "string" },
    "owner": { "type": "string" },
    "variants": {
      "type": "array",
      "items": { "$ref": "#/$defs/variant" }
    },
    "shared_rules": {
      "type": "array",
      "items": { "$ref": "#/$defs/shared_rule" }
    }
  },
  "$defs": {
    "variant": {
      "type": "object",
      "required": ["id"],
      "properties": {
        "id": { "type": "string" },
        "label": { "type": "string" },
        "inline": { "type": "string" },
        "path": { "type": "string" }
      },
      "oneOf": [
        { "required": ["inline"] },
        { "required": ["path"] }
      ]
    },
    "shared_rule": {
      "type": "object",
      "required": ["id", "inline"],
      "properties": {
        "id": { "type": "string" },
        "inline": { "type": "string" }
      }
    }
  }
}
```

## Trace Record Example (debug mode)
```json
{
  "trace_id": "b3b88f2d-6a2c-4e7b-8c2a-0df4f980b0a4",
  "pipeline_id": "routine_ingest",
  "pipeline_version": "0.1.0",
  "pipeline_hash": "sha256:1f2a...",
  "git_commit": "abc123",
  "prompt_id": "routine_structurer",
  "prompt_variant": "A",
  "prompt_hash": "sha256:6b7d...",
  "prompt_text": "You are a routine structurer...",
  "params": {
    "user_text": "Buy groceries tomorrow evening",
    "timezone": "America/Los_Angeles",
    "tool_list": "- list_routines: ..."
  },
  "model": {
    "provider": "openai",
    "name": "gpt-4o-mini",
    "temperature": 0.2
  },
  "steps": [
    {
      "id": "build_prompt",
      "type": "llm",
      "input": { "user_text": "Buy groceries tomorrow evening" },
      "output": { "type": "direct", "direct": { "routine": {"name": "Buy groceries" } } },
      "usage": { "prompt_tokens": 312, "completion_tokens": 176 },
      "repair": { "attempted": true, "count": 1 },
      "timing_ms": 2140
    }
  ],
  "redactions": ["input.api_key", "params.user_email"],
  "final_output": { "routine": { "name": "Buy groceries" } },
  "created_at": "2026-01-22T19:12:43Z"
}
```

## Repository Layout Proposal (pipelines/prompts/traces)
```
pipelines/
  routine_ingest.yaml
  weekly_planner.yaml
prompts/
  routine_structurer/
    prompt.yaml
  plan_generator/
    prompt.yaml
traces/
  2026-01-22/
    b3b88f2d-6a2c-4e7b-8c2a-0df4f980b0a4.json
```
Notes:
- `pipelines/` and `prompts/` are git-tracked.
- `traces/` can be local-only (gitignored later) but the schema should be stable.

## Minimal REST API Spec (runtime)
### Pipelines
- `POST /pipelines` register or update a pipeline
  - Body: `{ pipeline_yaml: string }`
  - Response: `{ id, version, warnings }`
- `GET /pipelines` list pipelines
  - Response: `[ { id, version, updated_at } ]`
- `GET /pipelines/{id}` fetch pipeline
  - Response: `{ id, version, pipeline_yaml }`

### Prompts (optional if runtime manages prompt files)
- `GET /prompts` list prompt manifests
  - Response: `[ { id, variants: [A,B], updated_at } ]`
- `GET /prompts/{id}` fetch prompt manifest
  - Response: `{ id, prompt_yaml }`
- `POST /prompts/{id}` update prompt manifest
  - Body: `{ prompt_yaml: string }`
  - Response: `{ id, warnings }`

### Execute
- `POST /pipelines/{id}/run`
  - Body: `{ input: object, prompt_overrides?: object, debug?: boolean }`
  - Response: `{ output: object, trace_id?: string }`

### Traces
- `GET /traces/{trace_id}` fetch trace record
  - Response: full trace JSON (see example)
- `GET /traces?pipeline_id=...&limit=...`
  - Response: `[ { trace_id, pipeline_id, created_at, status } ]`

### MCP bridge (internal)
- MCP `tools/list` exposes pipelines as tools
- MCP `tools/call` executes `POST /pipelines/{id}/run`

## Design Updates Needed in Branchline (proposed)
- **Pipeline core**: add a YAML pipeline loader + validator module.
- **Prompt registry**: load prompt manifests/variants, resolve inline/path variants, compute prompt hash.
- **Execution engine**: step dispatcher with schema validation + repair loop; pluggable providers.
- **Trace store**: write full prompts/outputs when debug enabled; include prompt hash + params.
- **Metrics**: track repair counts/rates per model + prompt.
- **REST runtime**: endpoints for publish/run/trace; JSON schema validation on inputs.
- **MCP bridge**: expose pipelines as tools; `tools/list` returns manifest; `tools/call` runs pipeline.
- **DSL tooling**: allow Branchline programs to be generated by LLM, executed as transforms.

## Prompt Studio (UI module) design with verification focus
Primary flows:
- **Load**: open pipeline YAML, render DAG, show prompt variants.
- **Edit**: edit YAML inline; parse + validate; display errors in context.
- **Run**: execute with test input; show raw prompt, resolved params, raw LLM output.
- **Compare**: select variants (A/B), run side-by-side, compare outputs + schema validity.
- **Publish**: send pipeline + prompt manifests to REST runtime.

Verification steps (UI + backend):
- **Pre-run validation**: YAML parse + schema validation; highlight missing prompt IDs/variants.
- **Prompt resolution check**: render resolved prompt with placeholders highlighted; fail if any unresolved variables.
- **Schema check**: validate LLM output against expected schema; show diff + repair attempts.
- **Repair telemetry**: display repair count and rate per prompt/model.
- **Trace review**: link to trace record; show prompt hash + params used.
- **Publish gate**: block publish on schema errors or missing prompts; warn on repair usage, missing tests, or non-fatal issues.

## Browser Interpreter Considerations (online usage)
Constraints:
- **No direct LLM calls** in browser (secrets). Browser must call runtime via REST/MCP.
- **Client-side safety**: avoid storing secrets; use short-lived tokens if needed.

Benefits:
- **Instant feedback**: parse/validate YAML, resolve templates, and render DAG locally.
- **Prompt preview**: show fully resolved prompt + shared rules before execution.
- **Schema-aware diffs**: highlight missing/extra fields without server roundtrips.
- **Simulation**: allow mocked LLM outputs or replay traces for debugging.

Risks:
- **Runtime divergence**: browser parser/template must match JVM behavior.
- **Data leakage**: prompt/output previews can expose sensitive data in logs or storage.
- **Overconfidence**: mock outputs may hide real model issues; require real runs before publish.

Mitigations:
- Share a **single template/parser spec** between browser and JVM (or conformance tests).
- Add **redaction hooks** for previews.
- Require publish gate to run at least one **real runtime execution**.

## Implementation Tasks (Agent Checklist)
1) **Define pipeline schema**
   - YAML-first schema with JSON compatibility.
   - Add schema validation + type mapping to Branchline types.
   - Support prompt IDs, prompt variants, and prompt revision hashes.

2) **Runtime step engine (JVM)**
   - Build step dispatcher (`llm`, `tool`, `transform`).
   - Implement schema validation and repair loop.
   - Record repair metrics per prompt/model (repair count, repair rate).
   - Add tracing (inputs/outputs, timings, model usage, prompt hash).

3) **MCP integration**
   - Implement `branchline-mcp` module skeleton and server lifecycle.
   - Implement external MCP tool calling.

4) **REST service**
   - Endpoints: register pipeline, run pipeline, list versions, fetch trace logs.
   - Add minimal auth or assume local-only for MVP.

5) **UI: Prompt Studio**
   - Pipeline visualization + prompt editing panel.
   - “Run test” flow showing full prompt and parsed output.
   - “Publish” flow pushes pipeline to runtime.
   - Prompt variant selector + basic comparison view.

6) **Docs + examples**
   - One demo pipeline mirroring the app’s ingest (direct/plan).
   - Usage docs in `docs/` and a sample in `playground/examples/`.
   - Example of **LLM-generated Branchline DSL** to answer tool-like questions.

## Acceptance Criteria
- A pipeline that mirrors the app’s ingest can be described in Branchline and executed on JVM.
- LLM step returns structured JSON, validated against schema; schema mismatch triggers repair attempts.
- MCP tool metadata can be listed and injected into prompts.
- REST API can run the pipeline and return trace data.
- UI can edit prompts, run a test input, and show schema validation errors.
- Prompt variants can be created and compared; trace logs show which prompt revision was used.

## Open Questions / Decisions Needed
- Optional future enhancements only (none required for MVP).

## Critical Decisions (resolved)
- **Template semantics**: default string injection; JSON insertion via `{{{var}}}` or `{{var|json}}`.
- **`when` language**: minimal boolean now (==, !=, &&, ||, exists); extendable later.
- **Prompt composition**: shared rules allowed; rendered as `<sharedRule name=\"...\">...</sharedRule>` in final text.
- **Repair policy**: dedicated repair model allowed; retry count; per-step opt-out.
- **Tool permissions**: optional pipeline allowlist with warnings; per-agent allowlist optional.
- **Publishing rules**: block on schema errors/missing prompts; warn on softer issues.
- **Trace content**: raw by default in debug; configurable redaction paths.
- **Versioning signal**: store pipeline hash + git commit hash when available.
- **Storage**: file-only for pipelines and prompts.
- **Prompt evaluation**: manual for now.
- **Schema system**: JSON Schema for validation.

## Solidifying Constraints (add to scope)
- **Formal pipeline JSON Schema**: publish and enforce it in runtime + UI validation.
- **Error schema**: standardize errors as `{code, message, step_id, details, recoverable}`.
- **Template resolution contract**: default lenient warnings; allow per-step `strict` flag.
- **Prompt hash + pipeline hash**: include both in traces; use them for comparison and debugging.
- **Tool allowlist at pipeline level**: optional but supported, with UI warnings if a step uses a non-allowed tool.
- **Repair budget**: cap total repair attempts per run; record budget usage.
- **Redaction hooks**: allow path-based masking in trace outputs (e.g., `input.api_key`).
- **Determinism metadata**: store provider, model name, parameters, tool versions, and prompt hash in every trace.

## Risks / Challenges (pushback)
- **Visualization vs. DSL drift**: if UI edits prompts directly, how do we keep the DSL in sync without lossy transforms?
- **Repair loops** can hide prompt quality problems; expose repair stats and allow opt-out.
- **MCP + REST double path**: avoid duplicate effort or inconsistent behavior across integration surfaces.
- **Prompt sprawl**: without versioning and test runs, editing prompts in UI can lead to regressions.
- **Prompt variants** can fragment evaluation; need clear comparison and baseline selection.
