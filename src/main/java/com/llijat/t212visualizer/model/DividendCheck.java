package com.llijat.t212visualizer.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "dividend_checks")
public class DividendCheck {
    @Id
    private String ticker;
    private Instant checkedAt;

    protected DividendCheck() {
        //required by JPA
    }

    public DividendCheck(String ticker, Instant checkedAt) {
        this.ticker = ticker;
        this.checkedAt = checkedAt;
    }

    public String getTicker() {return ticker;}
    public Instant getCheckedAt() {return checkedAt;}
}