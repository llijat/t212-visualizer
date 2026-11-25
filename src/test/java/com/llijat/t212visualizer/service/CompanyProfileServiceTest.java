package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.FinnhubCompanyProfileDto;
import com.llijat.t212visualizer.dto.LogoImage;
import com.llijat.t212visualizer.dto.OpenFigiListingDto;
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
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyProfileServiceTest {

    private static final String LOGO_URL = "https://static.example.test/logos/MU.png";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};

    private static final String META_ISIN = "US30303M1027";
    private static final String RWE_ISIN = "DE0007037129";

    @Mock
    private FinnhubClient finnhubClient;

    @Mock
    private PositionService positionService;

    @Mock
    private CompanyProfileRepository repository;

    // Without stubbing, it answers an empty Map: OpenFIGI knows none of the ISINs
    @Mock
    private OpenFigiClient openFigiClient;

    @Captor
    private ArgumentCaptor<CompanyProfile> savedCaptor;

    private CompanyProfileService service;

    @BeforeEach
    void setUp() {
        service = new CompanyProfileService(finnhubClient, positionService, repository, openFigiClient);
    }

    // Only the ticker and the ISIN matter to the service; the figures are invented
    private static Position position(String ticker, String isin) {
        BigDecimal one = BigDecimal.ONE;
        return new Position(ticker, ticker + " Inc.", isin, one, one, one, one, BigDecimal.ZERO, null, one, "USD", "EUR");
    }

    private static Position position(String ticker) {
        return position(ticker, "US0000000001");
    }

    private static OpenFigiListingDto listing(String ticker, String exchCode) {
        return new OpenFigiListingDto(ticker, exchCode, "Common Stock", "SOME COMPANY");
    }

    private static CompanyProfile profileFetched(String ticker, Duration ago) {
        CompanyProfile profile = new CompanyProfile(ticker);
        profile.updateProfile("Old name", null, null, Instant.now().minus(ago));
        return profile;
    }

    @Test
    void aNewUsCompany_getsItsNameAndLogo_andNonUsStocksAreNotAskedToFinnhub() {
        when(positionService.getAllPositions()).thenReturn(List.of(position("MU_US_EQ"), position("RWEd_EQ", RWE_ISIN)));
        when(repository.findById(anyString())).thenReturn(Optional.empty());
        when(finnhubClient.fetchCompanyProfile("MU")).thenReturn(new FinnhubCompanyProfileDto("Micron Technology, Inc.", LOGO_URL));
        when(finnhubClient.downloadLogo(LOGO_URL)).thenReturn(Optional.of(new LogoImage(PNG, "image/png")));
        Instant before = Instant.now();

        service.refreshProfiles();

        verify(finnhubClient, times(1)).fetchCompanyProfile(anyString());
        verify(repository, times(2)).save(savedCaptor.capture());
        CompanyProfile saved = savedCaptor.getAllValues().get(0);
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

        verify(openFigiClient, never()).findListings(anyList());
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

    @Test
    void meta_isAskedToFinnhubByItsCurrentTicker_andSavesItWithItsExchange() {
        // T212 still calls it FB; OpenFIGI knows it is META on Nasdaq (UW)
        when(positionService.getAllPositions()).thenReturn(List.of(position("FB_US_EQ", META_ISIN)));
        when(repository.findById("FB_US_EQ")).thenReturn(Optional.empty());
        when(openFigiClient.findListings(List.of(META_ISIN)))
                .thenReturn(Map.of(META_ISIN, List.of(listing("META", "UN"), listing("META", "UW"), listing("FB2A", "GY"))));
        when(finnhubClient.fetchCompanyProfile("META")).thenReturn(new FinnhubCompanyProfileDto("Meta Platforms Inc", LOGO_URL));
        when(finnhubClient.downloadLogo(LOGO_URL)).thenReturn(Optional.of(new LogoImage(PNG, "image/png")));

        service.refreshProfiles();

        verify(finnhubClient, never()).fetchCompanyProfile("FB");
        verify(repository).save(savedCaptor.capture());
        CompanyProfile saved = savedCaptor.getValue();
        assertEquals("FB_US_EQ", saved.getTicker());
        assertEquals("META", saved.getSymbol());
        assertEquals("UW", saved.getExchange());
        assertEquals("Meta Platforms Inc", saved.getName());
        assertTrue(saved.hasLogo());
    }

    @Test
    void aEuropeanStock_getsItsTickerAndExchange_withoutAskingFinnhub() {
        when(positionService.getAllPositions()).thenReturn(List.of(position("RWEd_EQ", RWE_ISIN)));
        when(repository.findById("RWEd_EQ")).thenReturn(Optional.empty());
        when(openFigiClient.findListings(List.of(RWE_ISIN)))
                .thenReturn(Map.of(RWE_ISIN, List.of(listing("RWE", "GF"), listing("RWE", "GY"))));

        service.refreshProfiles();

        verify(finnhubClient, never()).fetchCompanyProfile(anyString());
        verify(repository).save(savedCaptor.capture());
        CompanyProfile saved = savedCaptor.getValue();
        assertEquals("RWE", saved.getSymbol());
        assertEquals("GY", saved.getExchange());
        assertNull(saved.getName());
        assertFalse(saved.hasLogo());
        assertTrue(saved.getFetchedAt() != null);
    }

    @Test
    void ifOpenFigiDoesNotKnowTheStock_finnhubIsStillAskedWithTheT212Ticker() {
        // What already worked before OpenFIGI must keep working
        when(positionService.getAllPositions()).thenReturn(List.of(position("MU_US_EQ")));
        when(repository.findById("MU_US_EQ")).thenReturn(Optional.empty());
        when(finnhubClient.fetchCompanyProfile("MU")).thenReturn(new FinnhubCompanyProfileDto("Micron Technology, Inc.", ""));

        service.refreshProfiles();

        verify(repository).save(savedCaptor.capture());
        assertEquals("Micron Technology, Inc.", savedCaptor.getValue().getName());
        assertNull(savedCaptor.getValue().getSymbol());
        assertNull(savedCaptor.getValue().getExchange());
    }

    @Test
    void ifOpenFigiFails_nothingIsRefreshedToday() {
        // Saving anyway would stamp today's date and hide the old ticker for 30 days
        when(positionService.getAllPositions()).thenReturn(List.of(position("FB_US_EQ", META_ISIN), position("RWEd_EQ", RWE_ISIN)));
        when(repository.findById(anyString())).thenReturn(Optional.empty());
        when(openFigiClient.findListings(anyList())).thenThrow(new RuntimeException("429 Too Many Requests"));

        service.refreshProfiles();

        verify(finnhubClient, never()).fetchCompanyProfile(anyString());
        verify(repository, never()).save(any());
    }

    @Test
    void openFigiIsAskedOnce_onlyForTheProfilesThatAreDue() {
        // MU was refreshed two days ago; the other two are new
        when(positionService.getAllPositions())
                .thenReturn(List.of(position("MU_US_EQ", "US5951121038"), position("FB_US_EQ", META_ISIN), position("RWEd_EQ", RWE_ISIN)));
        when(repository.findById("MU_US_EQ")).thenReturn(Optional.of(profileFetched("MU_US_EQ", Duration.ofDays(2))));
        when(repository.findById("FB_US_EQ")).thenReturn(Optional.empty());
        when(repository.findById("RWEd_EQ")).thenReturn(Optional.empty());

        service.refreshProfiles();

        verify(openFigiClient, times(1)).findListings(anyList());
        verify(openFigiClient).findListings(List.of(META_ISIN, RWE_ISIN));
        verify(repository, times(2)).save(any());
    }

    @Test
    void aPositionWithoutIsin_isNotSentToOpenFigi_butIsStillRefreshed() {
        // Like a row saved before V7, until the next T212 refresh fills its ISIN
        when(positionService.getAllPositions()).thenReturn(List.of(position("MU_US_EQ", null)));
        when(repository.findById("MU_US_EQ")).thenReturn(Optional.empty());
        when(finnhubClient.fetchCompanyProfile("MU")).thenReturn(new FinnhubCompanyProfileDto("Micron Technology, Inc.", ""));

        service.refreshProfiles();

        verify(openFigiClient).findListings(List.of());
        verify(repository).save(savedCaptor.capture());
        assertEquals("Micron Technology, Inc.", savedCaptor.getValue().getName());
    }

    @Test
    void currentSymbols_mapsEachT212TickerToItsCurrentOne_leavingOutTheUnresolved() {
        // MU has no resolved ticker: its key must be missing, not null,
        // so that getOrDefault falls back to the T212 ticker
        CompanyProfile meta = new CompanyProfile("FB_US_EQ");
        meta.updateListing("META", "UW");
        CompanyProfile rwe = new CompanyProfile("RWEd_EQ");
        rwe.updateListing("RWE", "GY");
        CompanyProfile mu = new CompanyProfile("MU_US_EQ");
        when(repository.findAll()).thenReturn(List.of(meta, rwe, mu));

        Map<String, String> symbols = service.currentSymbols();

        assertEquals(Map.of("FB_US_EQ", "META", "RWEd_EQ", "RWE"), symbols);
        assertFalse(symbols.containsKey("MU_US_EQ"));
        assertEquals("MU", symbols.getOrDefault("MU_US_EQ", "MU"));
    }

    @Test
    void currentTradingViewSymbols_putTheExchangeInFront_asTradingViewNamesIt() {
        CompanyProfile meta = new CompanyProfile("FB_US_EQ");
        meta.updateListing("META", "UW");
        CompanyProfile rwe = new CompanyProfile("RWEd_EQ");
        rwe.updateListing("RWE", "GY");
        CompanyProfile mu = new CompanyProfile("MU_US_EQ");   // not resolved: the page falls back to its own symbol
        when(repository.findAll()).thenReturn(List.of(meta, rwe, mu));

        assertEquals(Map.of("FB_US_EQ", "NASDAQ:META", "RWEd_EQ", "XETR:RWE"), service.currentTradingViewSymbols());
    }
}
