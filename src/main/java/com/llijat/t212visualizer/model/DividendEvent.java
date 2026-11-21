package com.llijat.t212visualizer.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "dividend_events")
public class DividendEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String ticker;
    private LocalDate paymentDate;
    private LocalDate exDividendDate;
    @Column(precision = 19, scale = 6)
    private BigDecimal amountPerShare;
    @Column(precision = 19, scale = 4)
    private BigDecimal receivedEur;

    protected DividendEvent() {
        //required by JPA
    }

    public DividendEvent(String ticker, LocalDate paymentDate) {
        this.ticker = ticker;
        this.paymentDate = paymentDate;
    }

    public void updateAnnouncement(LocalDate exDividendDate, BigDecimal amountPerShare) {
        this.exDividendDate = exDividendDate;
        this.amountPerShare = amountPerShare;
    }

    public Long getId() {return id;}
    public String getTicker() {return ticker;}
    public LocalDate getPaymentDate() {return paymentDate;}
    public LocalDate getExDividendDate() {return exDividendDate;}
    public BigDecimal getAmountPerShare() {return amountPerShare;}
    public BigDecimal getReceivedEur() {return receivedEur;}
}