package com.sciencebot.pos.businesses;

import java.math.BigDecimal;

public record PaymentMethodTotal(String paymentMethod, long salesCount, BigDecimal amount) {}
