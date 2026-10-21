<!-- Generated from playground/examples/prepare-order.json; run npm run sync:first-success in playground/. -->

```branchline
TRANSFORM Shipment {
    OUTPUT {
        order_id: input.id,
        city: input.shipping.city ?? "Not provided",
        skus: [item.sku FOR EACH item IN input.items WHERE item.qty > 0]
    }
}
```
