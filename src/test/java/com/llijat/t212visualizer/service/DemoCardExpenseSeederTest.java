package com.llijat.t212visualizer.service;

import java.time.ZoneId;

import com.llijat.t212visualizer.model.CardExpense;
import com.llijat.t212visualizer.model.CardCashback;
import com.llijat.t212visualizer.model.MerchantCategory;
import com.llijat.t212visualizer.repository.CardCashbackRepository;
import com.llijat.t212visualizer.repository.CardExpenseRepository;
import com.llijat.t212visualizer.repository.CustomCategoryRepository;
import com.llijat.t212visualizer.repository.MerchantCategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoCardExpenseSeederTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 15);

    @Mock
    private CardExpenseRepository repository;

    @Mock
    private CardCashbackRepository cashbackRepository;

    @Mock
    private CustomCategoryRepository customCategoryRepository;

    @Mock
    private MerchantCategoryRepository merchantCategoryRepository;

    private DemoCardExpenseSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new DemoCardExpenseSeeder(repository, cashbackRepository, customCategoryRepository, merchantCategoryRepository);
    }

    @Test
    void buildExpenses_coversTheLastTwelveMonths_withoutFuturePayments() {
        // Act
        List<CardExpense> expenses = seeder.buildExpenses(TODAY);

        // Assert
        Set<YearMonth> months = new HashSet<>();
        for (CardExpense expense : expenses) {
            months.add(YearMonth.from(expense.getDateTime().atZone(ZoneOffset.UTC)));
            assertFalse(expense.getDateTime().isAfter(Instant.parse("2026-11-15T23:59:59Z")));
        }
        assertEquals(12, months.size());
        assertTrue(months.contains(YearMonth.of(2025, 12)));
        assertTrue(months.contains(YearMonth.of(2026, 11)));
    }

    @Test
    void buildExpenses_looksLikeTheImportedPayments() {
        // Act
        List<CardExpense> expenses = seeder.buildExpenses(TODAY);

        // Assert: same action and sign as the real ones, and the summary needs a merchant and a category
        Set<String> references = new HashSet<>();
        for (CardExpense expense : expenses) {
            references.add(expense.getReference());
            assertEquals("Card debit", expense.getAction());
            assertEquals(-1, expense.getAmount().signum());
            assertFalse(expense.getMerchantName().isBlank());
            assertFalse(expense.getMerchantCategory().isBlank());
        }
        assertEquals(expenses.size(), references.size());
    }

    @Test
    void run_savesThePayments_whenTheTableIsEmpty() {
        // Arrange
        when(repository.count()).thenReturn(0L);

        // Act
        seeder.run(new DefaultApplicationArguments());

        // Assert
        verify(repository).saveAll(anyList());
    }

    @Test
    void run_doesNothing_whenThereAreAlreadyPayments() {
        // Arrange
        when(repository.count()).thenReturn(5L);

        // Act
        seeder.run(new DefaultApplicationArguments());

        // Assert
        verify(repository, never()).saveAll(any());
    }

    @Test
    void theDemo_hasSubscriptionsToShow_oneOfThemWithAPriceRise() {
        // Whatever day the demo starts, its fixed-price payments must pass the detector's rules
        for (LocalDate today = LocalDate.of(2026, 1, 1); today.isBefore(LocalDate.of(2027, 1, 1)); today = today.plusDays(1)) {
            List<com.llijat.t212visualizer.dto.Subscription> found = SubscriptionDetector.detect(seeder.buildExpenses(today), today, MADRID);

            assertEquals(4, found.size(), "on " + today + " found " + found);
            assertEquals(1, found.stream().filter(com.llijat.t212visualizer.dto.Subscription::priceChanged).count(), "on " + today);
        }
    }

    @Test
    void theDemoCashback_isASmallPositiveShare_paidTheNightAfter_andNeverInTheFuture() {
        List<CardExpense> expenses = seeder.buildExpenses(TODAY);

        List<CardCashback> cashback = seeder.buildCashback(expenses, TODAY);

        assertFalse(cashback.isEmpty());
        for (CardCashback c : cashback) {
            assertEquals(1, c.getAmount().signum());
            assertEquals(1, c.getDateTime().atZone(ZoneOffset.UTC).getHour());
            assertFalse(c.getDateTime().isAfter(Instant.parse("2026-11-15T23:59:59Z")));
        }
        assertEquals(cashback.size(), cashback.stream().map(CardCashback::getReference).distinct().count());
    }

    @Test
    void theMerchantsMovedToYourCategories_arePaymentsOfTheDemo() {
        List<String> merchants = seeder.buildExpenses(TODAY).stream().map(CardExpense::getMerchantName).distinct().toList();

        for (MerchantCategory moved : seeder.buildMerchantsMoved()) {
            assertTrue(merchants.contains(moved.getMerchantName()), moved.getMerchantName() + " has no payments in the demo");
        }
    }
}
