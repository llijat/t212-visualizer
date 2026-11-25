package com.llijat.t212visualizer.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "account_snapshots")
public class AccountSnapshot {
    @Id
    private LocalDate snapshotDate;
    @Column(precision = 19, scale = 4)
    private BigDecimal totalValue;
    @Column(precision = 19, scale = 4)
    private BigDecimal netDeposits;
    private Instant takenAt;

    protected AccountSnapshot() {}

    public AccountSnapshot(LocalDate snapshotDate, BigDecimal totalValue, BigDecimal netDeposits, Instant takenAt) {
        this.snapshotDate = snapshotDate;
        this.totalValue = totalValue;
        this.netDeposits = netDeposits;
        this.takenAt = takenAt;
    }

    public LocalDate getSnapshotDate() {return snapshotDate;}
    public BigDecimal getTotalValue() {return totalValue; }
    public BigDecimal getNetDeposits() {return netDeposits;}
    public Instant getTakenAt() {return takenAt;}
}
