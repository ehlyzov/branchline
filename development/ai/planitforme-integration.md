---
status: Proposed
depends_on: ['ai/llm-pipelines']
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-02-01
changelog:
  - date: 2026-02-01
    change: "Migrated from research/planner.md and added YAML front matter."
  - date: 2026-02-01
    change: "Updated dependency link to ai/llm-pipelines."
---
# Planitforme + Branchline Integration Experiment


## Status (as of 2026-01-31)
- Stage: Draft.
- Pipeline sketches exist; depends on the pipeline runtime described in `development/ai/llm-pipelines.md`.
- Next: align schemas with the runtime spec and define a concrete migration plan.

## Goal
Describe how planitforme changes when LLM logic is migrated to Branchline pipelines. Provide pipeline sketches for ingest, plan, and chat, explain how users interact with pipelines, and outline a system sketch + cost/benefit analysis.

## Current Baseline (planitforme)
- FastAPI backend owns prompts (`backend/prompts.py`) and LLM calls (`backend/llm_client.py`).
- Ingest uses a direct/plan response contract and optional tool execution flow.
- Planning uses a single prompt to generate the day plan.
- Chat is standard LLM interaction via backend endpoints.
- MCP server proxies backend endpoints as tools for external agents.

## Target State (after Branchline integration)
- Prompts and pipelines live in Branchline YAML files.
- Planitforme backend calls Branchline runtime (`/pipelines/{id}/run`) instead of calling LLM providers directly.
- Prompt Studio (separate app) edits pipeline YAML and prompt variants; publishes to Branchline runtime.
- MCP wraps Branchline pipelines as tools for external agents.

## Pipeline Sketches
### 1) Ingest Pipeline
Purpose: convert user text into structured routines or clarification questions.

Pipeline sketch:
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
```

### 2) Plan Pipeline
Purpose: generate a daily/weekly plan from routines + constraints.

Pipeline sketch:
```yaml
id: plan_generate
label: Generate plan
version: 0.1.0
inputs:
  schema:
    type: object
    properties:
      target_date: { type: string }
      user_id: { type: integer }
    required: [target_date]
steps:
  - id: load_routines
    type: tool
    tool: list_routines
    expects:
      schema:
        type: array
        items: { type: object }

  - id: plan_prompt
    type: llm
    prompt_id: plan_generator
    prompt_variant: A
    model:
      provider: openai
      name: gpt-4o-mini
      temperature: 0.2
    params:
      target_date: "{{input.target_date}}"
      routines: "{{{steps.load_routines.output}}}"
      user_profile: "{{{context.user}}}"
    expects:
      schema:
        type: object
        properties:
          plan: { type: object }
          plan_markdown: { type: string }
        required: [plan]
    repair:
      enabled: true
      max_attempts: 1
```

### 3) Chat Pipeline
Purpose: provide conversational answers; optional tool usage.

Pipeline sketch:
```yaml
id: chat_response
label: Chat response
version: 0.1.0
inputs:
  schema:
    type: object
    properties:
      messages: { type: array, items: { type: object } }
      user_id: { type: integer }
    required: [messages]
steps:
  - id: chat_llm
    type: llm
    prompt_id: chat_system
    prompt_variant: A
    model:
      provider: openai
      name: gpt-4o-mini
      temperature: 0.5
    params:
      messages: "{{{input.messages}}}"
      tool_list: "{{tools.list}}"
    expects:
      schema:
        type: object
        properties:
          role: { type: string }
          content: { type: string }
          tool_calls: { type: array }
        required: [role, content]
```

## How users interact with pipelines
- **Prompt Studio**
  - Open a pipeline YAML file.
  - View DAG and all prompts/variants.
  - Edit prompt text or variants, save YAML.
  - Run test input, inspect resolved prompt + raw output.
  - Compare variants (A/B) manually.
  - Publish to runtime (REST).

- **Planitforme app**
  - Backend routes (ingest/plan/chat) call Branchline runtime.
  - UI behavior remains unchanged; responses come from Branchline pipelines.
  - Traces are accessible for debugging and prompt iteration.

## Browser-based interpreter notes (online usage)
Planitforme and Prompt Studio must assume the interpreter runs in a browser:
- **No direct LLM calls** from the browser. All execution goes through Branchline runtime.
- **Local validation** only: YAML parse, template resolution, schema checks, graph rendering.
- **Preview safety**: redact sensitive values in prompt previews.
- **Publish rule**: require at least one real runtime execution before publishing changes.

## System Sketch (after migration)
```
[Prompt Studio UI]
        |
        v
[Branchline Runtime + Pipeline Store] <----> [Traces Store]
        |
        +--> /pipelines/{id}/run (REST)
        |
        +--> MCP server (tools/list, tools/call)
        |
        v
[LLM Providers: OpenAI/Anthropic/OpenRouter]
        ^
        |
