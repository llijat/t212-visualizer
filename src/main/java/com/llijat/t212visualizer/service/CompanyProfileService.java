package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.FinnhubCompanyProfileDto;
import com.llijat.t212visualizer.dto.LogoImage;
import com.llijat.t212visualizer.dto.OpenFigiListingDto;
import com.llijat.t212visualizer.model.CompanyProfile;
import com.llijat.t212visualizer.model.Position;
import com.llijat.t212visualizer.repository.CompanyProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CompanyProfileService {
    private static final Logger log = LoggerFactory.getLogger(CompanyProfileService.class);
    private static final Duration REFRESH_AFTER = Duration.ofDays(30);
    private final FinnhubClient finnhubClient;
    private final PositionService positionService;
    private final CompanyProfileRepository companyProfileRepository;
    private final OpenFigiClient openFigiClient;

    public CompanyProfileService(FinnhubClient finnhubClient, PositionService positionService, CompanyProfileRepository companyProfileRepository, OpenFigiClient openFigiClient) {
        this.finnhubClient = finnhubClient;
        this.positionService = positionService;
        this.companyProfileRepository = companyProfileRepository;
        this.openFigiClient = openFigiClient;
    }


    @Scheduled(fixedRate = 24*60*60*1000, initialDelay = 2*60*1000)
    public void refreshProfiles() {
        Instant now = Instant.now();
        Instant staleBefore = now.minus(REFRESH_AFTER);

        List<Position> due = new ArrayList<>();

        for (Position position : positionService.getAllPositions()) {
            Optional<CompanyProfile> existing = companyProfileRepository.findById(position.getTicker());
            if (existing.isEmpty() || existing.get().getFetchedAt().isBefore(staleBefore)) {
                due.add(position);
            }
        }
        if (due.isEmpty()) {
            log.info("All company profiles are up to date.");
            return;
        }

        List<String> isins = due.stream()
                .map(Position::getIsin)
                .filter(Objects::nonNull)
                .toList();

        Map<String, List<OpenFigiListingDto>> listings;
        try {
            listings = openFigiClient.findListings(isins);
        } catch (Exception e) {
            log.warn("Could not ask OpenFIGI, no company profile refreshed today: {}", e.getMessage());
            return;
        }

        int refreshed = 0;
        for (Position position : due) {
            try {
                Optional<CompanyProfile> existing = companyProfileRepository.findById(position.getTicker());
                List<OpenFigiListingDto> found = position.getIsin() == null
                        ? List.of()
                        : listings.getOrDefault(position.getIsin(), List.of());
                Optional<OpenFigiListingDto> listing = ListingSelector.choose(position.getTicker(), found);
                refreshProfile(position, existing, listing, now);
                refreshed++;
            } catch (Exception e) {log.warn("Could not refresh the profile of {}: {}", position.getTicker(), e.getMessage()); }
        }


        log.info("Refreshed {} company profiles.",refreshed);
    }

    private void refreshProfile(Position position, Optional<CompanyProfile> existing, Optional<OpenFigiListingDto> listing, Instant now) {
        CompanyProfile profile = existing.orElseGet(() -> new CompanyProfile(position.getTicker()));
        String symbol = listing.map(OpenFigiListingDto::ticker).orElse(null);
        String exchange = listing.map(OpenFigiListingDto::exchCode).orElse(null);
        profile.updateListing(symbol, exchange);

        // Fuera de EE. UU., Finnhub no tiene nada: se guarda y se acaba aquí
        if (!position.getTicker().contains("_US_")) {
            profile.updateProfile(null, null, null, now);
            companyProfileRepository.save(profile);
            return;
        }

        // De aquí para abajo, solo llegan las de EE. UU.: tu código de siempre
        String finnhubSymbol = symbol != null ? symbol : position.getSymbol();
        FinnhubCompanyProfileDto dto = finnhubClient.fetchCompanyProfile(finnhubSymbol);
        String name = dto == null ? null : dto.name();
        String logoUrl = dto == null ? null : dto.logo();
        Optional<LogoImage> logo = (logoUrl == null || logoUrl.isBlank()) ? Optional.empty() : finnhubClient.downloadLogo(logoUrl);

        profile.updateProfile(name,
                logo.map(LogoImage::bytes).orElse(null),
                logo.map(LogoImage::contentType).orElse(null),
                now);
        companyProfileRepository.save(profile);
    }

    public Map<String, String> currentSymbols() {
        return companyProfileRepository.findAll().stream()
                .filter(profile -> profile.getSymbol() != null)
                .collect(Collectors.toMap(CompanyProfile::getTicker, CompanyProfile::getSymbol));
    }
}
