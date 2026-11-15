package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.OpenFigiListingDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ListingSelector {
    private static final Map<String, List<String>> EXCHANGES = Map.of(
            "US", List.of("UW", "UQ", "UR", "UN", "UA", "UP"),
            "d", List.of("GY"),
            "l", List.of("LN"),
            "s", List.of("SW", "SE"));

    private ListingSelector() {}

    public static Optional<OpenFigiListingDto> choose(String t212Ticker, List<OpenFigiListingDto> listings) {
        int underscore = t212Ticker.indexOf('_');
        if (underscore < 1) return Optional.empty();

        String symbol = t212Ticker.substring(0, underscore);

        String market = t212Ticker.contains("_US_") ? "US" : symbol.substring(symbol.length() - 1);
        String base = market.equals("US") ? symbol : symbol.substring(0, symbol.length() - 1);

        List<String> exchanges = EXCHANGES.getOrDefault(market, List.of());
        List<OpenFigiListingDto> candidates = new ArrayList<>();
        for (String exchange : exchanges) {
            for (OpenFigiListingDto listing : listings) {
                if (exchange.equals(listing.exchCode()) && listing.ticker() != null) candidates.add(listing);
            }
        }

        return candidates.stream().filter(listing -> base.equals(listing.ticker()))
                .findFirst().or(() -> candidates.stream().findFirst());
    }
}
