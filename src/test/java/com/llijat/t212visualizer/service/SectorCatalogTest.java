package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.Subsector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SectorCatalogTest {

    @Test
    void aCompanyInTheExceptions_goesWhereTheExceptionSays_evenIfItsIndustrySaysOtherwise() {
        // Finnhub files Apple under "Technology", which would make it Software
        Subsector apple = SectorCatalog.subsectorOf("AAPL", "Technology");

        assertEquals("Hardware & devices", apple.name());
        assertEquals("Technology", apple.sector());
        assertEquals("XLK", apple.etf());
    }

    @Test
    void anyOtherCompany_goesByItsFinnhubIndustry() {
        Subsector micron = SectorCatalog.subsectorOf("MU", "Semiconductors");

        assertEquals("Semiconductors", micron.name());
        assertEquals("SMH", micron.etf());
        assertEquals("Banks", SectorCatalog.subsectorOf("JPM", "Banking").name());
    }

    @Test
    void withoutSymbol_theIndustryIsStillUsed() {
        assertEquals("Banks", SectorCatalog.subsectorOf(null, "Banking").name());
    }

    @Test
    void withoutIndustry_orWithOneThatIsNotInTheTable_itGoesToOther() {
        // Non-US stocks have no industry, and Finnhub may send one we did not list
        assertSame(SectorCatalog.OTHER, SectorCatalog.subsectorOf("RWE", null));
        assertSame(SectorCatalog.OTHER, SectorCatalog.subsectorOf(null, null));
        assertSame(SectorCatalog.OTHER, SectorCatalog.subsectorOf("XYZW", "Something new"));
        assertSame(SectorCatalog.OTHER, SectorCatalog.subsectorOf("XYZW", "Something new"));   // a second time: only logged once, same answer
    }

    @Test
    void other_hasNoEtfAndNoMarketWeight() {
        assertEquals("Other", SectorCatalog.OTHER.name());
        assertEquals(null, SectorCatalog.OTHER.etf());
        assertEquals(0, SectorCatalog.OTHER.marketWeight().signum());
    }

    @Test
    void theCatalog_has32Subsectors_in11Sectors_eachWithItsOwnNameAndEtf() {
        List<Subsector> all = SectorCatalog.all();

        assertEquals(32, all.size());
        assertEquals(11, all.stream().map(Subsector::sector).distinct().count());
        assertEquals(32, all.stream().map(Subsector::name).distinct().count());
        assertEquals(32, all.stream().map(Subsector::etf).distinct().count());
        all.forEach(subsector -> {
            assertNotNull(subsector.etf(), subsector.name());
            assertTrue(subsector.marketWeight().signum() > 0, subsector.name());
        });
    }

    @Test
    void theMarketWeights_addUpToRoughlyTheWholeSp500() {
        BigDecimal total = SectorCatalog.all().stream().map(Subsector::marketWeight).reduce(BigDecimal.ZERO, BigDecimal::add);

        assertTrue(total.compareTo(new BigDecimal("95")) >= 0 && total.compareTo(new BigDecimal("101")) <= 0, total.toPlainString());
    }

    @Test
    void everyIndustryAndEveryException_pointsToASubsectorThatExists() throws Exception {
        // A typo in a name ("Bank" instead of "Banks") would silently give null: this catches it
        Set<String> names = SectorCatalog.all().stream().map(Subsector::name).collect(Collectors.toSet());
        Set<String> targets = new HashSet<>(privateMap("BY_INDUSTRY").values());
        targets.addAll(privateMap("BY_SYMBOL").values());

        targets.forEach(target -> assertTrue(names.contains(target), target));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> privateMap(String name) throws Exception {
        Field field = SectorCatalog.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Map<String, String>) field.get(null);
    }
}
