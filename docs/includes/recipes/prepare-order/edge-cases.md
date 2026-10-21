<!-- Generated from playground/examples/prepare-order.json; run npm run sync:recipes in playground/. -->

- **Empty items:** produce `{"order_id":"A-104","city":"London","skus":[]}`.
- **Missing city:** produce `{"order_id":"A-104","city":"Not provided","skus":["BOOK"]}`.
- **Null city:** produce `{"order_id":"A-104","city":"Not provided","skus":["BOOK"]}`.
