package com.llijat.t212visualizer.controller;

import com.llijat.t212visualizer.service.AccountService;
import com.llijat.t212visualizer.service.PositionService;
import com.llijat.t212visualizer.service.NewsService;
import com.llijat.t212visualizer.service.TransactionService;
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


    public DashboardController(PositionService positionService, NewsService newsService, AccountService accountService, TransactionService transactionService) {
        this.positionService = positionService;
        this.newsService = newsService;
        this.accountService = accountService;
        this.transactionService = transactionService;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("positions", positionService.getAllPositions());
        model.addAttribute("news", newsService.getNews());
        model.addAttribute("portfolioNews", newsService.getPortfolioNews());
        model.addAttribute("portfolioTickers", newsService.getPortfolioTickers());
        model.addAttribute("summary", accountService.getSummary());
        model.addAttribute("netDeposits", transactionService.getNetDeposits());
        return "dashboard"; // resolves to templates/dashboard.html
    }

    /** Handy while you're developing: trigger a refresh without waiting for the schedule. */
    @PostMapping("/api/refresh")
    public ResponseEntity<String> refreshNow() {
        try {
            positionService.refreshFromT212();
            accountService.refreshSummary();
            return ResponseEntity.ok("ok");
        }
        catch (Exception e) {
            return ResponseEntity.status(502).body(e.getMessage());
        }
    }
}
