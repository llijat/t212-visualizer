package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.FinnhubCompanyProfileDto;
import com.llijat.t212visualizer.dto.LogoImage;
import com.llijat.t212visualizer.model.CompanyProfile;
import com.llijat.t212visualizer.model.Position;
import com.llijat.t212visualizer.repository.CompanyProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyProfileServiceTest {

    private static final String LOGO_URL = "https://static.example.test/logos/MU.png";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};

    @Mock
    private FinnhubClient finnhubClient;

    @Mock
    private PositionService positionService;

    @Mock
    private CompanyProfileRepository repository;

    @Captor
    private ArgumentCaptor<CompanyProfile> savedCaptor;

    private CompanyProfileService service;

    @BeforeEach
    void setUp() {
        service = new CompanyProfileService(finnhubClient, positionService, repository);
    }

    // Only the ticker matters to the service; the figures are invented
    private static Position position(String ticker) {
        BigDecimal one = BigDecimal.ONE;
        return new Position(ticker, ticker + " Inc.", one, one, one, one, BigDecimal.ZERO, null, one, "USD", "EUR");
    }

    private static CompanyProfile profileFetched(String ticker, Duration ago) {
        CompanyProfile profile = new CompanyProfile(ticker);
        profile.updateProfile("Old name", null, null, Instant.now().minus(ago));
        return profile;
    }

    @Test
    void aNewUsCompany_getsItsNameAndLogo_andNonUsStocksAreNotAsked() {
        when(positionService.getAllPositions()).thenReturn(List.of(position("MU_US_EQ"), position("RWEd_EQ")));
        when(repository.findById("MU_US_EQ")).thenReturn(Optional.empty());
        when(finnhubClient.fetchCompanyProfile("MU")).thenReturn(new FinnhubCompanyProfileDto("Micron Technology, Inc.", LOGO_URL));
        when(finnhubClient.downloadLogo(LOGO_URL)).thenReturn(Optional.of(new LogoImage(PNG, "image/png")));
        Instant before = Instant.now();

        service.refreshProfiles();

        verify(finnhubClient, times(1)).fetchCompanyProfile(anyString());
        verify(repository).save(savedCaptor.capture());
        CompanyProfile saved = savedCaptor.getValue();
        assertEquals("MU_US_EQ", saved.getTicker());
        assertEquals("Micron Technology, Inc.", saved.getName());
        assertArrayEquals(PNG, saved.getLogo());
        assertEquals("image/png", saved.getLogoContentType());
        assertFalse(saved.getFetchedAt().isBefore(before));
    }

    @Test
    void aCompanyWithoutLogoLink_isSavedWithoutLogo_andNothingIsDownloaded() {
        when(positionService.getAllPositions()).thenReturn(List.of(position("XYZ_US_EQ")));
        when(repository.findById("XYZ_US_EQ")).thenReturn(Optional.empty());
        when(finnhubClient.fetchCompanyProfile("XYZ")).thenReturn(new FinnhubCompanyProfileDto("Xyz Corp", ""));

        service.refreshProfiles();

        verify(finnhubClient, never()).downloadLogo(anyString());
        verify(repository).save(savedCaptor.capture());
        assertEquals("Xyz Corp", savedCaptor.getValue().getName());
        assertFalse(savedCaptor.getValue().hasLogo());
        assertNull(savedCaptor.getValue().getLogoContentType());
    }

    @Test
    void anImageThatIsRejected_savesTheProfileWithoutLogo() {
        when(positionService.getAllPositions()).thenReturn(List.of(position("MU_US_EQ")));
        when(repository.findById("MU_US_EQ")).thenReturn(Optional.empty());
        when(finnhubClient.fetchCompanyProfile("MU")).thenReturn(new FinnhubCompanyProfileDto("Micron Technology, Inc.", LOGO_URL));
        when(finnhubClient.downloadLogo(LOGO_URL)).thenReturn(Optional.empty());

        service.refreshProfiles();

        verify(repository).save(savedCaptor.capture());
        assertFalse(savedCaptor.getValue().hasLogo());
        assertEquals("Micron Technology, Inc.", savedCaptor.getValue().getName());
    }

    @Test
    void anEmptyAnswerFromFinnhub_stillSavesTheAttempt() {
        when(positionService.getAllPositions()).thenReturn(List.of(position("MU_US_EQ")));
        when(repository.findById("MU_US_EQ")).thenReturn(Optional.empty());
        when(finnhubClient.fetchCompanyProfile("MU")).thenReturn(null);

        service.refreshProfiles();

        verify(repository).save(savedCaptor.capture());
        assertNull(savedCaptor.getValue().getName());
        assertFalse(savedCaptor.getValue().hasLogo());
        assertTrue(savedCaptor.getValue().getFetchedAt() != null);
    }

    @Test
    void aRecentProfile_isSkipped_evenWithoutLogo() {
        when(positionService.getAllPositions()).thenReturn(List.of(position("XYZ_US_EQ")));
        when(repository.findById("XYZ_US_EQ")).thenReturn(Optional.of(profileFetched("XYZ_US_EQ", Duration.ofDays(2))));

        service.refreshProfiles();

        verify(finnhubClient, never()).fetchCompanyProfile(anyString());
        verify(repository, never()).save(any());
    }

    @Test
    void aProfileOlderThan30Days_isRefreshedInTheSameRow() {
        CompanyProfile old = profileFetched("MU_US_EQ", Duration.ofDays(40));
        when(positionService.getAllPositions()).thenReturn(List.of(position("MU_US_EQ")));
        when(repository.findById("MU_US_EQ")).thenReturn(Optional.of(old));
        when(finnhubClient.fetchCompanyProfile("MU")).thenReturn(new FinnhubCompanyProfileDto("Micron Technology, Inc.", LOGO_URL));
        when(finnhubClient.downloadLogo(LOGO_URL)).thenReturn(Optional.of(new LogoImage(PNG, "image/png")));

        service.refreshProfiles();

        verify(repository).save(savedCaptor.capture());
        assertSame(old, savedCaptor.getValue());
        assertEquals("Micron Technology, Inc.", old.getName());
        assertTrue(old.hasLogo());
    }

    @Test
    void aFailingCompany_isNotSaved_andDoesNotStopTheOthers() {
        when(positionService.getAllPositions()).thenReturn(List.of(position("MU_US_EQ"), position("NVDA_US_EQ")));
        when(repository.findById(anyString())).thenReturn(Optional.empty());
        when(finnhubClient.fetchCompanyProfile("MU")).thenReturn(new FinnhubCompanyProfileDto("Micron Technology, Inc.", LOGO_URL));
        when(finnhubClient.downloadLogo(LOGO_URL)).thenThrow(new RuntimeException("404 Not Found"));
        when(finnhubClient.fetchCompanyProfile("NVDA")).thenReturn(new FinnhubCompanyProfileDto("NVIDIA Corp", ""));

        service.refreshProfiles();

        verify(repository, times(1)).save(savedCaptor.capture());
        assertEquals("NVDA_US_EQ", savedCaptor.getValue().getTicker());
    }
}
