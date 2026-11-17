package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.FinnhubCompanyProfileDto;
import com.llijat.t212visualizer.dto.FinnhubEarningsDto;
import com.llijat.t212visualizer.dto.FinnhubNewsDto;
import com.llijat.t212visualizer.dto.LogoImage;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

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
                news("general", 5L, "", "Sample news: silver follows gold up on industrial demand", 54, "Demo Markets")
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
                news("company", baseId + 4, symbol, "Sample news: " + symbol + " opens a new office in Europe", 100, "Sample Wire")
        );
    }

    private FinnhubNewsDto news(String category, long id, String related, String headline, int hoursAgo, String source) {
        long datetime = Instant.now().minusSeconds(hoursAgo * 3600L).getEpochSecond();
        return new FinnhubNewsDto(category, datetime, headline, "#", id, related, "", "Sample text from demo mode.", source);
    }

    @Override
    public List<FinnhubEarningsDto> fetchEarnings(String symbol, LocalDate from, LocalDate to) {
        return List.of();
    }

    @Override
    public FinnhubCompanyProfileDto fetchCompanyProfile(String symbol) {
        return new FinnhubCompanyProfileDto(null, null);
    }

    @Override
    public Optional<LogoImage> downloadLogo(String url) {
        return Optional.empty();
    }
}
