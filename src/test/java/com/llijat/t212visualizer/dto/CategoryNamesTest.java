package com.llijat.t212visualizer.dto;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CategoryNamesTest {

    private static CategoryNames names() {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("RESTAURANTS", "Restaurants");
        names.put("TRANSPORT", "Transport");
        names.put("Gym & Padel", "Gym & Padel");
        return new CategoryNames(names);
    }

    @Test
    void aT212Key_becomesAName_withOnlyTheFirstLetterInCapitals() {
        assertEquals("Restaurants", CategoryNames.fromKey("RESTAURANTS"));
        assertEquals("Automobile rental", CategoryNames.fromKey("AUTOMOBILE_RENTAL"));
    }

    @Test
    void yourCategories_keepTheNameAsYouWroteIt() {
        assertEquals("Gym & Padel", names().of("Gym & Padel"));   // not "Gym & padel"
    }

    @Test
    void aCategoryT212AddsOneDay_stillGetsAReadableName() {
        assertEquals("Pet stores", names().of("PET_STORES"));
    }

    @Test
    void theChoices_keepTheirOrder_T212sFirstAndThenYours() {
        assertEquals(List.of("RESTAURANTS", "TRANSPORT", "Gym & Padel"), List.copyOf(names().choices()));
    }

    @Test
    void anI_staysAnI_whateverTheLanguageOfTheComputer() {
        Locale before = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));   // in Turkish, "I".toLowerCase() is a dotless "ı"
            assertEquals("Hotels", CategoryNames.fromKey("HOTELS"));
        } finally {
            Locale.setDefault(before);
        }
    }
}
