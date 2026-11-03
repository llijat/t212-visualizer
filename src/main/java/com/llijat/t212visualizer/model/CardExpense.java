package com.llijat.t212visualizer.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "card_expenses")
public class CardExpense {
    @Id
    private String reference;
    private String action;
    private Instant dateTime;
    @Column(precision = 19, scale = 4)
    private BigDecimal amount;
    private String currency;
    private String merchantName;
    private String merchantCategory;

    protected CardExpense() {
        //required by JPA
    }

    public CardExpense(String reference, String action, Instant dateTime, BigDecimal amount, String currency, String merchantName, String merchantCategory) {
        this.reference = reference;
        this.action = action;
        this.dateTime = dateTime;
        this.amount = amount;
        this.currency = currency;
        this.merchantName = merchantName;
        this.merchantCategory = merchantCategory;
    }

    public String getReference() {return  reference; }
    public String getAction() {return action;}
    public Instant getDateTime() {return  dateTime;}
    public BigDecimal getAmount() {return amount;}
    public String getCurrency() {return currency;}
    public String getMerchantName() {return merchantName;}
    public String getMerchantCategory() {return merchantCategory;}

}
