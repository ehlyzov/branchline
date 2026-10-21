<!-- Generated from playground/examples/recipe-incomplete-data.json; run npm run sync:recipes in playground/. -->

```branchline
TRANSFORM ContactReadiness {
    ASSERT(input.id != null, "Customer id is required")
    LET contact = input.contact ?? {}
    LET email = contact.email ?? null
    LET phone = contact.phone ?? null
    LET tags = input.tags ?? []
    OUTPUT {
        customer_id: input.id,
        email: email,
        phone: phone,
        tags: tags,
        needs_contact: email == null && phone == null
    }
}
```
