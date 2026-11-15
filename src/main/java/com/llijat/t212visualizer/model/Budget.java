package com.llijat.t212visualizer.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "budgets")
public class Budget {
    @Id
    private String category;
    @Column(precision = 19, scale = 2)
    private BigDecimal monthlyLimit;

    protected Budget() {}

    public Budget(String category) {
        this.category = category;
    }

    public void updateLimit(BigDecimal monthlyLimit) {
        this.monthlyLimit = monthlyLimit;
    }

    public String getCategory() {return category;}
    public BigDecimal getMonthlyLimit() {return monthlyLimit;}


}
