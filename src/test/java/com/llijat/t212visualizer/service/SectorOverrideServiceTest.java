package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.model.SectorOverride;
import com.llijat.t212visualizer.repository.SectorOverrideRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SectorOverrideServiceTest {

    @Mock
    private SectorOverrideRepository sectorOverrideRepository;

    @Captor
    private ArgumentCaptor<SectorOverride> saved;

    private SectorOverrideService sectorOverrideService;

    @BeforeEach
    void setUp() {
        sectorOverrideService = new SectorOverrideService(sectorOverrideRepository);
    }

    private static SectorOverride override(String ticker, String subsector) {
        SectorOverride override = new SectorOverride(ticker);
        override.changeSubsector(subsector);
        return override;
    }

    @Test
    void aFirstChoice_isSavedAsANewRow() {
        when(sectorOverrideRepository.findById("RWEd_EQ")).thenReturn(Optional.empty());

        sectorOverrideService.saveSubsector("RWEd_EQ", "Clean energy");

        verify(sectorOverrideRepository).save(saved.capture());
        assertEquals("RWEd_EQ", saved.getValue().getTicker());
        assertEquals("Clean energy", saved.getValue().getSubsector());
    }

    @Test
    void aSecondChoice_changesTheSameRow() {
        SectorOverride existing = override("RWEd_EQ", "Utilities");
        when(sectorOverrideRepository.findById("RWEd_EQ")).thenReturn(Optional.of(existing));

        sectorOverrideService.saveSubsector("RWEd_EQ", "Clean energy");

        verify(sectorOverrideRepository).save(saved.capture());
        assertSame(existing, saved.getValue());
        assertEquals("Clean energy", existing.getSubsector());
    }

    @Test
    void aSubsectorThatIsNotInTheCatalog_isRefused() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> sectorOverrideService.saveSubsector("RWEd_EQ", "Renewables of my own"));

        assertEquals("Choose a subsector.", error.getMessage());
        verify(sectorOverrideRepository, never()).save(any());
    }

    @Test
    void withoutAStock_orWithoutASubsector_nothingIsSaved() {
        assertThrows(IllegalArgumentException.class, () -> sectorOverrideService.saveSubsector(" ", "Clean energy"));
        assertThrows(IllegalArgumentException.class, () -> sectorOverrideService.saveSubsector(null, "Clean energy"));
        assertThrows(IllegalArgumentException.class, () -> sectorOverrideService.saveSubsector("RWEd_EQ", null));
        verify(sectorOverrideRepository, never()).save(any());
    }

    @Test
    void backToAutomatic_deletesTheChoice() {
        sectorOverrideService.resetSubsector("RWEd_EQ");

        verify(sectorOverrideRepository).deleteById("RWEd_EQ");
    }

    @Test
    void theChoices_comeAsTickerToSubsector() {
        when(sectorOverrideRepository.findAll()).thenReturn(List.of(override("RWEd_EQ", "Clean energy"), override("NVDA_US_EQ", "Software")));

        assertEquals(Map.of("RWEd_EQ", "Clean energy", "NVDA_US_EQ", "Software"), sectorOverrideService.getOverrides());
    }
}
