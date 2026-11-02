package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.FinnhubNewsDto;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Noticias de ejemplo, marcadas como tales, sobre las empresas inventadas de DemoT212Client. */
@Service
@Profile("demo")
public class DemoFinnhubClient implements FinnhubClient {

    @Override
    public List<FinnhubNewsDto> fetchGeneralNews() {
        return List.of(
                news("general", 1L, "", "Noticia de ejemplo: los mercados abren la semana sin grandes cambios", 2),
                news("general", 2L, "", "Noticia de ejemplo: los bancos centrales mantienen los tipos de interés", 5),
                news("general", 3L, "", "Noticia de ejemplo: el sector tecnológico lidera las subidas", 9)
        );
    }

    @Override
    public List<FinnhubNewsDto> fetchCompanyNews(String symbol, LocalDate from, LocalDate to) {
        long baseId = symbol.hashCode() * 10L;
        return List.of(
                news("company", baseId + 1, symbol, "Noticia de ejemplo: " + symbol + " presenta sus resultados trimestrales", 3),
                news("company", baseId + 2, symbol, "Noticia de ejemplo: " + symbol + " anuncia un nuevo producto", 20)
        );
    }

    private FinnhubNewsDto news(String category, long id, String related, String headline, int hoursAgo) {
        long datetime = Instant.now().minusSeconds(hoursAgo * 3600L).getEpochSecond();
        return new FinnhubNewsDto(category, datetime, headline, "#", id, related, "", "Texto de ejemplo del modo demo.");
    }
}
