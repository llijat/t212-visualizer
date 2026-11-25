package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.model.MerchantCategory;
import com.llijat.t212visualizer.repository.MerchantCategoryRepository;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MerchantCategoryServiceTest {

    @Mock
    private MerchantCategoryRepository repository;

    @Captor
    private ArgumentCaptor<MerchantCategory> savedCaptor;

    private MerchantCategoryService service;

    @BeforeEach
    void setUp() {
        service = new MerchantCategoryService(repository);
    }

    private static MerchantCategory correction(String merchant, String category) {
        MerchantCategory correction = new MerchantCategory(merchant);
        correction.changeCategory(category);
        return correction;
    }

    // ===== Saving a correction =====

    @Test
    void aMerchantWithoutCorrection_getsANewOne() {
        when(repository.findById("Ferry Company")).thenReturn(Optional.empty());

        service.saveCategory("Ferry Company", "TRANSPORT");

        verify(repository).save(savedCaptor.capture());
        assertEquals("Ferry Company", savedCaptor.getValue().getMerchantName());
        assertEquals("TRANSPORT", savedCaptor.getValue().getCategory());
    }

    @Test
    void correctingTheSameMerchantAgain_changesTheSameRow() {
        // One row per merchant: the second correction replaces the first one
        MerchantCategory existing = correction("Ferry Company", "TRANSPORT");
        when(repository.findById("Ferry Company")).thenReturn(Optional.of(existing));

        service.saveCategory("Ferry Company", "TRAVEL");

        verify(repository).save(savedCaptor.capture());
        assertSame(existing, savedCaptor.getValue());
        assertEquals("TRAVEL", savedCaptor.getValue().getCategory());
    }

    @Test
    void anEmptyMerchant_isRejected_andNothingIsSaved() {
        assertThrows(IllegalArgumentException.class, () -> service.saveCategory(null, "TRANSPORT"));
        assertThrows(IllegalArgumentException.class, () -> service.saveCategory("  ", "TRANSPORT"));
        verify(repository, never()).save(any());
    }

    @Test
    void anEmptyCategory_isRejected_andNothingIsSaved() {
        assertThrows(IllegalArgumentException.class, () -> service.saveCategory("Ferry Company", null));
        assertThrows(IllegalArgumentException.class, () -> service.saveCategory("Ferry Company", ""));
        verify(repository, never()).save(any());
    }

    @Test
    void aCategoryLongerThanTheColumn_isRejected() {
        // The column is varchar(255): better a clear message than a database error
        assertThrows(IllegalArgumentException.class, () -> service.saveCategory("Ferry Company", "X".repeat(256)));
        verify(repository, never()).save(any());
    }

    @Test
    void aCategoryOfExactly255Characters_isAccepted() {
        when(repository.findById("Ferry Company")).thenReturn(Optional.empty());

        service.saveCategory("Ferry Company", "X".repeat(255));

        verify(repository).save(any());
    }

    // ===== Going back to the T212 category =====

    @Test
    void resetting_deletesTheCorrection() {
        service.resetCategory("Ferry Company");

        verify(repository).deleteById("Ferry Company");
    }

    // ===== Reading them all =====

    @Test
    void theCorrections_comeAsMerchantToCategory() {
        when(repository.findAll()).thenReturn(List.of(
                correction("Ferry Company", "TRANSPORT"),
                correction("Corner Bistro", "RESTAURANTS")));

        Map<String, String> corrections = service.getCorrections();

        assertEquals(Map.of("Ferry Company", "TRANSPORT", "Corner Bistro", "RESTAURANTS"), corrections);
    }

    @Test
    void withoutCorrections_theMapIsEmpty() {
        when(repository.findAll()).thenReturn(List.of());

        assertTrue(service.getCorrections().isEmpty());
    }
}
