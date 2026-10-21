<!-- Generated from playground/examples/recipe-customer.json; run npm run sync:recipes in playground/. -->

- **Missing optional contacts stay null:** produce `{"customer_id":"C-18","name":"Grace Hopper","email":null,"city":null}`.
- **Blank email becomes null:** produce `{"customer_id":"C-19","name":"Lin","email":null,"city":null}`.
- **Missing required name is rejected:** reject with `Customer name is required`.
- **Blank required name is rejected:** reject with `Customer name must not be blank`.
