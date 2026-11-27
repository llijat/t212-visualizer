package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.model.SectorOverride;
import com.llijat.t212visualizer.repository.SectorOverrideRepository;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SectorOverrideService {
    private final SectorOverrideRepository sectorOverrideRepository;

    public SectorOverrideService(SectorOverrideRepository sectorOverrideRepository) {this.sectorOverrideRepository = sectorOverrideRepository;}

    public void saveSubsector(String ticker, String subsector) {
        if (ticker == null || ticker.isBlank()) throw new IllegalArgumentException("Choose a stock.");
        if (subsector == null || subsector.isBlank() || subsector.length() > 100) throw new IllegalArgumentException("Choose a subsector.");
        if (SectorCatalog.byName(subsector).isEmpty()) throw new IllegalArgumentException("Choose a subsector.");

        SectorOverride correction = sectorOverrideRepository.findById(ticker).orElseGet(() -> new SectorOverride(ticker));
        correction.changeSubsector(subsector);
        sectorOverrideRepository.save(correction);
    }

    public void resetSubsector(String ticker) {sectorOverrideRepository.deleteById(ticker);}

    public Map<String, String> getOverrides() {
        return sectorOverrideRepository.findAll().stream()
                .collect(Collectors.toMap(SectorOverride::getTicker, SectorOverride::getSubsector));
    }
}
