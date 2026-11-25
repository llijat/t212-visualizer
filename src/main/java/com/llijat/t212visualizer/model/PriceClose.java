package com.llijat.t212visualizer.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "price_history")
public class PriceClose {
    @EmbeddedId
    private PriceDay id;
    @Column(precision = 19, scale = 4)
    private BigDecimal close;

    protected PriceClose() {}

    public PriceClose(PriceDay id, BigDecimal close) {
        this.id = id;
        this.close = close;
    }

    public void changeClose(BigDecimal close) {
        this.close = close;
    }

    public PriceDay getId() {return id;}
    public BigDecimal getClose() {return close;}
}
