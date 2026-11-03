package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.T212PositionDto;
import com.llijat.t212visualizer.model.Position;
import com.llijat.t212visualizer.repository.PositionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;

@Service
public class PositionService {

    private static final Logger log = LoggerFactory.getLogger(PositionService.class);

    private final T212Client t212Client;
    private final PositionRepository positionRepository;

    public PositionService(T212Client t212Client, PositionRepository positionRepository) {
        this.t212Client = t212Client;
        this.positionRepository = positionRepository;
    }

    public List<Position> getAllPositions() {
        return positionRepository.findAll();
    }


    @Scheduled(fixedRate = 5 * 60 * 1000, initialDelay = 0)
    @Transactional
    public void refreshFromT212() {
        log.info("Refreshing positions from T212...");
        List<T212PositionDto> fetched = t212Client.fetchPositions();

        Set<String> tickers = new HashSet<>();

        for (T212PositionDto dto : fetched) {
            String ticker = dto.instrument().ticker();
            tickers.add(ticker);

            Optional<Position> existing = positionRepository.findById(ticker);
            if (existing.isPresent()) {
                existing.get().updateFrom(
                        dto.instrument().name(),
                        dto.quantity(),
                        dto.averagePricePaid(),
                        dto.currentPrice(),
                        dto.walletImpact().currentValue(),
                        dto.walletImpact().unrealizedProfitLoss(),
                        dto.walletImpact().fxImpact(),
                        dto.walletImpact().totalCost(),
                        dto.instrument().currency(),
                        dto.walletImpact().currency()
                );
                positionRepository.save(existing.get());
            } else {
                Position created = new Position(
                        ticker,
                        dto.instrument().name(),
                        dto.quantity(),
                        dto.averagePricePaid(),
                        dto.currentPrice(),
                        dto.walletImpact().currentValue(),
                        dto.walletImpact().unrealizedProfitLoss(),
                        dto.walletImpact().fxImpact(), 
                        dto.walletImpact().totalCost(),
                        dto.instrument().currency(),
                        dto.walletImpact().currency()
                );
                positionRepository.save(created);
            }
        }

        // Delete every position whose ticker was not returned (it has been closed)
        
        for (Position position : positionRepository.findAll()) {
            if (!tickers.contains(position.getTicker()))positionRepository.delete(position);
        }
        

        log.info("Refreshed {} positions.", fetched.size());
    }
}
