package com.llijat.t212visualizer.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "positions")
public class Position {

    @Id
    private String ticker; // T212's instrument.ticker (e.g. "AAPL_US_EQ") — unique, fine as PK

    private String name;
    private double quantity;
    private double averagePricePaid;
    private double currentPrice;
    private double currentValue;
    private double unrealizedProfitLoss;
    private Double fxImpact; // null when instrument currency == account currency
    private Instant lastUpdated;
    private double totalCost;
    private String currencyType;
    private String accountCurrency;

    protected Position() {
        // required by JPA
    }

    public Position(String ticker, String name, double quantity, double averagePricePaid,
                     double currentPrice, double currentValue, double unrealizedProfitLoss,
                     Double fxImpact, double totalCost, String currencyType, String accountCurrency) {
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
    public double getQuantity() { return quantity; }
    public double getAveragePricePaid() { return averagePricePaid; }
    public double getCurrentPrice() { return currentPrice; }
    public double getCurrentValue() { return currentValue; }
    public double getUnrealizedProfitLoss() { return unrealizedProfitLoss; }
    public Double getFxImpact() { return fxImpact; }
    public Instant getLastUpdated() { return lastUpdated; }
    public double getTotalCost() {return totalCost; }
    public String getCurrencyType() {return currencyType; }
    public String getAccountCurrency() {return accountCurrency; }

    public void updateFrom(String name, double quantity, double averagePricePaid, double currentPrice,
                            double currentValue, double unrealizedProfitLoss, Double fxImpact, double totalCost,
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

    public double getUnrealizedPlPercent() {
        if (totalCost == 0) return 0;
        return (unrealizedProfitLoss/totalCost)*100.0;
    }

    public String getSymbol() {
        int t = ticker.indexOf("_");
        if (t != -1) {
            return ticker.substring(0, t);  
        } 
        else return ticker;
    }
}
