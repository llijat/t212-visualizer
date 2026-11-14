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
    }

    @Test
    void updateProfile_copiesTheFourValues() {
        CompanyProfile profile = new CompanyProfile("MU_US_EQ");
        Instant now = Instant.parse("2026-11-14T10:00:00Z");

        profile.updateProfile("Micron Technology, Inc.", PNG, "image/png", now);

        assertEquals("Micron Technology, Inc.", profile.getName());
        assertArrayEquals(PNG, profile.getLogo());
        assertEquals("image/png", profile.getLogoContentType());
        assertEquals(now, profile.getFetchedAt());
        assertTrue(profile.hasLogo());
    }

    @Test
    void aCompanyWithoutLogo_keepsItsNameAndTheDateOfTheAttempt() {
        CompanyProfile profile = new CompanyProfile("XYZ_US_EQ");
        Instant now = Instant.parse("2026-11-14T10:00:00Z");

        profile.updateProfile("Xyz Corp", null, null, now);

        assertFalse(profile.hasLogo());
        assertNull(profile.getLogoContentType());
        assertEquals("Xyz Corp", profile.getName());
        assertEquals(now, profile.getFetchedAt());
    }

    @Test
    void aLaterUpdate_canRemoveALogoThatNoLongerExists() {
        CompanyProfile profile = new CompanyProfile("MU_US_EQ");
        profile.updateProfile("Micron Technology, Inc.", PNG, "image/png", Instant.parse("2026-10-14T10:00:00Z"));

        profile.updateProfile("Micron Technology, Inc.", null, null, Instant.parse("2026-11-14T10:00:00Z"));

        assertFalse(profile.hasLogo());
        assertEquals(Instant.parse("2026-11-14T10:00:00Z"), profile.getFetchedAt());
    }
}
