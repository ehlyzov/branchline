---
title: Getting Started
---

# Getting Started

Start with three complete JSON transformations. [Install the JVM or Node CLI](install.md)
first, or use the [Playground](../playground.md?example=hello-transform){ target="_blank" }.

## Transform a JSON object

### Program

Save this as `hello.bl`:

<!-- onboarding:hello:program -->
```branchline
TRANSFORM Hello {
    LET greet = "Hello, " + input.name
    OUTPUT { greeting: greet }
}
```

### Input example

Save this as `sample.json`:

<!-- onboarding:hello:input -->
```json
{"name":"Ada"}
```

### Expected output

<!-- onboarding:hello:output -->
```json
{"greeting":"Hello, Ada"}
```

### Why this works

`input` holds the input object. `greet` combines a literal prefix with its `name`
field, and `OUTPUT` constructs a new object with a `greeting` field. This example
expects `name` to be a string.

### Validation commands

For the JVM, replace `<tag>` with your downloaded release version:

```bash
java -jar branchline-cli-<tag>-all.jar hello.bl --input sample.json
```

For Node, run from the extracted CLI package directory, with your two files there:

```bash
./bin/bl.cjs hello.bl --input sample.json
```

In the Playground, paste the program and input into their editors, run, and compare
the result with the expected output. JSON whitespace and object key order may differ.

## Handle missing fields and null

### Program

Save this as `defaults.bl`:

<!-- onboarding:defaults:program -->
```branchline
TRANSFORM Defaults {
    OUTPUT {
        name: input.name ?? "Guest",
        nickname: input.nickname,
        active: input.active ?? true,
        attempts: input.attempts ?? 3
    }
}
```

### Input example

<!-- onboarding:defaults:input -->
```json
{"name":null,"nickname":null,"active":false,"attempts":0}
```

### Expected output

<!-- onboarding:defaults:output -->
```json
{"name":"Guest","nickname":null,"active":false,"attempts":0}
```

### Why this works

`??` replaces a missing field or `null` with the value on its right. It preserves
`false`, `0`, and empty strings. Reading `nickname` without a fallback keeps its
`null`; a missing `nickname` also produces `null`. With `{}` as input, the result
is `{"name":"Guest","nickname":null,"active":true,"attempts":3}`.

### Validation commands

Replace `sample.json` with the input above and run:

```bash
java -jar branchline-cli-<tag>-all.jar defaults.bl --input sample.json
./bin/bl.cjs defaults.bl --input sample.json
```

The same program and input can be pasted into the Playground.

## Filter and project an array

### Program

Save this as `available-items.bl`:

<!-- onboarding:available-items:program -->
```branchline
TRANSFORM AvailableItems {
    LET items = input.items ?? []
    OUTPUT {
        items: [
            { sku: item.sku, total: item.qty * item.price }
            FOR EACH item IN items WHERE item.qty > 0
        ]
    }
}
```

### Input example

<!-- onboarding:available-items:input -->
```json
{"items":[{"sku":"A","qty":0,"price":10},{"sku":"B","qty":2,"price":7},{"sku":"C","qty":1,"price":3}]}
```

### Expected output

<!-- onboarding:available-items:output -->
```json
{"items":[{"sku":"B","total":14},{"sku":"C","total":3}]}
```

### Why this works

`items` defaults to an empty array when the input field is missing or `null`.
The comprehension visits items in order, keeps positive quantities, and constructs
each output row from `sku` and `qty * price`. Here each row has a string `sku` and
small integer `qty` and `price` values. An empty array or no matching rows produces
`{"items":[]}`. See [Numeric Semantics](../language/numeric.md) before using large
integers or decimal amounts.

### Validation commands

Replace `sample.json` with the input above and run:

```bash
java -jar branchline-cli-<tag>-all.jar available-items.bl --input sample.json
./bin/bl.cjs available-items.bl --input sample.json
```

The same program and input can be pasted into the Playground.

These three programs, inputs, and outputs are read directly from this guide by the
conformance tests on JVM and Node. Both interpreter execution and strict VM
execution (with interpreter fallback disabled) are checked, along with missing
fields, explicit nulls, and empty or fully filtered arrays. This is a bounded set
of examples; see the [release readiness scope](release-readiness.md) for wider support.

## Next steps

- Learn the syntax in the [Tour of Branchline](../learn/index.md).
- Use [TRY/CATCH](try-catch.md) to keep pipelines resilient.
- Explore the [Standard Library](../language/std-core.md).
