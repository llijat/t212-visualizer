package com.llijat.t212visualizer.dto;

import com.llijat.t212visualizer.model.CardCashback;
import com.llijat.t212visualizer.model.CardExpense;
import java.util.List;
import java.util.Map;

public record CardExpenseCsvResult (
        List<CardExpense> expenses,
        List<CardCashback> cashback,
        Map<String, Long> ignoredByAction)
{}


