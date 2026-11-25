package com.llijat.t212visualizer.dto;

import java.math.BigDecimal;

/** A subscription on a day of the calendar: the last real payment, or one expected from its usual day. */
public record CalendarSubscription(
        String merchant,
        BigDecimal amount,
        boolean expected
) {}
