package com.llijat.t212visualizer.service;

import java.time.Clock;
import java.time.Instant;

import com.llijat.t212visualizer.dto.CalendarDay;
import com.llijat.t212visualizer.dto.CalendarDividend;
import com.llijat.t212visualizer.dto.CalendarMonth;
import com.llijat.t212visualizer.dto.CalendarSubscription;
import com.llijat.t212visualizer.dto.Subscription;
import com.llijat.t212visualizer.dto.UpcomingDividend;
import com.llijat.t212visualizer.dto.UpcomingEarnings;
import com.llijat.t212visualizer.model.DividendEvent;
import com.llijat.t212visualizer.model.EarningsEvent;
import com.llijat.t212visualizer.model.Position;
import com.llijat.t212visualizer.repository.DividendEventRepository;
import com.llijat.t212visualizer.repository.EarningsEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Without stubbing, the mocks return empty lists: no positions and no earnings
@ExtendWith(MockitoExtension.class)
class CalendarServiceTest {

    // "Now" for the code under test: fixed, so a test run at midnight does not see two different days
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-15T11:00:00Z"), ZoneId.of("Europe/Madrid"));

    @Mock
    private EarningsEventRepository repository;

    @Mock
    private PositionService positionService;

    @Mock
    private CompanyProfileService companyProfileService;

    // Without stubbing, no dividends at all: the earnings tests above do not need any
    @Mock
    private DividendEventRepository dividendRepository;

    // Without stubbing, no subscriptions
    @Mock
    private CardExpenseSummaryService cardExpenseSummaryService;

    private CalendarService service;

    @BeforeEach
    void setUp() {
        service = new CalendarService(repository, positionService, companyProfileService, dividendRepository, CLOCK, cardExpenseSummaryService);
    }

    private static Position position(String ticker) {
        BigDecimal one = BigDecimal.ONE;
        return new Position(ticker, ticker + " Inc.", "US0000000001", one, one, one, one, BigDecimal.ZERO, null, one, "USD", "EUR");
    }

    private static EarningsEvent earnings(String ticker, LocalDate date) {
        EarningsEvent event = new EarningsEvent(ticker, 2026, 3);
        event.updateReport(date, "amc", null, null, null, null);
        return event;
    }

    private static CalendarDay dayOf(CalendarMonth calendar, LocalDate date) {
        return calendar.days().stream().filter(day -> day.date().equals(date)).findFirst().orElseThrow();
    }

    @Test
    void november2026_startsOnTheMondayBefore_andEndsOnTheSundayAfter() {
        // 1 November 2026 is a Sunday and 30 November is a Monday: the worst case, 6 rows
        CalendarMonth calendar = service.getMonth(YearMonth.of(2026, 11));
        List<CalendarDay> days = calendar.days();

        assertEquals(YearMonth.of(2026, 11), calendar.month());
        assertEquals(42, days.size());
        assertEquals(LocalDate.of(2026, 10, 26), days.get(0).date());
        assertEquals(LocalDate.of(2026, 12, 6), days.get(41).date());
    }

    @Test
    void november2026_marksOnlyTheDaysOfNovemberAsInMonth() {
        List<CalendarDay> days = service.getMonth(YearMonth.of(2026, 11)).days();

        assertFalse(days.get(0).inMonth());   // 26 October
        assertFalse(days.get(5).inMonth());   // 31 October
        assertTrue(days.get(6).inMonth());    // 1 November
        assertTrue(days.get(35).inMonth());   // 30 November
        assertFalse(days.get(36).inMonth());  // 1 December
        assertEquals(30, days.stream().filter(CalendarDay::inMonth).count());
    }

    @Test
    void february2027_fitsExactlyInFourRows() {
        // 1 February 2027 is a Monday and 28 February is a Sunday: no grey days at all
        List<CalendarDay> days = service.getMonth(YearMonth.of(2027, 2)).days();

        assertEquals(28, days.size());
        assertEquals(LocalDate.of(2027, 2, 1), days.get(0).date());
        assertEquals(LocalDate.of(2027, 2, 28), days.get(27).date());
        assertTrue(days.stream().allMatch(CalendarDay::inMonth));
    }

