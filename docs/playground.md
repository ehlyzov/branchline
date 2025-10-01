---
title: Interactive Playground
description: Try Branchline in your browser with the interactive playground.
---

# Interactive Playground

Try Branchline in your browser with the interactive playground!

[**🚀 Launch Interactive Playground →**](playground/demo.html){ .md-button .md-button--primary target="_blank" }

The playground lets you:

* Load curated examples with pre-configured input data
* Edit Branchline programs with syntax highlighting
* Modify input JSON in real-time
* Execute transforms directly in your browser
* View trace explanations to understand program execution

All computation happens client-side using the Kotlin/JS-compiled Branchline interpreter.

## Example Programs

Here are some sample Branchline programs you can try in the playground:

### Basic Transform

```kotlin
LET fullName = msg.first_name + " " + msg.last_name;
LET loyalty = msg.loyalty_tier ?? "standard";

OUTPUT {
    id: msg.id,
    full_name: fullName,
    loyalty_tier: loyalty,
    shipping_city: msg.address.city
}
```

### Array Processing

```kotlin
LET totalAmount = ARRAY_SUM(msg.items, x -> x.price * x.quantity);
LET itemCount = ARRAY_LENGTH(msg.items);

OUTPUT {
    order_id: msg.id,
    total_amount: totalAmount,
    item_count: itemCount,
    items: ARRAY_MAP(msg.items, item -> {
        name: item.name,
        subtotal: item.price * item.quantity
    })
}
```

