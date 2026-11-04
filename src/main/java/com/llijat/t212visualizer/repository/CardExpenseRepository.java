package com.llijat.t212visualizer.repository;

import com.llijat.t212visualizer.model.CardExpense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CardExpenseRepository extends JpaRepository<CardExpense, String> {
    Optional<CardExpense> findTopByOrderByDateTimeDesc();
}

