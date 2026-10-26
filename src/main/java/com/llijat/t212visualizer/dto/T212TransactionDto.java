package com.llijat.t212visualizer.dto;

public record T212TransactionDto (
        double amount,
        String currency,
        String dateTime,
        String reference,
        String type
) {}
