<!-- Generated from playground/examples/recipe-sales-summary.json; run npm run sync:recipes in playground/. -->

- **Empty sales have a zero total:** produce `{"currency":"GBP","paid_count":0,"revenue_cents":0,"excluded_count":0}`.
- **Refunded sales are excluded and zero paid sales count:** produce `{"currency":"GBP","paid_count":1,"revenue_cents":0,"excluded_count":1}`.
- **Missing amount is rejected:** reject with `Every sale needs amount_cents`.
- **Negative amount is rejected:** reject with `Amounts must be nonnegative`.
- **Unknown status is rejected:** reject with `Unknown sale status`.
