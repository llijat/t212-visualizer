package com.llijat.t212visualizer.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyProfileTest {

    // The first bytes of every PNG file
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};

    @Test
    void newProfile_hasOnlyItsTicker() {
        CompanyProfile profile = new CompanyProfile("MU_US_EQ");

        assertEquals("MU_US_EQ", profile.getTicker());
        assertNull(profile.getName());
        assertNull(profile.getFetchedAt());
        assertFalse(profile.hasLogo());
        assertNull(profile.getSymbol());
        assertNull(profile.getExchange());
    }

    @Test
    void updateProfile_copiesTheFiveValues() {
        CompanyProfile profile = new CompanyProfile("MU_US_EQ");
        Instant now = Instant.parse("2026-11-14T10:00:00Z");

        profile.updateProfile("Micron Technology, Inc.", PNG, "image/png", now, "Semiconductors");

        assertEquals("Micron Technology, Inc.", profile.getName());
        assertEquals("Semiconductors", profile.getIndustry());
        assertArrayEquals(PNG, profile.getLogo());
        assertEquals("image/png", profile.getLogoContentType());
        assertEquals(now, profile.getFetchedAt());
        assertTrue(profile.hasLogo());
    }

    @Test
    void aCompanyWithoutLogo_keepsItsNameAndTheDateOfTheAttempt() {
        CompanyProfile profile = new CompanyProfile("XYZ_US_EQ");
        Instant now = Instant.parse("2026-11-14T10:00:00Z");

        profile.updateProfile("Xyz Corp", null, null, now, null);

        assertFalse(profile.hasLogo());
        assertNull(profile.getLogoContentType());
        assertEquals("Xyz Corp", profile.getName());
        assertEquals(now, profile.getFetchedAt());
    }

    @Test
    void aLaterUpdate_canRemoveALogoThatNoLongerExists() {
        CompanyProfile profile = new CompanyProfile("MU_US_EQ");
        profile.updateProfile("Micron Technology, Inc.", PNG, "image/png", Instant.parse("2026-10-14T10:00:00Z"), null);

        profile.updateProfile("Micron Technology, Inc.", null, null, Instant.parse("2026-11-14T10:00:00Z"), null);

        assertFalse(profile.hasLogo());
        assertEquals(Instant.parse("2026-11-14T10:00:00Z"), profile.getFetchedAt());
    }

    @Test
    void updateListing_savesTheCurrentTickerAndItsExchange() {
        // T212 still calls Meta by its old ticker; OpenFIGI gives the current one
        CompanyProfile profile = new CompanyProfile("FB_US_EQ");

        profile.updateListing("META", "UW");

        assertEquals("FB_US_EQ", profile.getTicker());
        assertEquals("META", profile.getSymbol());
        assertEquals("UW", profile.getExchange());
    }

    @Test
    void updateListing_andUpdateProfile_doNotTouchEachOther() {
        // They come from different sources: refreshing one must keep the other
        CompanyProfile profile = new CompanyProfile("FB_US_EQ");
        Instant now = Instant.parse("2026-11-15T10:00:00Z");
        profile.updateProfile("Meta Platforms Inc", PNG, "image/png", now, null);

        profile.updateListing("META", "UW");
        assertEquals("Meta Platforms Inc", profile.getName());
        assertTrue(profile.hasLogo());
        assertEquals(now, profile.getFetchedAt());

        profile.updateProfile("Meta Platforms, Inc.", null, null, now.plusSeconds(60), null);
        assertEquals("META", profile.getSymbol());
        assertEquals("UW", profile.getExchange());
    }

    @Test
    void aListingThatIsNoLongerFound_canBeCleared() {
        CompanyProfile profile = new CompanyProfile("FB_US_EQ");
        profile.updateListing("META", "UW");

        profile.updateListing(null, null);

        assertNull(profile.getSymbol());
        assertNull(profile.getExchange());
    }
}
