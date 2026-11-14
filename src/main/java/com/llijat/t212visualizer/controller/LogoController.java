package com.llijat.t212visualizer.controller;

import com.llijat.t212visualizer.model.CompanyProfile;
import com.llijat.t212visualizer.repository.CompanyProfileRepository;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.Duration;

@Controller
public class LogoController {
    private final CompanyProfileRepository companyProfileRepository;

    public LogoController(CompanyProfileRepository companyProfileRepository) {
        this.companyProfileRepository = companyProfileRepository;
    }

    @GetMapping("/logos/{ticker}")
    public ResponseEntity<byte[]> logo(@PathVariable String ticker) {
        return companyProfileRepository.findById(ticker)
                .filter(CompanyProfile::hasLogo)
                .map(profile -> ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(profile.getLogoContentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)))
                .header("X-Content-Type-Options","nosniff")
                        .body(profile.getLogo()))
                .orElse(ResponseEntity.notFound().build());
    }
}
