<!-- Generated from playground/examples/recipe-incomplete-data.json; run npm run sync:recipes in playground/. -->

- **Missing optional objects and arrays:** produce `{"customer_id":"C-18","email":null,"phone":null,"tags":[],"needs_contact":true}`.
- **Existing phone and tags are preserved:** produce `{"customer_id":"C-19","email":null,"phone":"+44 20 0000 0000","tags":["customer"],"needs_contact":false}`.
- **Missing customer id is rejected:** reject with `Customer id is required`.
