---
title: Branchline
description: Shape JSON into the data your service needs. Try a complete Branchline transformation in your browser, then run it on JVM or Node.
hide:
  - navigation
  - toc
---

<div class="bl-home" markdown="1">

<div class="bl-home__intro" markdown="1">

# Shape JSON into the data your service needs

Branchline is a language for reshaping structured data. Select fields, handle missing values, and filter collections into a new JSON payload.

<div class="bl-home__actions" markdown="1">

[Try this example](playground.md?example=prepare-order){ .md-button .md-button--primary }
[Get started](guides/getting-started.md){ .md-button }

</div>

No installation needed to try it. Run locally on JVM or Node when you are ready.
{ .bl-home__hint }

</div>

<div class="bl-home__example" markdown="1">

## Prepare an order payload

Keep the order ID and city, then select SKUs with a positive quantity.

<div class="bl-home__flow" markdown="1">

<section class="bl-home__panel" aria-labelledby="home-input" markdown="1">

### Input JSON { #home-input }

--8<-- "includes/prepare-order/input.md"

</section>

<section class="bl-home__panel bl-home__panel--program" aria-labelledby="home-program" markdown="1">

### Branchline { #home-program }

--8<-- "includes/prepare-order/program.md"

</section>

<section class="bl-home__panel" aria-labelledby="home-output" markdown="1">

### Output JSON { #home-output }

--8<-- "includes/prepare-order/expected.md"

</section>

</div>

Open the example, change PEN's `qty` from `0` to `1`, and run again. Both SKUs will appear.
{ .bl-home__exercise }

This example expects an array of items with numeric quantities. The missing-city label is a fallback for demonstration; it does not validate an address.
{ .bl-home__hint }

</div>

## Use it for everyday data work

<div class="bl-home__use-cases" markdown="1">

<section markdown="1">

### Shape API payloads

Rename fields and flatten nested records for the next service in your workflow.

[Explore a customer profile](playground.md?example=customer-profile)

</section>

<section markdown="1">

### Filter and map collections

Select the rows you need and build a new list with explicit transformation rules.

[Explore collection transforms](playground.md?example=collection-transforms)

</section>

<section markdown="1">

### Summarize reports

Turn a structured test report into counts and status fields for a dashboard.

[Explore a test report summary](playground.md?example=junit-badge-summary)

</section>

</div>

<div class="bl-home__limits" markdown="1">

## Before you integrate

**v1.0.0-rc.1 is a release candidate.** The browser playground uses the JS interpreter. Local CLI packages support JVM and Node; check the [platform scope](guides/platform-support.md) for qualified runtime and OS combinations.

Numeric precision depends on the runtime. JS has no exact decimal guarantee; use the [numeric profiles](language/numeric.md) to choose safe bounds for your data and calculations.

[Run this example locally](guides/getting-started.md#run-locally) · [Language reference](language/index.md) · [Standard library](language/stdlib-support.md)

</div>

</div>
