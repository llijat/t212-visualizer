package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.T212PositionDto;
import com.llijat.t212visualizer.model.Position;
import com.llijat.t212visualizer.repository.PositionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PositionServiceTest {

    private static final String META_ISIN = "US30303M1027";

    @Mock
    private T212Client t212Client;

    @Mock
    private PositionRepository repository;

    @Captor
    private ArgumentCaptor<Position> savedCaptor;

    private PositionService service;

    @BeforeEach
    void setUp() {
        service = new PositionService(t212Client, repository);
    }

    private static T212PositionDto dto(String ticker, String isin) {
        BigDecimal one = BigDecimal.ONE;
        return new T212PositionDto(one, "2026-01-01T00:00:00Z", one, one, one, BigDecimal.ZERO,
                new T212PositionDto.Instrument("USD", isin, "Meta Platforms", ticker),
                new T212PositionDto.WalletImpact("EUR", one, null, one, BigDecimal.ZERO));
    }

    @Test
    void aNewPosition_isSavedWithItsIsin() {
        when(t212Client.fetchPositions()).thenReturn(List.of(dto("FB_US_EQ", META_ISIN)));
        when(repository.findById("FB_US_EQ")).thenReturn(Optional.empty());

        service.refreshFromT212();

        verify(repository).save(savedCaptor.capture());
        assertEquals("FB_US_EQ", savedCaptor.getValue().getTicker());
        assertEquals(META_ISIN, savedCaptor.getValue().getIsin());
    }

    @Test
    void aPositionSavedBeforeTheIsinExisted_getsItOnTheNextRefresh() {
        // Like the 36 rows that were already there when V7 added the column: no ISIN yet
        BigDecimal one = BigDecimal.ONE;
        Position existing = new Position("FB_US_EQ", "Meta Platforms", null, one, one, one, one, BigDecimal.ZERO, null, one, "USD", "EUR");
        when(t212Client.fetchPositions()).thenReturn(List.of(dto("FB_US_EQ", META_ISIN)));
        when(repository.findById("FB_US_EQ")).thenReturn(Optional.of(existing));

        service.refreshFromT212();

        verify(repository).save(savedCaptor.capture());
        assertSame(existing, savedCaptor.getValue());
        assertEquals(META_ISIN, existing.getIsin());
    }
}
