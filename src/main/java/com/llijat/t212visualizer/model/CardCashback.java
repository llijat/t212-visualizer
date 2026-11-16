package com.llijat.t212visualizer.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "card_cashback")
public class CardCashback {
    @Id
    private String reference;
    private Instant dateTime;
    private BigDecimal amount;
    private String currency;

    protected CardCashback() {}

    public CardCashback(String reference, Instant dateTime, BigDecimal amount, String currency) {
        this.reference = reference;
        this.dateTime = dateTime;
        this.amount = amount;
        this.currency = currency;
    }

    public String getReference() {return reference;}
    public Instant getDateTime() {return dateTime;}
    public BigDecimal getAmount() {return amount;}
    public String getCurrency() {return currency;}
}
