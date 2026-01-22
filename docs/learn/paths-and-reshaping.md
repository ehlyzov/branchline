---
title: Paths and Reshaping
---

# Paths and Reshaping

Branchline reads from `INPUT` (or `$`) and builds new objects with `OUTPUT`. Use dot paths and array access to grab what you need.

## Goal
Reshape an order payload into a compact shipment record.

## Program
```branchline
TRANSFORM OrderShipment {
    OUTPUT {
        orderId: INPUT.order.id,
        customerId: INPUT.order.customer.id,
        shipTo: INPUT.order.shipping.address,
        items: INPUT.order.items,
    };
}
```

## Try it in the Playground
- [order-shipment](../playground.md?example=order-shipment){ target="_blank" }

## Tips
- Prefer direct paths for clarity.
- Build new objects with explicit keys so downstream systems get stable schemas.

## Next steps
Continue to [Control Flow for Pipelines](control-flow.md).
