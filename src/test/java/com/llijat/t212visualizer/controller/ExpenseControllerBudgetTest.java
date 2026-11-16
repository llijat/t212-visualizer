package com.llijat.t212visualizer.controller;

import com.llijat.t212visualizer.service.BudgetService;
import com.llijat.t212visualizer.service.CardExpenseImportService;
import com.llijat.t212visualizer.service.CardExpenseSummaryService;
import com.llijat.t212visualizer.service.MerchantCategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The two budget forms: they always answer with a redirect (Post/Redirect/Get). */
@ExtendWith(MockitoExtension.class)
class ExpenseControllerBudgetTest {

    @Mock
    private CardExpenseImportService importService;

    @Mock
    private CardExpenseSummaryService summaryService;

    @Mock
    private BudgetService budgetService;

    @Mock
    private MerchantCategoryService merchantCategoryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ExpenseController(importService, summaryService, budgetService, merchantCategoryService)).build();
    }

    @Test
    void savingABudget_inTheRunningMonth_redirectsBackToExpenses() throws Exception {
        mockMvc.perform(post("/expenses/budgets").param("category", "RESTAURANTS").param("monthlyLimit", "200"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/expenses"));

        verify(budgetService).saveBudget("RESTAURANTS", new BigDecimal("200"));
    }

    @Test
    void savingABudget_whileLookingAtAPastMonth_goesBackToThatMonth() throws Exception {
        mockMvc.perform(post("/expenses/budgets").param("category", "RESTAURANTS").param("monthlyLimit", "150.50")
                        .param("month", "2026-09"))
                .andExpect(redirectedUrl("/expenses?month=2026-09"));

        verify(budgetService).saveBudget("RESTAURANTS", new BigDecimal("150.50"));
    }

    @Test
    void anInvalidLimit_isShownAfterTheRedirect_insteadOfAnErrorPage() throws Exception {
        doThrow(new IllegalArgumentException("The monthly limit must be more than 0."))
                .when(budgetService).saveBudget("RESTAURANTS", new BigDecimal("-5"));

        mockMvc.perform(post("/expenses/budgets").param("category", "RESTAURANTS").param("monthlyLimit", "-5"))
                .andExpect(redirectedUrl("/expenses"))
                .andExpect(flash().attribute("budgetError", "The monthly limit must be more than 0."));
    }

    @Test
    void aLimitThatIsNotANumber_isRejectedBeforeReachingTheService() throws Exception {
        mockMvc.perform(post("/expenses/budgets").param("category", "RESTAURANTS").param("monthlyLimit", "lots"))
                .andExpect(status().isBadRequest());

        verify(budgetService, never()).saveBudget(anyString(), any());
    }

    @Test
    void deletingABudget_redirectsBackToTheSameMonth() throws Exception {
        mockMvc.perform(post("/expenses/budgets/delete").param("category", "RESTAURANTS").param("month", "2026-09"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/expenses?month=2026-09"));

        verify(budgetService).deleteBudget("RESTAURANTS");
    }
}
