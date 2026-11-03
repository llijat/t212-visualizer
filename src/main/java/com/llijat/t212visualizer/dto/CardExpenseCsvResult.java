package com.llijat.t212visualizer.dto;

import com.llijat.t212visualizer.model.CardExpense;
import java.util.List;
import java.util.Map;

public record CardExpenseCsvResult (
        List<CardExpense> expenses,
        Map<String, Long> ignoredByAction)
{}


