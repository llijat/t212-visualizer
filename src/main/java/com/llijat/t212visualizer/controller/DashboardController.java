package com.llijat.t212visualizer.controller;

import com.llijat.t212visualizer.service.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping; 
import org.springframework.http.ResponseEntity;

@Controller
public class DashboardController {

    private final PositionService positionService;
    private final NewsService newsService;
    private final AccountService accountService;
    private final TransactionService transactionService;
    private final CompanyProfileService companyProfileService;


    public DashboardController(PositionService positionService, NewsService newsService, AccountService accountService, TransactionService transactionService, CompanyProfileService companyProfileService) {
        this.positionService = positionService;
        this.newsService = newsService;
        this.accountService = accountService;
        this.transactionService = transactionService;
        this.companyProfileService = companyProfileService;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("positions", positionService.getAllPositions());
        model.addAttribute("news", newsService.getNews());
        model.addAttribute("portfolioNews", newsService.getPortfolioNews());
        model.addAttribute("portfolioTickers", newsService.getPortfolioTickers());
        model.addAttribute("summary", accountService.getSummary());
        model.addAttribute("netDeposits", transactionService.getNetDeposits());
        model.addAttribute("firstSyncDone", transactionService.isFirstSyncDone());
        model.addAttribute("symbols", companyProfileService.currentSymbols());
        return "dashboard"; // resolves to templates/dashboard.html
    }

    /** Handy while you're developing: trigger a refresh without waiting for the schedule. */
    @PostMapping("/api/refresh")
    public ResponseEntity<String> refreshNow() {
        try {
            positionService.refreshFromT212();
            accountService.refreshSummary();
            transactionService.syncTransactions();
            return ResponseEntity.ok("ok");
        }
        catch (Exception e) {
            return ResponseEntity.status(502).body(e.getMessage());
        }
    }
}
