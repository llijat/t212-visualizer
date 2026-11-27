package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.FinnhubNewsDto;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Decides whether a portfolio news item is really about the company, or only mentions it in passing */
public final class NewsRelevance {

    // What is left over in a company name: ".com", anything in brackets and the legal forms
    private static final Pattern NOISE = Pattern.compile(
            "(?i)\\.com|\\(.*?\\)|,|\\b(inc|corp|corporation|company|co|ltd|plc|holdings?|group|n\\.?v|s\\.?a|ag|se|class [a-c])\\b\\.?");

    // First words too common to count on their own ("Advanced" of Advanced Micro Devices)
    private static final Set<String> GENERIC = Set.of("advanced", "american", "united", "general", "first", "global",
            "international", "national", "new", "the", "bank", "royal", "digital", "applied", "micro");

    // Companies the press calls by another name
    private static final Map<String, List<String>> ALIASES = Map.of(
            "GOOGL", List.of("Google", "YouTube"),
            "GOOG", List.of("Google", "YouTube"),
            "META", List.of("Facebook", "Instagram", "WhatsApp"),
            "BRK.B", List.of("Berkshire"),
            "TSM", List.of("TSMC"));

    private NewsRelevance() {}

    public static boolean isAbout(FinnhubNewsDto news, String companyName) {
        String symbol = news.related();
        String text = (news.headline() == null ? "" : news.headline()) + " " + (news.summary() == null ? "" : news.summary());

        // The symbol in capitals ("AMZN" or "$AMZN"); with 1 or 2 letters only "$F", so "F" or "ON" do not match any text
        if (symbol != null && !symbol.isBlank()) {
            String dollar = symbol.length() >= 3 ? "\\$?" : "\\$";
            if (Pattern.compile("(?<![A-Za-z0-9])" + dollar + Pattern.quote(symbol) + "(?![A-Za-z0-9])").matcher(text).find()) return true;
            for (String alias : ALIASES.getOrDefault(symbol, List.of())) {
                if (mentions(text, alias)) return true;
            }
        }

        if (companyName == null) return false;
        String shortName = shortName(companyName);
        if (shortName.isEmpty()) return false;
        if (mentions(text, shortName)) return true;

        // "Meta Platforms" is usually just "Meta" in the news
        String firstWord = shortName.split(" ")[0];
        return firstWord.length() >= 4 && !GENERIC.contains(firstWord.toLowerCase()) && mentions(text, firstWord);
    }

    static String shortName(String name) {
        return NOISE.matcher(name).replaceAll(" ").replaceAll("\\s+", " ").trim();
    }

    private static boolean mentions(String text, String word) {
        return Pattern.compile("\\b" + Pattern.quote(word) + "\\b", Pattern.CASE_INSENSITIVE).matcher(text).find();
    }
}