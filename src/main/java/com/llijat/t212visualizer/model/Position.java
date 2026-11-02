package com.llijat.t212visualizer.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Entity
@Table(name = "positions")
public class Position {

    @Id
    private String ticker; // T212's instrument.ticker (e.g. "AAPL_US_EQ") — unique, fine as PK

    private String name;
    @Column(precision = 24, scale = 10)
    private BigDecimal quantity;
    @Column(precision = 24, scale = 10)
    private BigDecimal averagePricePaid;
    @Column(precision = 24, scale = 10)
    private BigDecimal currentPrice;
    @Column(precision = 19, scale = 4)
    private BigDecimal currentValue;
    @Column(precision = 19, scale = 4)
    private BigDecimal unrealizedProfitLoss;
    @Column(precision = 19, scale = 4)
    private BigDecimal fxImpact; // null when instrument currency == account currency
    private Instant lastUpdated;
    @Column(precision = 19, scale = 4)
    private BigDecimal totalCost;
    private String currencyType;
    private String accountCurrency;

    protected Position() {
        // required by JPA
    }

    public Position(String ticker, String name, BigDecimal quantity, BigDecimal averagePricePaid,
                    BigDecimal currentPrice, BigDecimal currentValue, BigDecimal unrealizedProfitLoss,
                    BigDecimal fxImpact, BigDecimal totalCost, String currencyType, String accountCurrency) {
        this.ticker = ticker;
        this.name = name;
        this.quantity = quantity;
        this.averagePricePaid = averagePricePaid;
        this.currentPrice = currentPrice;
        this.currentValue = currentValue;
        this.unrealizedProfitLoss = unrealizedProfitLoss;
        this.fxImpact = fxImpact;
        this.lastUpdated = Instant.now();
        this.totalCost = totalCost;
        this.currencyType = currencyType;
        this.accountCurrency = accountCurrency;
    }

    public String getTicker() { return ticker; }
    public String getName() { return name; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getAveragePricePaid() { return averagePricePaid; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public BigDecimal getCurrentValue() { return currentValue; }
    public BigDecimal getUnrealizedProfitLoss() { return unrealizedProfitLoss; }
    public BigDecimal getFxImpact() { return fxImpact; }
    public Instant getLastUpdated() { return lastUpdated; }
    public BigDecimal getTotalCost() {return totalCost; }
    public String getCurrencyType() {return currencyType; }
    public String getAccountCurrency() {return accountCurrency; }

    public void updateFrom(String name, BigDecimal quantity, BigDecimal averagePricePaid, BigDecimal currentPrice,
                           BigDecimal currentValue, BigDecimal unrealizedProfitLoss, BigDecimal fxImpact, BigDecimal totalCost,
                            String currencyType, String accountCurrency) {
        this.name = name;
        this.quantity = quantity;
        this.averagePricePaid = averagePricePaid;
        this.currentPrice = currentPrice;
        this.currentValue = currentValue;
        this.unrealizedProfitLoss = unrealizedProfitLoss;
        this.fxImpact = fxImpact;
        this.lastUpdated = Instant.now();
        this.totalCost = totalCost;
        this.currencyType = currencyType;
        this.accountCurrency = accountCurrency;
    }

    public BigDecimal getUnrealizedPlPercent() {
        if (totalCost.signum() == 0) return BigDecimal.ZERO;
        return unrealizedProfitLoss.multiply(new BigDecimal("100")).divide(totalCost, 2, RoundingMode.HALF_UP);
    }

    public String getSymbol() {
        int t = ticker.indexOf("_");
        if (t != -1) {
            return ticker.substring(0, t);  
        } 
        else return ticker;
    }
}
