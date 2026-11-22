package com.llijat.t212visualizer.controller;

import com.llijat.t212visualizer.service.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping; 
import org.springframework.http.ResponseEntity;

import java.time.Clock;
import java.time.LocalDate;

@Controller
public class DashboardController {
    private final Clock clock;

    private final PositionService positionService;
    private final NewsService newsService;
    private final AccountService accountService;
    private final TransactionService transactionService;
    private final CompanyProfileService companyProfileService;
    private final QuoteService quoteService;
    private final CalendarService calendarService;
    private final DividendHistoryService dividendHistoryService;
    private final AccountSnapshotService accountSnapshotService;


    public DashboardController(PositionService positionService, NewsService newsService, AccountService accountService, TransactionService transactionService, CompanyProfileService companyProfileService, QuoteService quoteService, CalendarService calendarService, DividendHistoryService dividendHistoryService, AccountSnapshotService accountSnapshotService, Clock clock) {
        this.positionService = positionService;
        this.newsService = newsService;
        this.accountService = accountService;
        this.transactionService = transactionService;
        this.companyProfileService = companyProfileService;
        this.quoteService = quoteService;
        this.calendarService = calendarService;
        this.dividendHistoryService = dividendHistoryService;
        this.accountSnapshotService = accountSnapshotService;
        this.clock = clock;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("positions", positionService.getAllPositions());
        model.addAttribute("portfolioNews", newsService.getPortfolioNews());
        model.addAttribute("summary", accountService.getSummary());
        model.addAttribute("netDeposits", transactionService.getNetDeposits());
        model.addAttribute("firstSyncDone", transactionService.isFirstSyncDone());
        model.addAttribute("symbols", companyProfileService.currentSymbols());
        model.addAttribute("moves", quoteService.getMoves());
        model.addAttribute("comingUp", calendarService.getComingUp());
        model.addAttribute("today", LocalDate.now(clock));
        model.addAttribute("metals", quoteService.getMetals());
        model.addAttribute("dividendHistory", dividendHistoryService.getLast12Months());
        model.addAttribute("tickers", newsService.getTickerBySymbol());
        model.addAttribute("todayChange", accountSnapshotService.getTodayChange());
        model.addAttribute("history", accountSnapshotService.getHistory());
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
