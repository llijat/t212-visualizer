package com.llijat.t212visualizer.service;


import com.llijat.t212visualizer.dto.Subsector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SectorCatalog {
    private static final Logger log = LoggerFactory.getLogger(SectorCatalog.class);
    public static final Subsector OTHER = new Subsector("Other", "Other", null, BigDecimal.ZERO);

    private static final List<Subsector> SUBSECTORS = List.of(
            new Subsector("Semiconductors", "Technology", "SMH", new BigDecimal("13")),
            new Subsector("Software", "Technology", "IGV", new BigDecimal("11")),
            new Subsector("Hardware & devices", "Technology", "XLK", new BigDecimal("8")),
            new Subsector("Cybersecurity", "Technology", "CIBR", new BigDecimal("2")),
            new Subsector("Banks", "Financials", "KBE", new BigDecimal("4.5")),
            new Subsector("Payments", "Financials", "IPAY", new BigDecimal("3.5")),
            new Subsector("Capital markets", "Financials", "IAI", new BigDecimal("2.5")),
            new Subsector("Insurance", "Financials", "KIE", new BigDecimal("2.5")),
            new Subsector("Retail", "Consumer discretionary", "XRT", new BigDecimal("5")),
            new Subsector("Automobiles", "Consumer discretionary", "CARZ", new BigDecimal("2.5")),
            new Subsector("Travel & leisure", "Consumer discretionary", "PEJ", new BigDecimal("2")),
            new Subsector("Homebuilders", "Consumer discretionary", "XHB", new BigDecimal("1")),
            new Subsector("Internet & media", "Communication services", "XLC", new BigDecimal("8.5")),
            new Subsector("Telecom", "Communication services", "IYZ", new BigDecimal("1.5")),
            new Subsector("Pharma", "Health care", "PPH", new BigDecimal("4")),
            new Subsector("Medical devices", "Health care", "IHI", new BigDecimal("2")),
            new Subsector("Biotech", "Health care", "IBB", new BigDecimal("1.5")),
            new Subsector("Health care providers", "Health care", "IHF", new BigDecimal("1.5")),
            new Subsector("Machinery & services", "Industrials", "XLI", new BigDecimal("4.5")),
            new Subsector("Aerospace & defense", "Industrials", "ITA", new BigDecimal("2")),
            new Subsector("Transportation", "Industrials", "IYT", new BigDecimal("1.5")),
            new Subsector("Food & beverage", "Consumer staples", "PBJ", new BigDecimal("2.5")),
            new Subsector("Household & staples retail", "Consumer staples", "XLP", new BigDecimal("2.5")),
            new Subsector("Oil & gas", "Energy", "XLE", new BigDecimal("2")),
            new Subsector("Exploration & production", "Energy", "XOP", new BigDecimal("0.6")),
            new Subsector("Oil services", "Energy", "OIH", new BigDecimal("0.4")),
            new Subsector("Utilities", "Utilities", "XLU", new BigDecimal("2.1")),
            new Subsector("Clean energy", "Utilities", "ICLN", new BigDecimal("0.2")),
            new Subsector("Real estate", "Real estate", "XLRE", new BigDecimal("2")),
            new Subsector("Chemicals & materials", "Materials", "XLB", new BigDecimal("1.2")),
            new Subsector("Metals & mining", "Materials", "XME", new BigDecimal("0.4")),
            new Subsector("Gold miners", "Materials", "GDX", new BigDecimal("0.2"))
    );

    // finnhubIndustry -> subsector name
    private static final Map<String, String> BY_INDUSTRY = Map.ofEntries(
            Map.entry("Semiconductors", "Semiconductors"),
            Map.entry("Technology", "Software"),
            Map.entry("Communications", "Hardware & devices"),
            Map.entry("Banking", "Banks"),
            Map.entry("Financial Services", "Capital markets"),
            Map.entry("Insurance", "Insurance"),
            Map.entry("Retail", "Retail"),
            Map.entry("Distributors", "Retail"),
            Map.entry("Textiles, Apparel & Luxury Goods", "Retail"),
            Map.entry("Automobiles", "Automobiles"),
            Map.entry("Auto Components", "Automobiles"),
            Map.entry("Hotels, Restaurants & Leisure", "Travel & leisure"),
            Map.entry("Leisure Products", "Travel & leisure"),
            Map.entry("Diversified Consumer Services", "Travel & leisure"),
            Map.entry("Building", "Homebuilders"),
            Map.entry("Media", "Internet & media"),
            Map.entry("Telecommunication", "Telecom"),
            Map.entry("Pharmaceuticals", "Pharma"),
            Map.entry("Health Care", "Medical devices"),
            Map.entry("Life Sciences Tools & Services", "Medical devices"),
            Map.entry("Biotechnology", "Biotech"),
            Map.entry("Machinery", "Machinery & services"),
            Map.entry("Industrial Conglomerates", "Machinery & services"),
            Map.entry("Electrical Equipment", "Machinery & services"),
            Map.entry("Construction", "Machinery & services"),
            Map.entry("Commercial Services & Supplies", "Machinery & services"),
            Map.entry("Professional Services", "Machinery & services"),
            Map.entry("Trading Companies & Distributors", "Machinery & services"),
            Map.entry("Aerospace & Defense", "Aerospace & defense"),
            Map.entry("Airlines", "Transportation"),
            Map.entry("Logistics & Transportation", "Transportation"),
            Map.entry("Road & Rail", "Transportation"),
            Map.entry("Marine", "Transportation"),
            Map.entry("Transportation Infrastructure", "Transportation"),
            Map.entry("Food Products", "Food & beverage"),
            Map.entry("Beverages", "Food & beverage"),
            Map.entry("Tobacco", "Food & beverage"),
            Map.entry("Consumer products", "Household & staples retail"),
            Map.entry("Energy", "Oil & gas"),
            Map.entry("Utilities", "Utilities"),
            Map.entry("Real Estate", "Real estate"),
            Map.entry("Chemicals", "Chemicals & materials"),
            Map.entry("Packaging", "Chemicals & materials"),
            Map.entry("Paper & Forest", "Chemicals & materials"),
            Map.entry("Metals & Mining", "Metals & mining")
    );

    // Finnhub symbol -> subsector name, for the companies Finnhub files somewhere less useful
    private static final Map<String, String> BY_SYMBOL = Map.ofEntries(
            Map.entry("AAPL", "Hardware & devices"), Map.entry("DELL", "Hardware & devices"), Map.entry("HPQ", "Hardware & devices"),
            Map.entry("HPE", "Hardware & devices"), Map.entry("SMCI", "Hardware & devices"), Map.entry("ANET", "Hardware & devices"),
            Map.entry("CSCO", "Hardware & devices"),
            Map.entry("CRWD", "Cybersecurity"), Map.entry("PANW", "Cybersecurity"), Map.entry("FTNT", "Cybersecurity"),
            Map.entry("ZS", "Cybersecurity"), Map.entry("NET", "Cybersecurity"), Map.entry("OKTA", "Cybersecurity"),
            Map.entry("S", "Cybersecurity"),
            Map.entry("V", "Payments"), Map.entry("MA", "Payments"), Map.entry("PYPL", "Payments"), Map.entry("AXP", "Payments"),
            Map.entry("FI", "Payments"), Map.entry("FIS", "Payments"), Map.entry("GPN", "Payments"), Map.entry("XYZ", "Payments"),
            Map.entry("COIN", "Payments"),
            Map.entry("UNH", "Health care providers"), Map.entry("CVS", "Health care providers"), Map.entry("CI", "Health care providers"),
            Map.entry("ELV", "Health care providers"), Map.entry("HUM", "Health care providers"), Map.entry("HCA", "Health care providers"),
            Map.entry("CNC", "Health care providers"),
            Map.entry("WMT", "Household & staples retail"), Map.entry("COST", "Household & staples retail"),
            Map.entry("KR", "Household & staples retail"), Map.entry("TGT", "Household & staples retail"),
            Map.entry("DG", "Household & staples retail"), Map.entry("DLTR", "Household & staples retail"),
            Map.entry("COP", "Exploration & production"), Map.entry("EOG", "Exploration & production"),
            Map.entry("OXY", "Exploration & production"), Map.entry("DVN", "Exploration & production"),
            Map.entry("FANG", "Exploration & production"), Map.entry("APA", "Exploration & production"),
            Map.entry("SLB", "Oil services"), Map.entry("HAL", "Oil services"), Map.entry("BKR", "Oil services"),
            Map.entry("ENPH", "Clean energy"), Map.entry("FSLR", "Clean energy"), Map.entry("SEDG", "Clean energy"),
            Map.entry("RUN", "Clean energy"), Map.entry("PLUG", "Clean energy"),
            Map.entry("NEM", "Gold miners"), Map.entry("GOLD", "Gold miners"), Map.entry("AEM", "Gold miners"),
            Map.entry("KGC", "Gold miners"), Map.entry("WPM", "Gold miners")
    );


    private static final Map<String, Subsector> BY_NAME = SUBSECTORS.stream()
            .collect(Collectors.toMap(Subsector::name, Function.identity()));

    private static final Set<String> WARNED_INDUSTRIES = ConcurrentHashMap.newKeySet();

    private SectorCatalog() {}

    public static List<Subsector> all() {
        return SUBSECTORS;
    }

    public static Subsector subsectorOf(String symbol, String industry) {
        if (symbol != null && BY_SYMBOL.containsKey(symbol)) {
            return BY_NAME.get(BY_SYMBOL.get(symbol));
        }
        if (industry == null) return OTHER;

        String name = BY_INDUSTRY.get(industry);
        if (name != null) return BY_NAME.get(name);

        if (WARNED_INDUSTRIES.add(industry)) {
            log.info("No subsector for the Finnhub industry \"{}\" ({}): it goes to Other", industry, symbol);
        }
        return OTHER;
    }
}
