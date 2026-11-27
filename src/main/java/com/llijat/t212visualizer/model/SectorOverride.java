package com.llijat.t212visualizer.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "sector_overrides")
public class SectorOverride {
    @Id
    private String ticker;
    private String subsector;

    protected SectorOverride() {}

    public SectorOverride(String ticker) {
        this.ticker = ticker;
    }

    public void changeSubsector(String subsector) {
        this.subsector = subsector;
    }

    public String getTicker() {return ticker;}
    public String getSubsector() {return subsector;}
}
