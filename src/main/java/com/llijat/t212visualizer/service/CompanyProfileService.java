package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.FinnhubCompanyProfileDto;
import com.llijat.t212visualizer.dto.LogoImage;
import com.llijat.t212visualizer.model.CompanyProfile;
import com.llijat.t212visualizer.model.Position;
import com.llijat.t212visualizer.repository.CompanyProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class CompanyProfileService {
    private static final Logger log = LoggerFactory.getLogger(CompanyProfileService.class);
    private static final Duration REFRESH_AFTER = Duration.ofDays(30);
    private final FinnhubClient finnhubClient;
    private final PositionService positionService;
    private final CompanyProfileRepository companyProfileRepository;

    public CompanyProfileService(FinnhubClient finnhubClient, PositionService positionService, CompanyProfileRepository companyProfileRepository) {
        this.finnhubClient = finnhubClient;
        this.positionService = positionService;
        this.companyProfileRepository = companyProfileRepository;
    }


    @Scheduled(fixedRate = 24*60*60*1000, initialDelay = 2*60*1000)
    public void refreshProfiles() {
        Instant now = Instant.now();
        Instant staleBefore = now.minus(REFRESH_AFTER);

        int refreshed = 0;
        for (Position position : positionService.getAllPositions()) {
            if (position.getTicker().contains("_US_")) {
                try {
                    Optional<CompanyProfile> existing = companyProfileRepository.findById(position.getTicker());
                    if (existing.isPresent() && existing.get().getFetchedAt().isAfter(staleBefore)) continue;
                    refreshProfile(position, existing, now);
                    refreshed++;
                } catch (Exception e) {
                    log.warn("Could not refresh the profile of {}: {}", position.getSymbol(), e.getMessage());
                }
            }
        }
        log.info("Refreshed {} company profiles.",refreshed);
    }

    private void refreshProfile(Position position, Optional<CompanyProfile> existing, Instant now) {
        FinnhubCompanyProfileDto dto = finnhubClient.fetchCompanyProfile(position.getSymbol());
        String name = dto == null ? null : dto.name();
        String logoUrl = dto == null ? null : dto.logo();

        Optional<LogoImage> logo = (logoUrl == null || logoUrl.isBlank()) ? Optional.empty() : finnhubClient.downloadLogo(logoUrl);

        CompanyProfile profile = existing.orElseGet(() -> new CompanyProfile(position.getTicker()));

        profile.updateProfile(name,
                logo.map(LogoImage::bytes).orElse(null),
                logo.map(LogoImage::contentType).orElse(null),
                now);

        companyProfileRepository.save(profile);
    }
}
