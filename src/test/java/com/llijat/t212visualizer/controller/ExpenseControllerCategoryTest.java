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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The two forms of a merchant's category: like the budgets, they always answer with a redirect (Post/Redirect/Get). */
@ExtendWith(MockitoExtension.class)
class ExpenseControllerCategoryTest {

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
        mockMvc = MockMvcBuilders.standaloneSetup(
                new ExpenseController(importService, summaryService, budgetService, merchantCategoryService)).build();
    }

    @Test
    void correctingAMerchant_inTheRunningMonth_redirectsBackToExpenses() throws Exception {
        mockMvc.perform(post("/expenses/categories").param("merchant", "Ferry Company").param("category", "TRANSPORT"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/expenses"));

        verify(merchantCategoryService).saveCategory("Ferry Company", "TRANSPORT");
    }

    @Test
    void correctingAMerchant_whileLookingAtAPastMonth_goesBackToThatMonth() throws Exception {
        mockMvc.perform(post("/expenses/categories").param("merchant", "Ferry Company").param("category", "TRANSPORT")
                        .param("month", "2026-08"))
                .andExpect(redirectedUrl("/expenses?month=2026-08"));
    }

    @Test
    void aMerchantNameWithSymbols_arrivesUntouched() throws Exception {
        // Merchant names come from T212 as they are: spaces, ampersands, accents...
        mockMvc.perform(post("/expenses/categories").param("merchant", "Café & Co. *Madrid").param("category", "RESTAURANTS"))
                .andExpect(redirectedUrl("/expenses"));

        verify(merchantCategoryService).saveCategory("Café & Co. *Madrid", "RESTAURANTS");
    }

    @Test
    void anEmptyCategory_isShownAfterTheRedirect_insteadOfAnErrorPage() throws Exception {
        // The path: the service throws, the controller catches it and keeps the message for the next page
        doThrow(new IllegalArgumentException("Choose a category."))
                .when(merchantCategoryService).saveCategory("Ferry Company", "");

        mockMvc.perform(post("/expenses/categories").param("merchant", "Ferry Company").param("category", ""))
                .andExpect(redirectedUrl("/expenses"))
                .andExpect(flash().attribute("categoryError", "Choose a category."));
    }

    @Test
    void withoutTheMerchant_theFormIsRejected_beforeReachingTheService() throws Exception {
        mockMvc.perform(post("/expenses/categories").param("category", "TRANSPORT"))
                .andExpect(status().isBadRequest());

        verify(merchantCategoryService, never()).saveCategory(anyString(), anyString());
    }

    @Test
    void goingBackToTheT212Category_redirectsBackToTheSameMonth() throws Exception {
        mockMvc.perform(post("/expenses/categories/reset").param("merchant", "Ferry Company").param("month", "2026-08"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/expenses?month=2026-08"));

        verify(merchantCategoryService).resetCategory("Ferry Company");
    }

    @Test
    void aLinkCannotChangeOrResetACategory() throws Exception {
        // Only POST: a link opened by mistake (or by another site) must not change anything
        mockMvc.perform(get("/expenses/categories").param("merchant", "Ferry Company").param("category", "TRANSPORT"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(get("/expenses/categories/reset").param("merchant", "Ferry Company"))
                .andExpect(status().isMethodNotAllowed());

        verify(merchantCategoryService, never()).saveCategory(anyString(), anyString());
        verify(merchantCategoryService, never()).resetCategory(anyString());
    }
}
