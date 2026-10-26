package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.T212TransactionDto;
import com.llijat.t212visualizer.dto.T212TransactionPageDto;
import com.llijat.t212visualizer.model.AccountTransaction;
import com.llijat.t212visualizer.repository.AccountTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class TransactionService {
    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private static final String FIRST_PAGE = "/equity/history/transactions?limit=50";
    private static final String API_PREFIX = "/api/v0";
    private static final double CASHBACK_THRESHOLD = 5.0;

    private final T212Client t212Client;
    private final AccountTransactionRepository transactionRepository;

    public TransactionService(T212Client t212Client, AccountTransactionRepository transactionRepository) {
        this.t212Client = t212Client;
        this.transactionRepository = transactionRepository;
    }

    public double getNetDeposits() {
        return transactionRepository.sumNetDeposits(CASHBACK_THRESHOLD);
    }

    @Scheduled(fixedRate = 5*60*1000, initialDelay = 20*1000)
    public void syncTransactions() {
        log.info("Syncing transactions from T212...");

        try {
            List<AccountTransaction> newTransactions = new ArrayList<>();
            String path = FIRST_PAGE;
            boolean reachedKnown = false;

            while (path != null && !reachedKnown) {
                T212TransactionPageDto page = t212Client.fetchTransactions(path);

                for (T212TransactionDto dto : page.items()) {
                    if (transactionRepository.existsById(dto.reference())) {
                        reachedKnown = true;
                        break;
                    }
                    else {
                        newTransactions.add(new AccountTransaction(
                                dto.reference(),
                                dto.type(),
                                dto.amount(),
                                dto.currency(),
                                Instant.parse(dto.dateTime())
                        ));
                    }
                }

                if (page.nextPagePath() == null) path = null;
                else path = page.nextPagePath().substring(API_PREFIX.length());

                if (path != null && !reachedKnown) Thread.sleep(3_100);
            }

            transactionRepository.saveAll(newTransactions);
            log.info("Synced {} new transactions.", newTransactions.size());}

            catch (Exception e) {
            log.warn("No se pudieron sincronizar los movimientos: {}", e.getMessage());
        }
    }

}