[Planitforme Backend]
  - ingest/plan/chat => call Branchline runtime
  - existing DB + domain logic remains
```

## Work Needed in Planitforme
1) **Replace direct LLM calls**
   - `backend/llm_client.py` becomes optional; calls route to Branchline runtime.
   - `backend/prompts.py` moves to prompt YAMLs.
2) **Add Branchline client**
   - Simple REST client for `/pipelines/{id}/run`.
   - Pass user context, feature flags, debug.
3) **Update debug/tracing**
   - Store or link Branchline trace IDs in existing debug logs.
4) **Align tool registry**
   - MCP tool list is exposed by Branchline; remove duplicate tool list in planitforme prompts.
5) **Pipeline ownership**
   - Owners now manage pipelines in Prompt Studio, not in code.

## Cost Analysis (engineering + ops)
### Engineering costs
- **Refactor prompts to YAML**: moderate (migrate prompts + tests).
- **Refactor routes**: replace direct LLM calls with pipeline runs (ingest/plan/chat).
- **New runtime dependency**: planitforme now depends on Branchline runtime availability.
- **Schema work**: align current response shapes with pipeline JSON schemas.

### Operational costs
- **Extra service**: Branchline runtime service must run alongside planitforme.
- **Trace storage**: more data volume when debug is enabled.
- **Monitoring**: must track runtime latency + LLM costs per pipeline.

### Risk / complexity
- Increased latency if runtime adds overhead.
- Debugging now spans two services (planitforme + Branchline runtime).
- Prompt edits are decoupled from code deploys (good for iteration, risk for drift).

## Benefits of Migration
- **Prompts become first-class**: versioned, editable, testable without code changes.
- **Faster iteration**: Prompt Studio enables rapid experiments and A/B comparison.
- **Consistent tracing**: unified trace format across ingest/plan/chat.
- **Safer operations**: schema validation + repair loops reduce runtime errors.
- **Reusable pipelines**: same pipelines can power multiple apps/agents.
- **MCP integration**: external agents can call planitforme capabilities via Branchline tools.

## What “success” looks like
- Ingest/plan/chat work via pipelines with equivalent or better output quality.
- Developers can debug a run by opening a trace and seeing prompt + params + output.
- Prompt changes can be tested and published without redeploying planitforme.

## A/B Testing for Prompts (manual evaluation)
### Goal
Compare prompt variants (A/B/…) for quality and reliability before promoting a new default.

### How it works (workflow)
1) Create variants in `prompts/<prompt_id>/prompt.yaml` (A = baseline, B = candidate).
2) In Prompt Studio, choose **Compare** mode and run the same input set against both variants.
3) Review outputs side-by-side (schema validity, clarity, missing fields, repair usage).
4) Record decision manually (keep A, promote B, or iterate).
5) Update `prompt.yaml` to set A/B labels and publish.

### UI expectations
- Side-by-side diff of **resolved prompts** and **raw outputs**.
- Highlight schema violations and repair attempts.
- Show trace metadata: prompt hash, model, token usage.
- Provide a manual “score” or “notes” field for evaluators.

### Example: A/B compare request payload
```
POST /pipelines/routine_ingest/run
{
  "input": { "user_text": "Buy groceries tomorrow evening" },
  "prompt_overrides": { "routine_structurer": "A" },
  "debug": true
}
```
Repeat with `prompt_overrides` set to `B`, then compare outputs and traces.

## Current Prompts (planitforme source)
Below are the active prompt templates in planitforme today (from `backend/prompts.py` and `backend/main.py`). Dynamic pieces are shown as placeholders.

### Routine structurer (ingest)
**System prompt (assembled):**
```
You structure personal routines for a planning app. Always return JSON (no markdown, no code fences, no prose) that matches the provided schema. Top-level schema: {type: "direct"|"plan", pipeline: [string], direct: {routine: {...}, routines: [same shape], questions: [{field, question}], step_explanations: string|[string]}, plan: {goal: string, steps: [{id, action, tool_name, arguments, expected_schema}], final_output_schema: object}}. For direct, return routine(s) in direct.routine/direct.routines. For plan, steps must be sequential with no loops/branches. expected_schema is required only for steps where action=llm. Example direct JSON: {"type":"direct","pipeline":["Parse routines","Normalize details","List missing fields"],"direct":{"routine":{"name":"Example","category":null,"recurrence":{"pattern":"once"},"constraints":{"location":null,"people":[],\"notes\":null},"steps":[],"estimated_minutes":null,"deadline":null,"reminder":null},"routines":[],"questions":[{"field":"estimated_minutes","question":"How long?"}],"step_explanations":"Reason"}}. Example plan JSON: {"type":"plan","pipeline":["Identify entities","Fetch profiles","Assemble routine"],"plan":{"goal":"Enrich routine with profiles","steps":[{"id":"people_lookup","action":"tool","tool_name":"get_people_profiles","arguments":{},"expected_schema":null},{"id":"assemble","action":"llm","tool_name":"llm_query","arguments":{"instruction":"Build routine","input":{}},"expected_schema":{"type":"object","properties":{"routine":{"type":"object"}},"required":["routine"]}}],"final_output_schema":{"type":"object","properties":{"routine":{"type":"object"}},"required":["routine"]}}}. {protocol} {tools_block} Return multiple routines in `routines` if the user text clearly contains more than one routine; otherwise use `routine`. Keep steps present even if some fields are unknown; set missing fields to null. Provide a brief step_explanations string describing why you chose these steps/order. If critical fields are missing, ask clarifying questions instead of guessing. Extract people and locations mentioned in the routine into constraints.people (list) and constraints.location. For plan steps, only use ingestion-safe tools: list_routines, get_routine, get_people_profiles, get_location_profiles, upsert_people_profiles, upsert_location_profiles. Do not save routines via tools. If a location is a named venue and you have access to external info, add a short factual description in constraints.notes; otherwise leave notes null unless the user supplied notes. If the user writes in another language, translate fields to English in the JSON, but keep any clarifying questions in English as well. {category_guidance} {context_guidance} {quick_add_guidance}
```

**Protocol block:**
```
Protocol: return JSON only with keys {type, pipeline, direct, plan}. type is direct or plan. pipeline is a short list (3-6) of declarative steps with no reasoning or chain-of-thought. If quick-add is true, always choose type=direct and return no questions. If type=plan, do not execute tools; only describe steps.
```

**User prompt (assembled):**
```
Convert this routine description into JSON with fields: name, category, description, recurrence, constraints, steps, estimated_minutes, deadline, reminder. Use recurrence pattern among: once, daily, weekly, monthly, custom_dates, custom_rule. For monthly ordinal rules (e.g., third Friday), set ordinal_week and weekday; for irregular dates, set pattern=custom_dates and provide dates as a list. Include anchor_date if provided, and optional end_date or occurrences if a stop is mentioned. Set deadline/reminder when the user provides them. Choose type=direct when input is self-contained or only needs light normalization; choose type=plan when tool context or complex multi-routine normalization is required. {question_instruction} Use constraints to capture time windows, location, people, and notes. Steps should include title, duration_min (if given), sort_order.

User text:
{user_text}

Clarifications provided (field -> answer):
{clarifications}
```

### Location resolver
**System:**
```
You resolve a place name or address to a timezone for a planning app. Return JSON only (no markdown, no code fences, no prose) with shape: {timezone: string or null, location_description: string or null}. Use an IANA timezone like 'Europe/Paris'. If you are unsure, return timezone null. If you have access to external data, you may use it to verify accuracy. Keep location_description short and factual.
```
**User:**
```
Location: {location}
```

### Plan generator
**System:**
```
You are a planning assistant. Create a feasible schedule without overlaps, respecting user time windows and routine recurrences. Return raw JSON only (no markdown, no code fences, no prose) with shape: {plan: {target_date, slots: [{start, end, title, routine_id, routine_name, status, notes}]}, plan_markdown: string}. Example JSON: {"plan":{"target_date":"2025-01-01","slots":[]},"plan_markdown":"Schedule..."}. Use ISO 8601 timestamps (with timezone offset or Z) for start/end. Keep titles concise. Use user_profile context like location or user description to make realistic choices. Handle recurrence fields explicitly: custom_dates only occur on listed dates; monthly rules may use day_of_month or ordinal_week+weekday; anchor_date marks the first valid date; end_date/occurrences stop scheduling beyond their bounds. Mention deadlines when relevant.
```
**User:**
```
Generate a plan with time-ordered slots for the given date. Include prep steps if provided. If time is insufficient, call it out in plan_markdown. Input data follows:
{plan_input}
```

### Chat (system + context)
**System:**
```
You are a planning assistant. Use the user's saved routines context to answer. Ask clarifying questions if details are missing. Keep replies concise.
```
**Context message:**
```
Saved routines context: {routine_summaries}
```

### Plan step execution (ingest plan)
**System:**
```
You are executing a plan step. Return JSON only that matches this schema: {expected_schema}
```
**User:**
```
Step ID: {step_id}
Instruction: {instruction}
Input: {input_payload}
Context: {context_payload}
```

### Repair prompt (schema mismatch)
**System:**
```
Repair the JSON to match the provided schema. Return JSON only (no markdown, no prose).
```
**User:**
```
Instruction: {instruction}
Schema: {schema}
Invalid JSON: {invalid_output}
```

### Weekly story
**System:**
```
You write epic weekly stories for a planning app using the Hero's Journey arc. Cast the user as the hero and weave completed routines as victories, cancellations as setbacks, and reschedules as course corrections or trials. Include a call to adventure, a sequence of trials, a climactic turn, and a return home. Keep it under 8 sentences, cinematic but clear. Avoid bullet points.
```
**User:**
```
Weekly activity data:
{story_input}
```
