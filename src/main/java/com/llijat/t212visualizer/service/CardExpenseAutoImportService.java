package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.CardExpenseImportResult;
import com.llijat.t212visualizer.dto.T212ExportReportDto;
import com.llijat.t212visualizer.repository.CardExpenseRepository;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringReader;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;

@Service
@Profile("!demo")
public class CardExpenseAutoImportService {
    private static final Logger log = LoggerFactory.getLogger(CardExpenseAutoImportService.class);
    private static final int MAX_CHECKS = 10;
    private static final Duration MARGIN = Duration.ofDays(7);
    private static final Duration MAX_PERIOD = Duration.ofDays(365);

    private final T212ExportClient exportClient;
    private final CardExpenseImportService importService;
    private final CardExpenseRepository repository;
    private final long pollDelayMs;

    public CardExpenseAutoImportService(T212ExportClient exportClient, CardExpenseImportService importService, CardExpenseRepository repository, @Value("${t212.export.poll-delay-ms}") long pollDelayMs) {
        this.exportClient = exportClient;
        this.importService = importService;
        this.repository = repository;
        this.pollDelayMs = pollDelayMs;
    }

    @Scheduled(fixedRate = 24*60*60*1000, initialDelay = 5*60*1000)
    public void importLatest() {
        try {
            Instant to = Instant.now();
            long reportId = exportClient.requestExport(calculateFrom(to), to);
            String link = waitForDownloadLink(reportId);
            if (link == null) {
                return;
            }

            String csv = exportClient.downloadExport(link);
            CardExpenseImportResult result = importService.importCsv(new StringReader(csv));
            log.info("Imported {} new card payments. {} were already saved. Skipped: {}", result.imported(), result.alreadyKnown(), result.ignoredByAction());;
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        catch (Exception e) {
            log.warn("Could not import the card payments: {}", e.getClass().getSimpleName());
        }
    }

    private Instant calculateFrom(Instant to) {
        Instant oldest = to.minus(MAX_PERIOD);
        return repository.findTopByOrderByDateTimeDesc()
                .map(latest-> latest.getDateTime().minus(MARGIN))
                .filter(from->from.isAfter(oldest))
                .orElse(oldest);
    }

    private String waitForDownloadLink(long reportId) throws InterruptedException {
        for (int check = 1; check <= MAX_CHECKS; ++check) {
            Thread.sleep(pollDelayMs);

            for (T212ExportReportDto report :  exportClient.fetchExports()) {
                if (report.reportId() != reportId) {
                    continue;
                }
                if (report.status().equals("Finished")) return report.downloadLink();
                if (report.status().equals("Failed") || report.status().equals("Canceled")) {
                    log.warn("The report ended with status {}", report.status());
                    return null;
                }
            }
        }
        log.warn("The report was not ready after {} checks", MAX_CHECKS);
        return null;
    }
}
