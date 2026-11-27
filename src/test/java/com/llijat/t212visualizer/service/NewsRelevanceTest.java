package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.FinnhubNewsDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NewsRelevanceTest {

    private static FinnhubNewsDto news(String symbol, String headline, String summary) {
        return new FinnhubNewsDto("company", 0, headline, "https://example.com", 1, symbol, "", summary, "Yahoo");
    }

    // ===== Cleaning the company name =====

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Amazon.com Inc        | Amazon",
            "Alphabet (Class A)    | Alphabet",
            "Meta Platforms, Inc.  | Meta Platforms",
            "Coca-Cola Co          | Coca-Cola",
            "RWE AG                | RWE",
            "SAP SE                | SAP",
            "Berkshire Hathaway Inc. Class B | Berkshire Hathaway",
            "NVIDIA Corporation    | NVIDIA",
    })
    void theShortName_dropsTheLegalFormsAndTheBrackets(String name, String expected) {
        assertEquals(expected, NewsRelevance.shortName(name));
    }

    // ===== Which news are about the company =====

    @Test
    void theExampleFromYahoo_aboutDisneyAndNetflix_isNotAboutAmazon() {
        FinnhubNewsDto news = news("AMZN", "NFL's Thanksgiving Boom Raises Stakes for Disney, Netflix",
                "America's favorite sport is becoming an increasingly expensive business.");

        assertFalse(NewsRelevance.isAbout(news, "Amazon.com"));
    }

    @Test
    void theCompanyName_inTheHeadlineOrTheSummary_whateverTheCapitals() {
        assertTrue(NewsRelevance.isAbout(news("AMZN", "Amazon cuts prices", ""), "Amazon.com Inc"));
        assertTrue(NewsRelevance.isAbout(news("NVDA", "Chips rally", "Nvidia leads the gains."), "NVIDIA Corporation"));
    }

    @Test
    void theSymbol_inCapitals_withOrWithoutDollar() {
        assertTrue(NewsRelevance.isAbout(news("AMZN", "Why AMZN fell today", ""), null));
        assertTrue(NewsRelevance.isAbout(news("AMZN", "Buying $AMZN before earnings", ""), null));
        assertFalse(NewsRelevance.isAbout(news("AMZN", "amzn", ""), null));             // capitals only
        assertFalse(NewsRelevance.isAbout(news("AMZN", "AMZNX is a fund", ""), null));  // part of a longer word
    }

    @Test
    void aSymbolOfOneOrTwoLetters_needsTheDollar() {
        assertFalse(NewsRelevance.isAbout(news("F", "Plan F for the economy", ""), null));
        assertFalse(NewsRelevance.isAbout(news("ON", "ON the move: markets rally", ""), null));
        assertTrue(NewsRelevance.isAbout(news("F", "Is $F a buy?", ""), null));
    }

    @Test
    void theFirstWordOfTheName_isEnough_unlessItIsACommonWord() {
        assertTrue(NewsRelevance.isAbout(news("META", "Meta bets on smart glasses", ""), "Meta Platforms Inc"));
        // "Advanced" alone is not Advanced Micro Devices
        assertFalse(NewsRelevance.isAbout(news("AMD", "Advanced packaging is the new bottleneck", ""), "Advanced Micro Devices"));
        // Words of three letters or fewer are not enough either
        assertFalse(NewsRelevance.isAbout(news("XYZ", "Fun times for all", ""), "Fun Brands Holdings"));
    }

    @Test
    void theNamesThePressUses_countToo() {
        assertTrue(NewsRelevance.isAbout(news("GOOGL", "Google wins an antitrust appeal", ""), "Alphabet (Class A)"));
        assertTrue(NewsRelevance.isAbout(news("META", "Instagram adds a new feed", ""), "Meta Platforms"));
        assertTrue(NewsRelevance.isAbout(news("TSM", "TSMC raises its forecast", ""), "Taiwan Semiconductor Manufacturing"));
    }

    @Test
    void wholeWordsOnly() {
        // "Apple" inside "Pineapple" is not Apple
        assertFalse(NewsRelevance.isAbout(news("AAPL", "Pineapple prices soar", ""), "Apple Inc"));
    }

    @Test
    void withoutHeadlineOrSummary_orWithoutSymbol_nothingBreaks() {
        assertFalse(NewsRelevance.isAbout(news("AMZN", null, null), "Amazon.com"));
        assertTrue(NewsRelevance.isAbout(news(null, "Amazon opens a store", null), "Amazon.com"));
        assertFalse(NewsRelevance.isAbout(news(" ", "Markets rally", ""), null));
    }
}
