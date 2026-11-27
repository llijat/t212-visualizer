package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.HoldingMove;
import com.llijat.t212visualizer.dto.SectorHolding;
import com.llijat.t212visualizer.dto.SectorMap;
import com.llijat.t212visualizer.dto.SectorTile;
import com.llijat.t212visualizer.dto.SubsectorTile;
import com.llijat.t212visualizer.model.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SectorServiceTest {

    @Mock
    private PositionService positionService;

    @Mock
    private QuoteService quoteService;

    @Mock
    private CompanyProfileService companyProfileService;

    @Mock
    private SectorOverrideService sectorOverrideService;

    private SectorService sectorService;

    @BeforeEach
    void setUp() {
        sectorService = new SectorService(positionService, quoteService, companyProfileService, sectorOverrideService);
    }

    // A real Position: only the ticker, the name and what it is worth today matter here
    private static Position position(String ticker, String value) {
        return new Position(ticker, ticker + " Inc", null, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                value == null ? null : new BigDecimal(value), BigDecimal.ZERO, null, BigDecimal.ONE, "USD", "EUR");
    }

    private void thePortfolioIs(Map<String, String> industries, Position... positions) {
        when(positionService.getAllPositions()).thenReturn(List.of(positions));
        when(companyProfileService.currentSymbols()).thenReturn(Map.of());
        when(companyProfileService.currentIndustries()).thenReturn(industries);
    }

    private static SectorTile sector(SectorMap map, String name) {
        return map.sectors().stream().filter(sector -> sector.name().equals(name)).findFirst().orElseThrow();
    }

    private static SubsectorTile subsector(SectorMap map, String sectorName, String name) {
        return sector(map, sectorName).subsectors().stream().filter(tile -> tile.name().equals(name)).findFirst().orElseThrow();
    }

    // ===== The map itself =====

    @Test
    void withAnEmptyPortfolio_everySectorAndSubsectorIsStillThere_inTheCatalogsOrder() {
        thePortfolioIs(Map.of());

        SectorMap map = sectorService.getSectorMap();

        assertEquals(11, map.sectors().size());
        assertEquals("Technology", map.sectors().get(0).name());
        assertEquals(32, map.sectors().stream().mapToInt(sector -> sector.subsectors().size()).sum());
        assertEquals(0, map.invested().signum());
        assertEquals(new BigDecimal("34"), sector(map, "Technology").marketWeight());   // 13 + 11 + 8 + 2
    }

    @Test
    void aSubsectorWithoutHoldings_hasZeroValue_andNoStocks() {
        thePortfolioIs(Map.of());

        SubsectorTile banks = subsector(sectorService.getSectorMap(), "Financials", "Banks");

        assertEquals("KBE", banks.etf());
        assertEquals(0, banks.myValue().signum());
        assertEquals(0, banks.myWeight().signum());
        assertTrue(banks.holdings().isEmpty());
    }

    // ===== My stocks in each subsector =====

    @Test
    void myStocks_goToTheirSubsector_biggestFirst_withTheirShareOfThePortfolio() {
        thePortfolioIs(Map.of("AMD_US_EQ", "Semiconductors", "NVDA_US_EQ", "Semiconductors", "AAPL_US_EQ", "Technology"),
                position("AMD_US_EQ", "300"), position("NVDA_US_EQ", "600"), position("AAPL_US_EQ", "100"));

        SectorMap map = sectorService.getSectorMap();
        SubsectorTile semis = subsector(map, "Technology", "Semiconductors");

        assertEquals(new BigDecimal("1000"), map.invested());
        assertEquals(new BigDecimal("900"), semis.myValue());
        assertEquals(new BigDecimal("90.0"), semis.myWeight());
        assertEquals(List.of("NVDA", "AMD"), semis.holdings().stream().map(SectorHolding::symbol).toList());
        assertEquals(new BigDecimal("60.0"), semis.holdings().get(0).weight());
        assertEquals("NVDA_US_EQ Inc", semis.holdings().get(0).name());
        // Apple says "Technology", but the exceptions put it in Hardware & devices
        assertEquals(List.of("AAPL"), subsector(map, "Technology", "Hardware & devices").holdings().stream().map(SectorHolding::symbol).toList());
        assertEquals(new BigDecimal("1000"), sector(map, "Technology").myValue());
    }

    @Test
    void theCurrentSymbol_isTheOneLookedUpInTheExceptions() {
        // T212 still says SQ; the company is now XYZ, which the exceptions file under Payments
        when(positionService.getAllPositions()).thenReturn(List.of(position("SQ_US_EQ", "500")));
        when(companyProfileService.currentSymbols()).thenReturn(Map.of("SQ_US_EQ", "XYZ"));
        when(companyProfileService.currentIndustries()).thenReturn(Map.of("SQ_US_EQ", "Financial Services"));

        SubsectorTile payments = subsector(sectorService.getSectorMap(), "Financials", "Payments");

        assertEquals(List.of("XYZ"), payments.holdings().stream().map(SectorHolding::symbol).toList());
    }

    @Test
    void todaysMoveInEuros_comesFromTodaysPercent_overYesterdaysValue() {
        // NVDA is worth 600 after rising 20 %: it was worth 500, so it made 100 (not 120).
        // AMD has no quote today: it counts as nothing, not as an error
        thePortfolioIs(Map.of("AMD_US_EQ", "Semiconductors", "NVDA_US_EQ", "Semiconductors"),
                position("NVDA_US_EQ", "600"), position("AMD_US_EQ", "300"));
        when(quoteService.getMoves()).thenReturn(List.of(new HoldingMove("NVDA", "NVDA_US_EQ", new BigDecimal("20"), new BigDecimal("66.7"))));

        SubsectorTile semis = subsector(sectorService.getSectorMap(), "Technology", "Semiconductors");

        assertEquals(new BigDecimal("100.00"), semis.myTodayChange());
        assertEquals(new BigDecimal("20"), semis.holdings().get(0).changePercent());
        assertNull(semis.holdings().get(1).changePercent());
    }

    @Test
    void aPositionWithoutValue_isLeftOut() {
        thePortfolioIs(Map.of("NVDA_US_EQ", "Semiconductors"), position("NVDA_US_EQ", "600"), position("AMD_US_EQ", null));

        SectorMap map = sectorService.getSectorMap();

        assertEquals(new BigDecimal("600"), map.invested());
        assertEquals(1, subsector(map, "Technology", "Semiconductors").holdings().size());
    }

    // ===== How much each subsector and sector moved =====

    @Test
    void eachSubsector_movesLikeItsEtf() {
        thePortfolioIs(Map.of());
        when(quoteService.getSectorEtfMoves()).thenReturn(Map.of("SMH", new BigDecimal("2.00")));

        SectorMap map = sectorService.getSectorMap();

        assertEquals(new BigDecimal("2.00"), subsector(map, "Technology", "Semiconductors").changePercent());
        assertNull(subsector(map, "Technology", "Software").changePercent());   // no quote yet
    }

    @Test
    void aSector_movesAsTheAverageOfItsSubsectors_weightedByMarket_leavingOutThoseWithoutQuote() {
        // SMH +2 % weighs 13 and IGV −1 % weighs 11; XLK and CIBR have no quote: (26 − 11) / 24 = 0.625
        thePortfolioIs(Map.of());
        when(quoteService.getSectorEtfMoves()).thenReturn(Map.of("SMH", new BigDecimal("2.00"), "IGV", new BigDecimal("-1.00")));

        SectorMap map = sectorService.getSectorMap();

        assertEquals(new BigDecimal("0.63"), sector(map, "Technology").changePercent());
        assertNull(sector(map, "Energy").changePercent());   // none of its ETFs has a quote
    }

    // ===== Other =====

    @Test
    void stocksWithoutASubsector_goToOther_atTheEnd() {
        // A non-US stock has no industry
        thePortfolioIs(Map.of("NVDA_US_EQ", "Semiconductors"), position("NVDA_US_EQ", "600"), position("RWEd_EQ", "400"));

        SectorMap map = sectorService.getSectorMap();
        SectorTile other = map.sectors().get(map.sectors().size() - 1);

        assertEquals(12, map.sectors().size());
        assertEquals("Other", other.name());
        assertNull(other.changePercent());
        assertEquals(1, other.subsectors().size());
        assertNull(other.subsectors().get(0).etf());
        assertEquals(new BigDecimal("40.0"), other.subsectors().get(0).myWeight());
        assertEquals(List.of("RWEd_EQ"), other.subsectors().get(0).holdings().stream().map(SectorHolding::ticker).toList());
    }

    @Test
    void withoutStocksInOther_thereIsNoOtherSector() {
        thePortfolioIs(Map.of("NVDA_US_EQ", "Semiconductors"), position("NVDA_US_EQ", "600"));

        assertTrue(sectorService.getSectorMap().sectors().stream().noneMatch(sector -> sector.name().equals("Other")));
    }

    // ===== The subsector you chose =====

    @Test
    void theSubsectorYouChose_winsOverTheIndustry_andTheStockIsMarkedAsPlacedByYou() {
        // RWE has no industry (not a US stock): you put it in Clean energy
        thePortfolioIs(Map.of("NVDA_US_EQ", "Semiconductors"), position("NVDA_US_EQ", "600"), position("RWEd_EQ", "400"));
        when(sectorOverrideService.getOverrides()).thenReturn(Map.of("RWEd_EQ", "Clean energy"));

        SectorMap map = sectorService.getSectorMap();
        SubsectorTile clean = subsector(map, "Utilities", "Clean energy");

        assertEquals(List.of("RWEd_EQ"), clean.holdings().stream().map(SectorHolding::ticker).toList());
        assertTrue(clean.holdings().get(0).chosen());
        assertFalse(subsector(map, "Technology", "Semiconductors").holdings().get(0).chosen());
        // Nothing left in Other, so there is no Other
        assertTrue(map.sectors().stream().noneMatch(sector -> sector.name().equals("Other")));
    }

    @Test
    void aChoiceCanMoveAStockAwayFromItsIndustry_too() {
        thePortfolioIs(Map.of("NVDA_US_EQ", "Semiconductors"), position("NVDA_US_EQ", "600"));
        when(sectorOverrideService.getOverrides()).thenReturn(Map.of("NVDA_US_EQ", "Software"));

        SectorMap map = sectorService.getSectorMap();

        assertEquals(1, subsector(map, "Technology", "Software").holdings().size());
        assertTrue(subsector(map, "Technology", "Semiconductors").holdings().isEmpty());
    }

    @Test
    void aChoiceThatNoLongerExistsInTheCatalog_isIgnored() {
        // A subsector renamed or removed later: the stock goes back to where its industry puts it
        thePortfolioIs(Map.of("NVDA_US_EQ", "Semiconductors"), position("NVDA_US_EQ", "600"));
        when(sectorOverrideService.getOverrides()).thenReturn(Map.of("NVDA_US_EQ", "Old name"));

        SubsectorTile semis = subsector(sectorService.getSectorMap(), "Technology", "Semiconductors");

        assertEquals(1, semis.holdings().size());
        assertTrue(semis.holdings().get(0).chosen());   // still yours: "Back to automatic" clears it
    }
}
