package com.llijat.t212visualizer.repository;

import com.llijat.t212visualizer.model.AccountTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountTransactionRepository extends JpaRepository<AccountTransaction, String> {
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM AccountTransaction t " +
            "WHERE (t.type = 'DEPOSIT' AND t.amount >= :cashbackThreshold) " +
            "OR t.type IN ('WITHDRAW', 'TRANSFER')")
    double sumNetDeposits(@Param("cashbackThreshold") double cashbackThreshold);
}
