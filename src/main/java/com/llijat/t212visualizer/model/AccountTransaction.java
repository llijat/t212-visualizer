package com.llijat.t212visualizer.model;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "account_transactions")
public class AccountTransaction {
    @Id
    private String reference;
    private String type;
    @Column(precision = 19, scale = 4)
    private BigDecimal amount;
    private String currency;
    private Instant dateTime;

    protected AccountTransaction() {
        //required by JPA
    }

    public AccountTransaction(String reference, String type, BigDecimal amount, String currency, Instant dateTime) {
        this.reference = reference;
        this.type = type;
        this.amount = amount;
        this.currency = currency;
        this.dateTime = dateTime;
    }

    public String getReference() {return reference;}
    public String getType() {return type;}
    public BigDecimal getAmount() {return amount;}
    public String getCurrency() {return currency;}
    public Instant getDateTime() {return dateTime;}
}
