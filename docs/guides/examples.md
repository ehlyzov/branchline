---
title: Example Catalog
---

# Example Catalog

Choose the task you want to complete. The six full recipes include a complete program, input, expected result, a small exercise, and local-run files. The focused examples below open directly in the Playground.

## Start with a full recipe

| Task | Recipe |
| --- | --- |
| Reshape data | [Normalize a customer](../recipes/recipe-customer.md) · [Prepare an order](../recipes/prepare-order.md) |
| Summarize data | [Summarize sales](../recipes/recipe-sales-summary.md) · [Summarize a JSON test report](../recipes/recipe-test-report.md) |
| Validate data | [Handle incomplete data](../recipes/recipe-incomplete-data.md) · [Guard a pipeline](../recipes/recipe-pipeline-guard.md) |

In the [Playground](../playground.md), **Task** filters the catalog and **Example** groups entries by task. Your current draft stays selected when a filter hides its example. AI subset filters are available under **Run settings & details**.

## Reshape data

- [Build a greeting from input](../playground.md?example=hello-transform)
- [Enrich a customer profile](../playground.md?example=customer-profile)
- [Build an order shipment overview](../playground.md?example=order-shipment)
- [Normalize scores and find passing values](../playground.md?example=collection-transforms)
- [Deduplicate, sort and combine arrays](../playground.md?example=stdlib-arrays-overview)
- [Build lists and collect nested entries](../playground.md?example=stdlib-core-append-prepend)
- [Normalize lists and look up defaults](../playground.md?example=stdlib-core-listify-get)
- [Update and remove object or list entries](../playground.md?example=stdlib-core-put-delete)
- [Map, filter and reduce array values](../playground.md?example=stdlib-hof-overview)
- [Convert primitive input values](../playground.md?example=stdlib-strings-casts)

## Summarize data

- [Summarize counts, prices and ranges](../playground.md?example=stdlib-agg-overview)
- [Choose a report status from totals](../playground.md?example=stdlib-case-basic)
- [Summarize a JUnit XML report](../playground.md?example=junit-badge-summary)

## Validate data

- [Handle a failed calculation guard](../playground.md?example=error-handling-try-catch)
- [Reject failing builds before deployment](../playground.md?example=pipeline-health-gating)

## Advanced tasks

These examples focus on individual language and runtime features. Check their prerequisites and the linked reference before adapting them to your data.

### Format text and encode payloads

- [Clean text and extract matching values](../playground.md?example=stdlib-strings-text)
- [Format messages with placeholders](../playground.md?example=stdlib-strings-format)
- [Decode and re-encode a Base64 payload](../playground.md?example=stdlib-strings-base64)

### Read and write XML

- [Read XML attributes and mixed text](../playground.md?example=xml-mapping-mixed-content)
- [Read namespaced XML elements and attributes](../playground.md?example=xml-namespaces-mapping)
- [Control XML element order and namespaces](../playground.md?example=xml-output-ordering)
- [Convert an XML invoice to an XML report](../playground.md?example=xml-input-output-roundtrip)

See [Input policies](../language/index.md#input-policies) for the mapped XML shapes.

### Work with numbers and JSON output

- [Compare division, decimals and rounding](../playground.md?example=stdlib-numeric-precision)
- [Calculate powers and square roots](../playground.md?example=numeric-powers-roots)
- [Inspect large numbers in JSON output](../playground.md?example=json-large-numbers)
- [Serialize JSON with canonical ordering](../playground.md?example=json-canonical-output)

Numeric capabilities differ between the JVM and browser/Node runtimes. Read [Numeric semantics](../language/numeric.md) before relying on large integers or decimal precision.

### Inspect paths and derived values

- [Inspect object keys, values and entries](../playground.md?example=stdlib-core-keys-values)
- [Inspect paths through nested data](../playground.md?example=stdlib-core-walk)
- [Trace how an order total is derived](../playground.md?example=explain-derived-total)
- [Trace and guard a discounted total](../playground.md?example=stdlib-debug-explain)

### Inspect input and output contracts

- [Inspect contracts for a nested report](../playground.md?example=contract-deep-composition)
- [Inspect contracts when building an array](../playground.md?example=contract-empty-array-append)
- [Inspect contracts with dynamic keys](../playground.md?example=contract-input-seeded-wildcard-output)
- [Inspect contracts for literal bracket paths](../playground.md?example=contract-literal-brackets-static)
- [Declare nullable input and output contracts](../playground.md?example=contract-nullable-precision)

### Work with shared state and time

- [Capture the current timestamp](../playground.md?example=stdlib-time-now)
- [Write a timestamp to shared state](../playground.md?example=shared-memory-basics)
- [Read a session from shared state](../playground.md?example=shared-memory-read)

Clock and shared-state examples are outside the AI authoring subset. Shared-state reads require data supplied through the host; see each example’s description.

### Configure transforms and identifiers

- [Use keyword names in a transform](../playground.md?example=keyword-identifiers)
- [Configure transform adapters and shared storage](../playground.md?example=transform-options)

Continue with the [Standard Library](../language/std-core.md) or the [Tour of Branchline](../learn/index.md).
