package com.sciencebot.pos.voicesales.internal.ports;

import java.math.BigDecimal;
import java.util.List;

/** Literal mentions only. Identifiers, totals and prices must not cross the AI boundary. */
public record SaleIntent(List<Item> items, String customer, String paymentMethod, BigDecimal cashReceived) {
    public static final int MAX_ITEMS = 30;
    public static final int MAX_QUANTITY = 1000;
    public static final int MAX_MENTION = 200;
    public static final int MAX_PAYMENT = 100;
    public static final BigDecimal MAX_CASH = new BigDecimal("999999999999.99");

    public record Item(String mention, Integer quantity) {}

    /**
     * Limits every provider must honor. The service checks them for any adapter, so a substituted
     * provider cannot widen the catalog fan-out or hand the resolver an impossible amount.
     */
    public boolean withinContract() {
        if (items != null) {
            if (items.size() > MAX_ITEMS) return false;
            for (Item item : items) {
                if (item == null) continue;
                if (item.mention() != null && item.mention().length() > MAX_MENTION) return false;
                if (item.quantity() != null && (item.quantity() < 1 || item.quantity() > MAX_QUANTITY)) return false;
            }
        }
        if (customer != null && customer.length() > MAX_MENTION) return false;
        if (paymentMethod != null && paymentMethod.length() > MAX_PAYMENT) return false;
        return cashReceived == null || (cashReceived.signum() >= 0 && cashReceived.compareTo(MAX_CASH) <= 0
                && cashReceived.stripTrailingZeros().scale() <= 2);
    }
}