    @Test
    void everyMonth_isWholeWeeksFromMondayToSunday_withNoGapsOrRepeats() {
        for (YearMonth month = YearMonth.of(2026, 1); !month.isAfter(YearMonth.of(2028, 12)); month = month.plusMonths(1)) {
            List<CalendarDay> days = service.getMonth(month).days();

            assertEquals(0, days.size() % 7, month + " is not whole weeks");
            assertEquals(DayOfWeek.MONDAY, days.get(0).date().getDayOfWeek(), month + " does not start on Monday");
            assertEquals(DayOfWeek.SUNDAY, days.get(days.size() - 1).date().getDayOfWeek(), month + " does not end on Sunday");
            for (int i = 1; i < days.size(); i++) {
                assertEquals(days.get(i - 1).date().plusDays(1), days.get(i).date(), month + " skips or repeats a day");
            }
        }
    }

    @Test
    void today_isMarkedOnce_inSpanishTime() {
        LocalDate today = LocalDate.now(CLOCK);

        List<CalendarDay> days = service.getMonth(YearMonth.from(today)).days();

        assertEquals(1, days.stream().filter(CalendarDay::today).count());
        assertTrue(days.stream().filter(CalendarDay::today).allMatch(day -> day.date().equals(today)));
    }

    @Test
    void readsTheEarningsOfTheWholeGrid_includingTheGreyDays() {
        service.getMonth(YearMonth.of(2026, 11));

        verify(repository).findByReportDateBetween(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 12, 6));
    }

    @Test
    void eachResultGoesToItsDay_sortedByTicker() {
        LocalDate oct28 = LocalDate.of(2026, 10, 28);
        LocalDate nov5 = LocalDate.of(2026, 11, 5);
        when(positionService.getAllPositions())
                .thenReturn(List.of(position("MSFT_US_EQ"), position("GOOGL_US_EQ"), position("GEV_US_EQ"), position("MU_US_EQ")));
        when(repository.findByReportDateBetween(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 12, 6)))
                .thenReturn(List.of(earnings("MSFT_US_EQ", oct28), earnings("MU_US_EQ", nov5),
                        earnings("GEV_US_EQ", oct28), earnings("GOOGL_US_EQ", oct28)));

        CalendarMonth calendar = service.getMonth(YearMonth.of(2026, 11));

        List<String> oct28Tickers = dayOf(calendar, oct28).earnings().stream().map(EarningsEvent::getTicker).toList();
        assertEquals(List.of("GEV_US_EQ", "GOOGL_US_EQ", "MSFT_US_EQ"), oct28Tickers);
        assertEquals(1, dayOf(calendar, nov5).earnings().size());
        assertEquals("MU_US_EQ", dayOf(calendar, nov5).earnings().get(0).getTicker());
    }

    @Test
    void resultsOfStocksYouNoLongerHold_areNotShown() {
        LocalDate nov5 = LocalDate.of(2026, 11, 5);
        when(positionService.getAllPositions()).thenReturn(List.of(position("MU_US_EQ")));
        when(repository.findByReportDateBetween(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 12, 6)))
                .thenReturn(List.of(earnings("MU_US_EQ", nov5), earnings("SOLD_US_EQ", nov5)));

        CalendarMonth calendar = service.getMonth(YearMonth.of(2026, 11));

        assertEquals(1, dayOf(calendar, nov5).earnings().size());
        assertEquals("MU_US_EQ", dayOf(calendar, nov5).earnings().get(0).getTicker());
    }

    @Test
    void aDayWithoutResults_getsAnEmptyList_neverNull() {
        CalendarMonth calendar = service.getMonth(YearMonth.of(2026, 11));

        for (CalendarDay day : calendar.days()) {
            assertNotNull(day.earnings(), day.date() + " has null earnings");
            assertTrue(day.earnings().isEmpty());
        }
    }

    // ===== Coming up: the next two weeks, for the news page =====

    private static final LocalDate TODAY = LocalDate.now(CLOCK);

    @Test
    void comingUp_asksForTodayAndTheNext13Days_inDateOrder() {
        when(positionService.getAllPositions()).thenReturn(List.of());
        when(companyProfileService.currentSymbols()).thenReturn(Map.of());

        service.getComingUp();

        // Between includes both ends: today + 13 makes 14 days
        verify(repository).findByReportDateBetweenOrderByReportDateAsc(TODAY, TODAY.plusDays(13));
    }

    @Test
    void comingUp_onlyShowsWhatIsHeldToday() {
        when(positionService.getAllPositions()).thenReturn(List.of(position("NVDA_US_EQ")));
        when(companyProfileService.currentSymbols()).thenReturn(Map.of());
        when(repository.findByReportDateBetweenOrderByReportDateAsc(any(), any())).thenReturn(List.of(
                earnings("NVDA_US_EQ", TODAY.plusDays(1)), earnings("SOLD_US_EQ", TODAY.plusDays(2))));

        List<UpcomingEarnings> comingUp = service.getComingUp();

        assertEquals(List.of("NVDA"), comingUp.stream().map(UpcomingEarnings::symbol).toList());
    }

    @Test
    void comingUp_usesTheCurrentSymbol_soTheNewsOfThatCompanyCanBeShown() {
        when(positionService.getAllPositions()).thenReturn(List.of(position("FB_US_EQ")));
        when(companyProfileService.currentSymbols()).thenReturn(Map.of("FB_US_EQ", "META"));
        when(repository.findByReportDateBetweenOrderByReportDateAsc(any(), any())).thenReturn(List.of(
                earnings("FB_US_EQ", TODAY.plusDays(3))));

        UpcomingEarnings meta = service.getComingUp().get(0);

        assertEquals("META", meta.symbol());
        assertEquals("FB_US_EQ", meta.ticker());
        assertEquals(TODAY.plusDays(3), meta.date());
        assertEquals("after the close", meta.hourLabel());
    }

    @Test
    void comingUp_keepsTheOrderOfTheQuery() {
        when(positionService.getAllPositions()).thenReturn(List.of(position("AAPL_US_EQ"), position("NVDA_US_EQ")));
        when(companyProfileService.currentSymbols()).thenReturn(Map.of());
        when(repository.findByReportDateBetweenOrderByReportDateAsc(any(), any())).thenReturn(List.of(
                earnings("NVDA_US_EQ", TODAY), earnings("AAPL_US_EQ", TODAY.plusDays(9))));

        assertEquals(List.of("NVDA", "AAPL"), service.getComingUp().stream().map(UpcomingEarnings::symbol).toList());
    }
    // ===== Dividends =====

    private static final YearMonth DECEMBER = YearMonth.of(2026, 12);
    private static final LocalDate NEXT_PAYMENT = LocalDate.of(2026, 12, 10);

    private static Position holding(String ticker, String shares) {
        BigDecimal one = BigDecimal.ONE;
        return new Position(ticker, ticker + " Inc.", "US0000000001", new BigDecimal(shares), one, one, one, BigDecimal.ZERO, null, one, "USD", "EUR");
    }

    private static DividendEvent announced(String ticker, LocalDate payment, String perShare) {
        DividendEvent dividend = new DividendEvent(ticker, payment);
        dividend.updateAnnouncement(payment.minusDays(14), new BigDecimal(perShare));
        return dividend;
    }

    private static DividendEvent received(String ticker, LocalDate payment, String perShare, String inEuro, String shares) {
        DividendEvent dividend = new DividendEvent(ticker, payment);
        dividend.recordPayment(new BigDecimal(inEuro), new BigDecimal(perShare), new BigDecimal(shares));
        return dividend;
    }

    private void theGridHas(DividendEvent... dividends) {
        when(dividendRepository.findByPaymentDateBetween(LocalDate.of(2026, 11, 30), LocalDate.of(2027, 1, 3))).thenReturn(List.of(dividends));
    }

    private void receivedBefore(DividendEvent... newestFirst) {
        when(dividendRepository.findByReceivedEurIsNotNullOrderByPaymentDateDesc()).thenReturn(List.of(newestFirst));
    }

    private CalendarDividend theOnlyDividendOn(LocalDate date) {
        List<CalendarDividend> dividends = dayOf(service.getMonth(DECEMBER), date).dividends();
        assertEquals(1, dividends.size());
        return dividends.get(0);
    }

    @Test
    void anAnnouncedDividend_goesToItsPaymentDay_withItsExDateAndAmount() {
        when(positionService.getAllPositions()).thenReturn(List.of(holding("MU_US_EQ", "8.5")));
        theGridHas(announced("MU_US_EQ", NEXT_PAYMENT, "0.115"));

        CalendarDividend dividend = theOnlyDividendOn(NEXT_PAYMENT);

        assertEquals("MU_US_EQ", dividend.ticker());
        assertEquals(NEXT_PAYMENT.minusDays(14), dividend.exDividendDate());
        assertEquals(new BigDecimal("0.115"), dividend.amountPerShare());
        assertFalse(dividend.isReceived());
    }

    @Test
    void theEstimate_usesTheShareOfTheGrossThatArrivedLastTime() {
        // Last time: 0.115 $ x 8.5 shares = 0.9775 $ gross, and 0.70 € arrived (tax and exchange rate)
        // Now 10 shares and 0.12 $ announced: 0.12 x 10 x 0.70 / 0.9775 = 0.859... -> 0.86 €
        when(positionService.getAllPositions()).thenReturn(List.of(holding("MU_US_EQ", "10")));
        theGridHas(announced("MU_US_EQ", NEXT_PAYMENT, "0.12"));
        receivedBefore(received("MU_US_EQ", LocalDate.of(2026, 9, 10), "0.115", "0.70", "8.5"));

        assertEquals(new BigDecimal("0.86"), theOnlyDividendOn(NEXT_PAYMENT).estimatedEur());
    }

    @Test
    void theEstimate_isBasedOnTheMostRecentPayment_notAnOlderOne() {
        // The repository gives the newest first: the September payment (more tax) must be the one used
        when(positionService.getAllPositions()).thenReturn(List.of(holding("MU_US_EQ", "10")));
        theGridHas(announced("MU_US_EQ", NEXT_PAYMENT, "0.10"));
        receivedBefore(
                received("MU_US_EQ", LocalDate.of(2026, 9, 10), "0.10", "0.50", "10"),
                received("MU_US_EQ", LocalDate.of(2026, 6, 10), "0.10", "0.90", "10"));

        assertEquals(new BigDecimal("0.50"), theOnlyDividendOn(NEXT_PAYMENT).estimatedEur());
    }

    @Test
    void withoutAPreviousPayment_thereIsNoEstimate_onlyTheDate() {
        when(positionService.getAllPositions()).thenReturn(List.of(holding("MU_US_EQ", "8.5")));
        theGridHas(announced("MU_US_EQ", NEXT_PAYMENT, "0.115"));

        assertNull(theOnlyDividendOn(NEXT_PAYMENT).estimatedEur());
    }

    @Test
    void aReceivedDividend_showsTheRealFigure_andNoEstimate() {
        LocalDate paid = LocalDate.of(2026, 12, 3);
        when(positionService.getAllPositions()).thenReturn(List.of(holding("MU_US_EQ", "8.5")));
        DividendEvent december = received("MU_US_EQ", paid, "0.115", "0.84", "8.5");
        theGridHas(december);
        receivedBefore(december);

        CalendarDividend dividend = theOnlyDividendOn(paid);

        assertTrue(dividend.isReceived());
        assertEquals(new BigDecimal("0.84"), dividend.receivedEur());
        assertNull(dividend.estimatedEur());
    }

    @Test
    void aStockYouSold_keepsWhatItPaid_butLosesWhatWasOnlyAnnounced() {
        // Received money was real, so it stays; an announcement for shares you no longer have does not
        LocalDate paid = LocalDate.of(2026, 12, 3);
        theGridHas(received("ORCL_US_EQ", paid, "0.50", "3.65", "10"), announced("ORCL_US_EQ", NEXT_PAYMENT, "0.50"));

        CalendarMonth calendar = service.getMonth(DECEMBER);

        assertEquals(1, dayOf(calendar, paid).dividends().size());
        assertTrue(dayOf(calendar, NEXT_PAYMENT).dividends().isEmpty());
    }

    @Test
    void aPaymentRecordedBeforeTheQuantityWasStored_givesNoEstimate_insteadOfFailing() {
        DividendEvent withoutQuantity = new DividendEvent("MU_US_EQ", LocalDate.of(2026, 9, 10));
        withoutQuantity.recordPayment(new BigDecimal("0.70"), new BigDecimal("0.115"), null);
        when(positionService.getAllPositions()).thenReturn(List.of(holding("MU_US_EQ", "10")));
        theGridHas(announced("MU_US_EQ", NEXT_PAYMENT, "0.12"));
        receivedBefore(withoutQuantity);

        assertNull(theOnlyDividendOn(NEXT_PAYMENT).estimatedEur());
    }

    @Test
    void severalDividendsOnOneDay_comeSortedByTicker() {
        when(positionService.getAllPositions()).thenReturn(List.of(holding("ORCL_US_EQ", "1"), holding("MU_US_EQ", "1")));
        theGridHas(announced("ORCL_US_EQ", NEXT_PAYMENT, "0.50"), announced("MU_US_EQ", NEXT_PAYMENT, "0.115"));

        List<CalendarDividend> dividends = dayOf(service.getMonth(DECEMBER), NEXT_PAYMENT).dividends();

        assertEquals(List.of("MU_US_EQ", "ORCL_US_EQ"), dividends.stream().map(CalendarDividend::ticker).toList());
    }

    @Test
    void aDayWithoutDividends_getsAnEmptyList_neverNull() {
        assertNotNull(dayOf(service.getMonth(DECEMBER), NEXT_PAYMENT).dividends());
    }
    // ===== Coming up: dividends of the next two weeks =====

    private void comingUpHolds(DividendEvent... dividends) {
        LocalDate today = LocalDate.now(CLOCK);
        when(dividendRepository.findByPaymentDateBetweenOrderByPaymentDateAsc(today, today.plusDays(13))).thenReturn(List.of(dividends));
    }

    @Test
    void comingUpDividends_asksForTodayAndTheNext13Days() {
        comingUpHolds();

        assertTrue(service.getComingUpDividends().isEmpty());
    }

    @Test
    void comingUpDividends_carryTheCurrentSymbol_andTheEstimate() {
        LocalDate soon = LocalDate.now(CLOCK).plusDays(4);
        when(positionService.getAllPositions()).thenReturn(List.of(holding("FB_US_EQ", "10")));
        when(companyProfileService.currentSymbols()).thenReturn(Map.of("FB_US_EQ", "META"));
        comingUpHolds(announced("FB_US_EQ", soon, "0.525"));
        receivedBefore(received("FB_US_EQ", soon.minusMonths(3), "0.525", "4.13", "10"));

        UpcomingDividend dividend = service.getComingUpDividends().get(0);

        assertEquals("META", dividend.symbol());
        assertEquals("FB_US_EQ", dividend.ticker());
        assertEquals(soon, dividend.date());
        assertNull(dividend.receivedEur());
        // 0.525 x 10 x 4.13 / (0.525 x 10) = 4.13: same shares and amount as last time, so the same euros
        assertEquals(new BigDecimal("4.13"), dividend.estimatedEur());
    }

    @Test
    void comingUpDividends_leaveOutStocksYouNoLongerHold_becauseThoseWillNotPayYou() {
        LocalDate soon = LocalDate.now(CLOCK).plusDays(4);
        when(positionService.getAllPositions()).thenReturn(List.of(holding("MU_US_EQ", "8.5")));
        comingUpHolds(announced("ORCL_US_EQ", soon, "0.50"), announced("MU_US_EQ", soon.plusDays(1), "0.115"));

        assertEquals(List.of("MU"), service.getComingUpDividends().stream().map(UpcomingDividend::symbol).toList());
    }

    @Test
    void comingUpDividends_keepTheOrderOfTheQuery() {
        LocalDate today = LocalDate.now(CLOCK);
        when(positionService.getAllPositions()).thenReturn(List.of(holding("MU_US_EQ", "1"), holding("ORCL_US_EQ", "1")));
        comingUpHolds(announced("ORCL_US_EQ", today.plusDays(2), "0.50"), announced("MU_US_EQ", today.plusDays(9), "0.115"));

        assertEquals(List.of("ORCL", "MU"), service.getComingUpDividends().stream().map(UpcomingDividend::symbol).toList());
    }

    // ===== Subscriptions: the last real payment, and the ones expected every month after it =====

    private void theSubscriptionIs(String lastPayment, String nextPayment) {
        when(cardExpenseSummaryService.getSubscriptions(LocalDate.of(2026, 11, 15))).thenReturn(List.of(
                new Subscription("Globo Gym", new BigDecimal("29.90"), new BigDecimal("29.90"), LocalDate.parse(lastPayment), LocalDate.parse(nextPayment))));
    }

    private static List<CalendarSubscription> subscriptionsOn(CalendarMonth calendar, String date) {
        return calendar.days().stream().filter(day -> day.date().equals(LocalDate.parse(date))).findFirst().orElseThrow().subscriptions();
    }

    @Test
    void theLastPayment_isPaid_andTheNextOne_isExpected() {
        theSubscriptionIs("2026-10-26", "2026-11-26");

        CalendarMonth november = service.getMonth(YearMonth.of(2026, 11));   // its grid starts on Monday 26 October

        assertEquals(List.of(new CalendarSubscription("Globo Gym", new BigDecimal("29.90"), false)), subscriptionsOn(november, "2026-10-26"));
        assertEquals(List.of(new CalendarSubscription("Globo Gym", new BigDecimal("29.90"), true)), subscriptionsOn(november, "2026-11-26"));
        assertEquals(2, november.days().stream().mapToInt(day -> day.subscriptions().size()).sum());
    }

    @Test
    void aLaterMonth_expectsItOnTheSameDay() {
        theSubscriptionIs("2026-10-26", "2026-11-26");

        CalendarMonth march = service.getMonth(YearMonth.of(2027, 3));

        assertTrue(subscriptionsOn(march, "2027-03-26").get(0).expected());
        assertEquals(1, march.days().stream().mapToInt(day -> day.subscriptions().size()).sum());
    }

    @Test
    void onThe31st_aShortMonthGetsItsLastDay_andTheNextLongOneThe31stAgain() {
        theSubscriptionIs("2026-12-31", "2027-01-31");

        assertEquals(1, subscriptionsOn(service.getMonth(YearMonth.of(2027, 2)), "2027-02-28").size());
        assertEquals(1, subscriptionsOn(service.getMonth(YearMonth.of(2027, 3)), "2027-03-31").size());   // not the 28th: counted from January
    }

    @Test
    void aMonthBeforeTheNextPayment_onlyShowsTheRealOne() {
        theSubscriptionIs("2026-10-26", "2026-11-26");

        CalendarMonth october = service.getMonth(YearMonth.of(2026, 10));   // its grid ends on Sunday 1 November

        assertFalse(subscriptionsOn(october, "2026-10-26").get(0).expected());
        assertEquals(1, october.days().stream().mapToInt(day -> day.subscriptions().size()).sum());
    }
}
