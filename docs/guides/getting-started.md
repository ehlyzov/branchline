---
title: Getting Started
---

# Getting Started

This short guide walks through writing and running a minimal Branchline transform.

## Write your first transform

Create a file `hello.bl` with the following contents:

```branchline
SOURCE row;
TRANSFORM Hello { LET greet = "Hello, " + row.name;
    OUTPUT { greeting: greet };
}
```

The transform reads a `row` record, builds a new field, and emits it with `OUTPUT`.

## Run the transform

You can execute Branchline from Kotlin using the test utility `compileAndRun`:

```kotlin
val result = compileAndRun(
    body = """
        LET greet = \"Hello, \" + row.name;
        OUTPUT { greeting: greet };
    """.trimIndent(),
    row = mapOf("name" to "Branchline")
)
println(result) // {greeting=Hello, Branchline}
```

The helper compiles the snippet and runs it against the provided input map.

## Try it in the playground
Open the playground with the starter example preloaded: [customer-profile](../playground.md?example=customer-profile){ target="_blank" }.

Expected output for the starter example:
```json
{
  "id": 42,
  "full_name": "Ada Lovelace",
  "loyalty_tier": "standard",
  "shipping_city": "London"
}
```

Change the input JSON and rerun to see the output update. Enable tracing to view `EXPLAIN` provenance for variables you care about.

## Next steps
- Edit the program to add a field (e.g., loyalty tier label) and confirm the output changes.
- Switch the input format to XML in the playground and try the `junit-badge-summary` example to see XML parsing.
- Read the [Language Overview](../language/index.md) for syntax details and explore the stdlib pages linked to interactive examples.
