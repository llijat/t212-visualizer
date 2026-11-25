package com.llijat.t212visualizer.repository;

import com.llijat.t212visualizer.model.Budget;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetRepository extends JpaRepository<Budget, String> {
}
