package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.T212AccountSummaryDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class AccountService {
    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final T212Client t212Client;
    private T212AccountSummaryDto summary; // null until the first successful refresh

    public AccountService(T212Client t212Client) {
        this.t212Client = t212Client;
    }

    public T212AccountSummaryDto getSummary() {
        return summary;
    }

    /** GET /equity/account/summary allows 1 req/5s, so every 5 minutes is well within the limit. */
    @Scheduled(fixedRate = 5 * 60 * 1000, initialDelay = 0)
    public void refreshSummary() {
        log.info("Refreshing account summary...");

        try {
            summary = t212Client.fetchAccountSummary();
            log.info("Refreshed account summary.");
        } catch (Exception e) {
            // Keep the last good summary: better slightly old numbers than empty cards
            log.warn("No se pudo refrescar el resumen de la cuenta: {}", e.getMessage());
        }
    }
}
