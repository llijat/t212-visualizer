package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.FinnhubCompanyProfileDto;
import com.llijat.t212visualizer.dto.FinnhubEarningsDto;
import com.llijat.t212visualizer.dto.FinnhubNewsDto;
import com.llijat.t212visualizer.dto.FinnhubQuoteDto;
import com.llijat.t212visualizer.dto.LogoImage;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Sample news, labelled as such, about the invented companies in DemoT212Client. */
@Service
@Profile("demo")
public class DemoFinnhubClient implements FinnhubClient {

    @Override
    public List<FinnhubNewsDto> fetchGeneralNews() {
        return List.of(
                news("general", 1L, "", "Sample news: markets open the week with little change", 2, "Sample Wire"),
                news("general", 2L, "", "Sample news: central banks hold interest rates", 5, "Demo Daily"),
                news("general", 3L, "", "Sample news: the technology sector leads the gains", 9, "Sample Wire"),
                news("general", 4L, "", "Sample news: gold reaches a new high as investors look for safety", 30, "Demo Markets"),
                news("general", 5L, "", "Sample news: silver follows gold up on industrial demand", 54, "Demo Markets"),
                news("general", 6L, "", "Sample news: crude oil slips as OPEC weighs a bigger output", 14, "Demo Daily")
        );
    }

    @Override
    public List<FinnhubNewsDto> fetchCryptoNews() {
        return List.of(
                news("crypto", 11L, "", "Sample news: bitcoin holds above a round number as funds keep buying", 4, "Demo Coins"),
                news("crypto", 12L, "", "Sample news: a new crypto fund opens to European savers", 40, "Sample Wire")
        );
    }

    @Override
    public List<FinnhubNewsDto> fetchCompanyNews(String symbol, LocalDate from, LocalDate to) {
        // Spread over the week, so the news page has several days to group
        long baseId = symbol.hashCode() * 10L;
        return List.of(
                news("company", baseId + 1, symbol, "Sample news: " + symbol + " reports quarterly results", 3, "Sample Wire"),
                news("company", baseId + 2, symbol, "Sample news: " + symbol + " announces a new product", 20, "Demo Daily"),
                news("company", baseId + 3, symbol, "Sample news: analysts raise their target for " + symbol, 50, "Demo Markets"),
                news("company", baseId + 4, symbol, "Sample news: " + symbol + " opens a new office in Europe", 100, "Sample Wire"),
                // Names neither the company nor its symbol: the relevance filter moves it to "loosely related"
                news("company", baseId + 5, symbol, "Sample news: a rival's results lift the whole sector", 30, "Demo Daily")
        );
    }

    private FinnhubNewsDto news(String category, long id, String related, String headline, int hoursAgo, String source) {
        long datetime = Instant.now().minusSeconds(hoursAgo * 3600L).getEpochSecond();
        return new FinnhubNewsDto(category, datetime, headline, "#", id, related, "", "Sample text from demo mode.", source);
    }

    @Override
    public List<FinnhubEarningsDto> fetchEarnings(String symbol, LocalDate from, LocalDate to) {
        // One made-up report per company, always on the same day relative to today: with the demo companies,
        // one was 4 days ago (with its figures) and three come in the next two weeks (only the estimates)
        LocalDate today = LocalDate.now();
        LocalDate date = today.plusDays(Math.floorMod(symbol.hashCode(), 24) - 6);
        String hour = Math.floorMod(symbol.hashCode(), 2) == 0 ? "bmo" : "amc";
        boolean published = date.isBefore(today);
        return List.of(new FinnhubEarningsDto(symbol, date, hour, 4, 2026,
                new BigDecimal("1.20"), published ? new BigDecimal("1.31") : null,
                new BigDecimal("2500000000"), published ? new BigDecimal("2610000000") : null));
    }

    @Override
    public FinnhubCompanyProfileDto fetchCompanyProfile(String symbol) {
        // Only the industry: the demo companies have no real name or logo. Each falls in a different subsector of the Sectors page
        String industry = switch (symbol) {
            case "ACME" -> "Technology";
            case "GLBX" -> "Semiconductors";
            case "INIT" -> "Banking";
            case "HOOL" -> "Media";
            default -> null;
        };
        return new FinnhubCompanyProfileDto(null, null, industry);
    }

    @Override
    public FinnhubQuoteDto fetchQuote(String symbol) {
        // A made-up move for each symbol, always the same one, between -5 % and +5 %, from a session that is happening now
        BigDecimal changePercent = BigDecimal.valueOf(Math.floorMod(symbol.hashCode(), 1001) - 500, 2);
        return new FinnhubQuoteDto(new BigDecimal("100.00"), changePercent, Instant.now().getEpochSecond());
    }

    @Override
    public Optional<LogoImage> downloadLogo(String url) {
        return Optional.empty();
    }
}
