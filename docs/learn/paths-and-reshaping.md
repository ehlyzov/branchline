---
title: Paths and Reshaping
---

# Paths and Reshaping

Read the incoming object with `input` and create a new object with `OUTPUT`.
This lesson continues [Getting Started](../guides/getting-started.md).

## Program

Use dot paths to reshape a nested order into a shipment record. Save as `program.bl`:

```branchline
TRANSFORM OrderShipment {
    OUTPUT {
        orderId: input.order.id,
        customerId: input.order.customer.id,
        shipTo: input.order.shipping.address,
        items: input.order.items
    }
}
```

## Input example

Save as `input.json`:

```json
{"order":{"id":"A-104","customer":{"id":"C-12"},"shipping":{"address":{"city":"London"}},"items":[{"sku":"BOOK","qty":2}]}}
```

## Expected output

```json
{"orderId":"A-104","customerId":"C-12","shipTo":{"city":"London"},"items":[{"sku":"BOOK","qty":2}]}
```

## Why this works

Each output field reads a named path under `input.order`. Objects and arrays can
be copied as values: `shipTo` keeps the address object and `items` keeps the array.
This example assumes `order`, `customer`, and `shipping` are objects. A missing
field on an existing object yields `null`; traversing a missing intermediate object
can fail. Use `??` to supply a fallback before accessing a nested optional object.

## Validation commands

Paste this program and input into the [Playground](../playground.md), then run and
compare the result. Or, after [local setup](../guides/install.md), run either CLI:

```bash
java -jar branchline-cli-v1.0.0-rc.1-all.jar program.bl --input input.json
node branchline-cli/bin/bl.cjs program.bl --input input.json
```

Continue to [Control Flow for Pipelines](control-flow.md).
