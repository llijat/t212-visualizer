package com.llijat.t212visualizer.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "account_ticks")
public class AccountTick {
    @Id
    private Instant takenAt;
    @Column(precision = 19, scale = 4)
    private BigDecimal totalValue;
    @Column(precision = 19, scale = 4)
    private BigDecimal netDeposits;

    protected AccountTick() {}

    public AccountTick(Instant takenAt, BigDecimal totalValue, BigDecimal netDeposits) {
        this.takenAt = takenAt;
        this.totalValue = totalValue;
        this.netDeposits = netDeposits;
    }

    public Instant getTakenAt() {return takenAt;}
    public BigDecimal getTotalValue() {return totalValue;}
    public BigDecimal getNetDeposits() {return netDeposits;}
}
