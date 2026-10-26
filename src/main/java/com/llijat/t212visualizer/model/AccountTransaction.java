package com.llijat.t212visualizer.model;


import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jdk.jfr.Enabled;

import java.time.Instant;

@Entity
@Table(name = "account_transactions")
public class AccountTransaction {
    @Id
    private String reference;

    private String type;
    private double amount;
    private String currency;
    private Instant dateTime;

    protected AccountTransaction() {
        //required by JPA
    }

    public AccountTransaction(String reference, String type, double amount, String currency, Instant dateTime) {
        this.reference = reference;
        this.type = type;
        this.amount = amount;
        this.currency = currency;
        this.dateTime = dateTime;
    }

    public String getReference() {return reference;}
    public String getType() {return type;}
    public double getAmount() {return amount;}
    public String getCurrency() {return currency;}
    public Instant getDateTime() {return dateTime;}
}
