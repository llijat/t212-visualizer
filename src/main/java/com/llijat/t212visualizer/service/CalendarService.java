package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.*;
import com.llijat.t212visualizer.model.DividendEvent;
import com.llijat.t212visualizer.model.EarningsEvent;
import com.llijat.t212visualizer.model.Position;
import com.llijat.t212visualizer.repository.DividendEventRepository;
import com.llijat.t212visualizer.repository.EarningsEventRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CalendarService {
    private final Clock clock;
    private static final int COMING_UP_DAYS = 14;

    private final EarningsEventRepository earningsEventRepository;
    private final PositionService positionService;
    private final CompanyProfileService companyProfileService;
    private final DividendEventRepository dividendEventRepository;
    private final CardExpenseSummaryService cardExpenseSummaryService;

    public CalendarService(EarningsEventRepository earningsEventRepository, PositionService positionService, CompanyProfileService companyProfileService, DividendEventRepository dividendEventRepository, Clock clock, CardExpenseSummaryService cardExpenseSummaryService) {
        this.earningsEventRepository = earningsEventRepository;
        this.positionService = positionService;
        this.companyProfileService = companyProfileService;
        this.dividendEventRepository = dividendEventRepository;
        this.clock = clock;
        this.cardExpenseSummaryService = cardExpenseSummaryService;
    }

    public CalendarMonth getMonth(YearMonth month) {
        LocalDate firstDay = month.atDay(1);
        LocalDate lastDay = month.atEndOfMonth();
        LocalDate gridStart = firstDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate gridEnd = lastDay.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        LocalDate today = LocalDate.now(clock);

        List<CalendarDay> dayList = new ArrayList<>();
        List<EarningsEvent> events = earningsEventRepository.findByReportDateBetween(gridStart, gridEnd);

        Set<String> heldTickers = positionService.getAllPositions().stream().map(Position::getTicker).collect(Collectors.toSet());

        Map<LocalDate, List<EarningsEvent>> byDate = events.stream()
                .filter(event -> heldTickers.contains(event.getTicker()))
                .sorted(Comparator.comparing(EarningsEvent::getTicker))
                .collect(Collectors.groupingBy(EarningsEvent::getReportDate));

        Map<String, BigDecimal> sharesByTicker = new HashMap<>();
        for (Position position : positionService.getAllPositions()) {
            sharesByTicker.put(position.getTicker(), position.getQuantity());
        }

        Map<String, DividendEvent> lastReceivedByTicker = lastReceivedByTicker();

        Map<LocalDate, List<CalendarDividend>> dividendsByDate = dividendEventRepository.findByPaymentDateBetween(gridStart, gridEnd).stream()
                .filter(dividend -> sharesByTicker.containsKey(dividend.getTicker()) || dividend.getReceivedEur() != null)
                .sorted(Comparator.comparing(DividendEvent::getTicker))
                .map(dividend -> toCalendarDividend(dividend, lastReceivedByTicker.get(dividend.getTicker()), sharesByTicker.get(dividend.getTicker())))
                .collect(Collectors.groupingBy(CalendarDividend::paymentDate));

        Map<LocalDate, List<CalendarSubscription>> subscriptionsByDate = subscriptionsByDate(gridStart, gridEnd, today);
        for (LocalDate date = gridStart; !date.isAfter(gridEnd); date = date.plusDays(1)) {
            dayList.add(new CalendarDay(date, YearMonth.from(date).equals(month), date.equals(today), byDate.getOrDefault(date, List.of()), dividendsByDate.getOrDefault(date, List.of()), subscriptionsByDate.getOrDefault(date, List.of())));
        }
        return new CalendarMonth(month,dayList);
    }

    public List<UpcomingEarnings> getComingUp() {
        LocalDate today = LocalDate.now(clock);
        Map<String, String> symbols = companyProfileService.currentSymbols();

        Map<String, String> symbolByTicker = new HashMap<>();
        for (Position position : positionService.getAllPositions()) {
            symbolByTicker.put(position.getTicker(), symbols.getOrDefault(position.getTicker(), position.getSymbol()));
        }

        return earningsEventRepository.findByReportDateBetweenOrderByReportDateAsc(today, today.plusDays(COMING_UP_DAYS - 1)).stream()
                .filter(event -> symbolByTicker.containsKey(event.getTicker()))
                .map(event -> new UpcomingEarnings(symbolByTicker.get(event.getTicker()), event.getTicker(), event.getReportDate(), event.getReportHour()))
                .toList();
    }

    public List<UpcomingDividend> getComingUpDividends() {
        LocalDate today = LocalDate.now(clock);
        Map<String, String> symbols = companyProfileService.currentSymbols();
        Map<String, DividendEvent> lastReceived = lastReceivedByTicker();

        Map<String, Position> positionByTicker = new HashMap<>();
        for (Position position : positionService.getAllPositions()) {
            positionByTicker.put(position.getTicker(), position);
        }

        List<UpcomingDividend> upcoming = new ArrayList<>();
        for (DividendEvent dividend : dividendEventRepository.findByPaymentDateBetweenOrderByPaymentDateAsc(today, today.plusDays(COMING_UP_DAYS - 1))) {
            Position position = positionByTicker.get(dividend.getTicker());
            if (position == null) continue;
            String symbol = symbols.getOrDefault(dividend.getTicker(), position.getSymbol());
            BigDecimal estimated = estimate(dividend, lastReceived.get(dividend.getTicker()), position.getQuantity());
            upcoming.add(new UpcomingDividend(symbol, dividend.getTicker(), dividend.getPaymentDate(), dividend.getReceivedEur(), estimated));
        }
        return upcoming;
    }

    private CalendarDividend toCalendarDividend(DividendEvent dividend, DividendEvent lastReceived, BigDecimal sharesNow) {
        return new CalendarDividend(dividend.getTicker(), dividend.getPaymentDate(), dividend.getExDividendDate(),
                dividend.getAmountPerShare(), dividend.getReceivedEur(), estimate(dividend, lastReceived, sharesNow));
    }

    /** Announced amount x shares now x the share of the gross that reached me last time (tax and exchange rate included). */
    private BigDecimal estimate(DividendEvent dividend, DividendEvent lastReceived, BigDecimal sharesNow) {
        if (dividend.getReceivedEur() != null || dividend.getAmountPerShare() == null || sharesNow == null) return null;
        if (lastReceived == null || lastReceived.getAmountPerShare() == null || lastReceived.getReceivedQuantity() == null) return null;

        BigDecimal lastGross = lastReceived.getAmountPerShare().multiply(lastReceived.getReceivedQuantity());
        if (lastGross.signum() == 0) return null;
        return dividend.getAmountPerShare().multiply(sharesNow).multiply(lastReceived.getReceivedEur())
                .divide(lastGross, 2, RoundingMode.HALF_UP);
    }

    private Map<String, DividendEvent> lastReceivedByTicker() {
        Map<String, DividendEvent> lastReceived = new HashMap<>();
        for (DividendEvent received : dividendEventRepository.findByReceivedEurIsNotNullOrderByPaymentDateDesc()) {
            lastReceived.putIfAbsent(received.getTicker(), received);
        }
        return lastReceived;
    }

    private Map<LocalDate, List<CalendarSubscription>> subscriptionsByDate(LocalDate gridStart, LocalDate gridEnd, LocalDate today) {
        Map<LocalDate, List<CalendarSubscription>> byDate = new HashMap<>();
        for (Subscription subscription : cardExpenseSummaryService.getSubscriptions(today)) {
            LocalDate last = subscription.lastPayment();
            if (!last.isBefore(gridStart) && !last.isAfter(gridEnd)) {
                byDate.computeIfAbsent(last, day -> new ArrayList<>())
                        .add(new CalendarSubscription(subscription.merchant(), subscription.amount(), false));
            }
            for (int months = 0; ; months++) {
                LocalDate expected = subscription.nextPayment().plusMonths(months);
                if (expected.isAfter(gridEnd)) break;
                if (expected.isBefore(gridStart)) continue;
                byDate.computeIfAbsent(expected, day -> new ArrayList<>())
                        .add(new CalendarSubscription(subscription.merchant(), subscription.amount(), true));
            }
        }
        return byDate;
    }
}
