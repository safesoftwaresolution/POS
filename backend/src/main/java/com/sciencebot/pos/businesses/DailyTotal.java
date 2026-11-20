package com.sciencebot.pos.businesses;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyTotal(LocalDate date, long salesCount, BigDecimal amount) {}
