package com.llijat.t212visualizer.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "company_profiles")
public class CompanyProfile {
    @Id
    private String ticker;
    private String name;
    private byte[] logo;
    private String logoContentType;
    private Instant fetchedAt;
    private String symbol;
    private String exchange;

    protected CompanyProfile() {
        //required by JPA
    }

    public CompanyProfile(String ticker) {
        this.ticker = ticker;
    }

    public void updateProfile(String name, byte[] logo, String logoContentType, Instant fetchedAt) {
        this.name = name;
        this.logo = logo;
        this.logoContentType = logoContentType;
        this.fetchedAt = fetchedAt;
    }

    public void updateListing(String symbol, String exchange) {
        this.symbol = symbol;
        this.exchange = exchange;
    }

    public boolean hasLogo() {
        return logo != null;
    }

    public String getTicker() {return ticker;}
    public String getName() {return name;}
    public byte[] getLogo() {return logo;}
    public String getLogoContentType() {return logoContentType;}
    public Instant getFetchedAt() {return fetchedAt;}
    public String getSymbol() {return symbol;}
    public String getExchange() {return exchange;}
}
