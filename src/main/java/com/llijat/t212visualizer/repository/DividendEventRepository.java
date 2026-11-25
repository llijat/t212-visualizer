package com.llijat.t212visualizer.repository;

import com.llijat.t212visualizer.model.DividendEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DividendEventRepository extends JpaRepository<DividendEvent, Long> {
    Optional<DividendEvent> findByTickerAndPaymentDate(String ticker, LocalDate paymentDate);
    List<DividendEvent> findByPaymentDateBetween(LocalDate from, LocalDate to);
    List<DividendEvent> findByReceivedEurIsNotNullOrderByPaymentDateDesc();
    List<DividendEvent> findByPaymentDateBetweenOrderByPaymentDateAsc(LocalDate from, LocalDate to);
    List<DividendEvent> findByReceivedEurIsNotNullAndPaymentDateBetween(LocalDate from, LocalDate to);
}