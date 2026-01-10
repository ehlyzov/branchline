package io.github.ehlyzov.branchline.vm

public object VMTransformPrograms {
    public val pathExpressions: String = """
        TRANSFORM T {
            LET firstSku = input.orders[0].items[0].sku;
            LET firstQty = input.orders[0].items[0].qty;
            LET secondSku = input.orders[0].items[1].sku;
            OUTPUT {
                customer: input.customer.name,
                firstSku: firstSku,
                firstQty: firstQty,
                secondSku: secondSku,
            }
        }
    """.trimIndent()

    public val arrayComprehension: String = """
        TRANSFORM T {
            LET expensive = [o.id FOR EACH o IN input.orders WHERE o.total > 150];
            LET skus = [i.sku FOR EACH i IN input.orders[0].items];
            OUTPUT {
                expensive: expensive,
                skus: skus,
            }
        }
    """.trimIndent()

    public val typicalTransform: String = """
        TRANSFORM T {
            LET total = 0;
            FOR EACH order IN input.orders {
                SET total = total + order.total;
            }
            OUTPUT {
                orderCount: LENGTH(input.orders),
                total: total,
            }
        }
    """.trimIndent()
}
