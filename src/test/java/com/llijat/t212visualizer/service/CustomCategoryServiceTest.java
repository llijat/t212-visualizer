package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.CategoryNames;
import com.llijat.t212visualizer.model.CustomCategory;
import com.llijat.t212visualizer.repository.BudgetRepository;
import com.llijat.t212visualizer.repository.CustomCategoryRepository;
import com.llijat.t212visualizer.repository.MerchantCategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomCategoryServiceTest {

    @Mock
    private CustomCategoryRepository customCategoryRepository;
    @Mock
    private MerchantCategoryRepository merchantCategoryRepository;
    @Mock
    private BudgetRepository budgetRepository;

    private CustomCategoryService service;

    @BeforeEach
    void setUp() {
        service = new CustomCategoryService(customCategoryRepository, merchantCategoryRepository, budgetRepository);
    }

    // ===== Creating one =====

    @Test
    void aNewCategory_isSaved_withTheSpacesAroundItTrimmed() {
        assertEquals("Gym & Padel", service.create("  Gym & Padel "));

        ArgumentCaptor<CustomCategory> saved = ArgumentCaptor.forClass(CustomCategory.class);
        verify(customCategoryRepository).save(saved.capture());
        assertEquals("Gym & Padel", saved.getValue().getName());
        assertNotNull(saved.getValue().getCreatedAt());
    }

    @Test
    void theNameIsKeptAsItIsWritten() {
        assertEquals("gym", service.create("gym"));   // no capitals added: the page shows exactly what was written
    }

    @Test
    void anEmptyName_isRefused() {
        assertThrows(IllegalArgumentException.class, () -> service.create(null));
        assertThrows(IllegalArgumentException.class, () -> service.create("   "));
        verify(customCategoryRepository, never()).save(any());
    }

    @Test
    void aNameOver100Characters_isRefused_becauseABudgetCouldNotHoldIt() {
        assertThrows(IllegalArgumentException.class, () -> service.create("x".repeat(101)));
        verify(customCategoryRepository, never()).save(any());
    }

    @Test
    void exactly100Characters_isAllowed() {
        service.create("x".repeat(100));

        verify(customCategoryRepository).save(any());
    }

    @Test
    void aNameThatAlreadyExists_isRefused_insteadOfOverwritingIt() {
        when(customCategoryRepository.existsById("Gym")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.create(" Gym "));   // checked once trimmed
        verify(customCategoryRepository, never()).save(any());
    }

    @Test
    void aNameThatIsAlreadyAT212Category_isRefused() {
        assertThrows(IllegalArgumentException.class, () -> service.create("RESTAURANTS"));
        verify(customCategoryRepository, never()).save(any());
    }

    // ===== Listing =====

    @Test
    void theList_isInTheOrderTheyWereCreated() {
        List<CustomCategory> categories = List.of(new CustomCategory("Gym"), new CustomCategory("Groceries"));
        when(customCategoryRepository.findAllByOrderByCreatedAtAsc()).thenReturn(categories);

        assertSame(categories, service.getAll());
    }

    // ===== Deleting one =====

    @Test
    void deletingACategory_givesItsMerchantsBackToT212_andRemovesItsBudget() {
        service.delete("Gym");

        // The category goes last: its merchants and its budget never point at a category that no longer exists
        InOrder order = inOrder(merchantCategoryRepository, budgetRepository, customCategoryRepository);
        order.verify(merchantCategoryRepository).deleteByCategory("Gym");
        order.verify(budgetRepository).deleteById("Gym");
        order.verify(customCategoryRepository).deleteById("Gym");
    }

    // ===== The names shown =====

    @Test
    void theNames_areT212sFirst_andThenYours_asYouWroteThem() {
        when(customCategoryRepository.findAllByOrderByCreatedAtAsc()).thenReturn(List.of(new CustomCategory("Gym & Padel")));

        CategoryNames names = service.getCategoryNames();

        List<String> choices = List.copyOf(names.choices());
        assertEquals("AUTOMOBILE", choices.get(0));
        assertEquals("Gym & Padel", choices.get(choices.size() - 1));
        assertEquals("Automobile rental", names.of("AUTOMOBILE_RENTAL"));
        assertEquals("Gym & Padel", names.of("Gym & Padel"));
    }
}
