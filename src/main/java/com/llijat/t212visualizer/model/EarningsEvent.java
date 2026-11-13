package com.llijat.t212visualizer.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "earnings_events")
public class EarningsEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String ticker;
    private int fiscalYear;
    private int fiscalQuarter;
    private LocalDate reportDate;
    private String reportHour;
    @Column(precision = 19, scale = 4)
    private BigDecimal epsEstimate;
    @Column(precision = 19, scale = 4)
    private BigDecimal epsActual;
    @Column(precision = 19, scale = 4)
    private BigDecimal revenueEstimate;
    @Column(precision = 19, scale = 4)
    private BigDecimal revenueActual;
    @Column(precision = 19, scale = 4)
    private BigDecimal epsYoy;
    @Column(precision = 19, scale = 4)
    private BigDecimal revenueYoy;

    protected EarningsEvent() {
        //required by JPA
    }

    public EarningsEvent(String ticker, int fiscalYear, int fiscalQuarter) {
        this.ticker = ticker;
        this.fiscalYear = fiscalYear;
        this.fiscalQuarter = fiscalQuarter;
    }

    public void updateReport(LocalDate reportDate, String reportHour, BigDecimal epsEstimate, BigDecimal epsActual, BigDecimal revenueEstimate, BigDecimal revenueActual) {
        this.reportDate = reportDate;
        this.reportHour = reportHour;
        this.epsEstimate = epsEstimate;
        this.epsActual = epsActual;
        this.revenueEstimate = revenueEstimate;
        this.revenueActual = revenueActual;
    }

    public void updateYoy(BigDecimal epsYoy, BigDecimal revenueYoy) {
        this.epsYoy = epsYoy;
        this.revenueYoy = revenueYoy;
    }

    public Long getId() {return id;}
    public String getTicker() {return ticker;}
    public int getFiscalYear() {return fiscalYear;}
    public int getFiscalQuarter() {return fiscalQuarter;}
    public LocalDate getReportDate() {return reportDate;}
    public String getReportHour() {return reportHour;}
    public BigDecimal getEpsEstimate() {return epsEstimate;}
    public BigDecimal getEpsActual() {return epsActual;}
    public BigDecimal getRevenueEstimate() {return revenueEstimate;}
    public BigDecimal getRevenueActual() {return revenueActual;}
    public BigDecimal getEpsYoy() {return epsYoy;}
    public BigDecimal getRevenueYoy() {return revenueYoy;}
}
