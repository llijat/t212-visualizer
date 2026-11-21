package com.llijat.t212visualizer.repository;

import com.llijat.t212visualizer.model.DividendEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface DividendEventRepository extends JpaRepository<DividendEvent, Long> {
    Optional<DividendEvent> findByTickerAndPaymentDate(String ticker, LocalDate paymentDate);
}