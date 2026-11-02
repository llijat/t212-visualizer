package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.FinnhubNewsDto;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Sample news, labelled as such, about the invented companies in DemoT212Client. */
@Service
@Profile("demo")
public class DemoFinnhubClient implements FinnhubClient {

    @Override
    public List<FinnhubNewsDto> fetchGeneralNews() {
        return List.of(
                news("general", 1L, "", "Sample news: markets open the week with little change", 2),
                news("general", 2L, "", "Sample news: central banks hold interest rates", 5),
                news("general", 3L, "", "Sample news: the technology sector leads the gains", 9)
        );
    }

    @Override
    public List<FinnhubNewsDto> fetchCompanyNews(String symbol, LocalDate from, LocalDate to) {
        long baseId = symbol.hashCode() * 10L;
        return List.of(
                news("company", baseId + 1, symbol, "Sample news: " + symbol + " reports quarterly results", 3),
                news("company", baseId + 2, symbol, "Sample news: " + symbol + " announces a new product", 20)
        );
    }

    private FinnhubNewsDto news(String category, long id, String related, String headline, int hoursAgo) {
        long datetime = Instant.now().minusSeconds(hoursAgo * 3600L).getEpochSecond();
        return new FinnhubNewsDto(category, datetime, headline, "#", id, related, "", "Sample text from demo mode.");
    }
}
