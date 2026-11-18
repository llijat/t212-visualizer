package com.llijat.t212visualizer.repository;


import com.llijat.t212visualizer.model.EarningsEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EarningsEventRepository extends JpaRepository<EarningsEvent, Long> {
    Optional<EarningsEvent> findByTickerAndFiscalYearAndFiscalQuarter(String ticker, int fiscalYear, int fiscalQuarter);
    List<EarningsEvent> findByReportDateBetween(LocalDate from, LocalDate to);
    List<EarningsEvent> findByReportDateBetweenOrderByReportDateAsc(LocalDate from, LocalDate to);
}
