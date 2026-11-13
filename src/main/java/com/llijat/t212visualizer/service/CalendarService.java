package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.CalendarDay;
import com.llijat.t212visualizer.dto.CalendarMonth;
import com.llijat.t212visualizer.model.EarningsEvent;
import com.llijat.t212visualizer.model.Position;
import com.llijat.t212visualizer.repository.EarningsEventRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CalendarService {
    private static final ZoneId SPAIN = ZoneId.of("Europe/Madrid");
    private final EarningsEventRepository earningsEventRepository;
    private final PositionService positionService;

    public CalendarService(EarningsEventRepository earningsEventRepository, PositionService positionService) {
        this.earningsEventRepository = earningsEventRepository;
        this.positionService = positionService;
    }

    public CalendarMonth getMonth(YearMonth month) {
        LocalDate firstDay = month.atDay(1);
        LocalDate lastDay = month.atEndOfMonth();
        LocalDate gridStart = firstDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate gridEnd = lastDay.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        LocalDate today = LocalDate.now(SPAIN);

        List<CalendarDay> dayList = new ArrayList<>();
        List<EarningsEvent> events = earningsEventRepository.findByReportDateBetween(gridStart, gridEnd);
        Set<String> heldTickers = positionService.getAllPositions().stream().map(Position::getTicker).collect(Collectors.toSet());
        Map<LocalDate, List<EarningsEvent>> byDate = events.stream()
                .filter(event -> heldTickers.contains(event.getTicker()))
                .sorted(Comparator.comparing(EarningsEvent::getTicker))
                .collect(Collectors.groupingBy(EarningsEvent::getReportDate));

        for (LocalDate date = gridStart; !date.isAfter(gridEnd); date = date.plusDays(1)) {
            dayList.add(new CalendarDay(date, YearMonth.from(date).equals(month),date.equals(today),byDate.getOrDefault(date,List.of())));
        }

        return new CalendarMonth(month,dayList);
    }
}
