package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.CardExpenseCsvResult;
import com.llijat.t212visualizer.model.CardExpense;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class CardExpenseCsvParser {
    private static final CSVFormat CSV_FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .get();
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssxxx");

    public CardExpenseCsvResult parse(Reader reader) throws IOException {
        List<CardExpense> expenses = new ArrayList<>();
        Map<String, Long> ignored = new HashMap<>();

        try (CSVParser csv = CSV_FORMAT.parse(reader)) {
            for (CSVRecord row : csv) {
                String action = row.get("Action");
                if (!action.equals("Card debit")) {
                    ignored.merge(action, 1L, Long::sum);
                    continue;
                }

                expenses.add(new CardExpense(
                        row.get("ID"),
                        action,
                        OffsetDateTime.parse(row.get("Time (UTC)"), TIME_FORMAT).toInstant(),
                        new BigDecimal(row.get("Total")),
                        row.get("Currency (Total)"),
                        row.get("Merchant name"),
                        row.get("Merchant category")));
            }
        }
        return new CardExpenseCsvResult(expenses, ignored);
    }
}