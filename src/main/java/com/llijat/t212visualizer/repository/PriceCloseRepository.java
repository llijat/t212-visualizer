package com.llijat.t212visualizer.repository;

import com.llijat.t212visualizer.model.PriceClose;
import com.llijat.t212visualizer.model.PriceDay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface PriceCloseRepository extends JpaRepository<PriceClose, PriceDay> {
    List<PriceClose> findByIdSymbolAndIdDayGreaterThanEqualOrderByIdDayAsc(String symbol, LocalDate from);
    long countByIdSymbolAndIdDayGreaterThanEqual(String symbol, LocalDate from);
    List<PriceClose> findByIdDayGreaterThanEqualOrderByIdDayAsc(LocalDate from);
}
