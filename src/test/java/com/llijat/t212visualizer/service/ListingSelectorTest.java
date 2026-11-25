package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.OpenFigiListingDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A static method with no Spring and no mocks: the test just calls it.
 * The listings are taken from real OpenFIGI answers (15/11/2026), shortened,
 * and often in an awkward order on purpose: the table must decide, not the order.
 */
class ListingSelectorTest {

    private static OpenFigiListingDto listing(String ticker, String exchCode) {
        return new OpenFigiListingDto(ticker, exchCode, "Common Stock", "SOME COMPANY");
    }

    private static void assertChosen(String ticker, String exchCode, Optional<OpenFigiListingDto> chosen) {
        assertTrue(chosen.isPresent(), "nothing was chosen");
        assertEquals(ticker, chosen.get().ticker());
        assertEquals(exchCode, chosen.get().exchCode());
    }

    @Test
    void meta_isFoundUnderItsNewTicker_onNasdaq_evenThoughNyseComesFirst() {
        // T212 still calls it FB; Meta trades on every US market, NYSE (UN) included
        List<OpenFigiListingDto> listings = List.of(
                listing("META", "US"), listing("META", "UA"), listing("META", "UN"), listing("META", "UP"),
                listing("META", "UW"), listing("META", "UF"), listing("FB2A", "GY"), listing("0QZI", "LN"));

        assertChosen("META", "UW", ListingSelector.choose("FB_US_EQ", listings));
    }

    @Test
    void rtx_hasNoNasdaqListing_soNyseIsChosen() {
        List<OpenFigiListingDto> listings = List.of(
                listing("RTX", "US"), listing("RTX", "UA"), listing("RTX", "UN"), listing("RTX", "UP"),
                listing("5UR", "GY"), listing("RTX", "SW"));

        assertChosen("RTX", "UN", ListingSelector.choose("UTX_US_EQ", listings));
    }

    @Test
    void rwe_isChosenOnXetra_notInFrankfurtOrTheUs() {
        List<OpenFigiListingDto> listings = List.of(
                listing("RWE", "GR"), listing("RWE", "GF"), listing("RWE", "GY"), listing("RWE", "SW"), listing("RWNFF", "US"));

        assertChosen("RWE", "GY", ListingSelector.choose("RWEd_EQ", listings));
    }

    @Test
    void theTickerThatMatchesT212_winsOverTheFirstOneOnTheSameMarket() {
        // The same ISIN has three London tickers, one per currency; T212 holds IGLN
        List<OpenFigiListingDto> listings = List.of(
                listing("SGLN", "LN"), listing("PHYMF", "US"), listing("IGLN", "LN"), listing("EGLN", "LN"));

        assertChosen("IGLN", "LN", ListingSelector.choose("IGLNl_EQ", listings));
    }

    @Test
    void aMatchingTicker_winsEvenOnALessPreferredMarket() {
        // Without the matching step, the first candidate (OTHER on UW) would be chosen
        List<OpenFigiListingDto> listings = List.of(listing("OTHER", "UW"), listing("ABC", "UN"));

        assertChosen("ABC", "UN", ListingSelector.choose("ABC_US_EQ", listings));
    }

    @Test
    void swissStocks_preferSw_overSe() {
        List<OpenFigiListingDto> listings = List.of(listing("DFND", "SE"), listing("DFND", "SW"), listing("DFND", "LN"));

        assertChosen("DFND", "SW", ListingSelector.choose("DFNDs_EQ", listings));
    }

    @Test
    void anUnknownMarketLetter_choosesNothing() {
        List<OpenFigiListingDto> listings = List.of(listing("ABC", "GY"), listing("ABC", "UW"));

        assertTrue(ListingSelector.choose("ABCx_EQ", listings).isEmpty());
    }

    @Test
    void listingsOnlyOnOtherMarkets_chooseNothing() {
        // A US stock that only appears on the composite "US" code: we cannot tell Nasdaq from NYSE
        List<OpenFigiListingDto> listings = List.of(listing("ABC", "US"), listing("ABC", "GY"));

        assertTrue(ListingSelector.choose("ABC_US_EQ", listings).isEmpty());
    }

    @Test
    void anIsinOpenFigiDidNotKnow_choosesNothing() {
        assertTrue(ListingSelector.choose("FB_US_EQ", List.of()).isEmpty());
    }

    @Test
    void aTickerWithoutAnUnderscore_orStartingWithOne_choosesNothing() {
        List<OpenFigiListingDto> listings = List.of(listing("META", "UW"));

        assertTrue(ListingSelector.choose("META", listings).isEmpty());
        assertTrue(ListingSelector.choose("_US_EQ", listings).isEmpty());
    }

    @Test
    void listingsWithMissingFields_areSkipped_withoutBreakingAnything() {
        // Data from the internet: the safe value goes on the left of equals(),
        // and a listing without a ticker is never chosen, even if it comes first
        List<OpenFigiListingDto> listings = List.of(
                new OpenFigiListingDto(null, "UW", null, null),
                new OpenFigiListingDto("META", null, null, null),
                listing("META", "UW"));

        assertChosen("META", "UW", ListingSelector.choose("FB_US_EQ", listings));
    }

    @Test
    void ifNoListingHasATicker_nothingIsChosen() {
        List<OpenFigiListingDto> listings = List.of(new OpenFigiListingDto(null, "UW", null, null));

        assertTrue(ListingSelector.choose("FB_US_EQ", listings).isEmpty());
    }
}
