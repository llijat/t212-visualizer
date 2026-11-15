package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.CalendarDay;
import com.llijat.t212visualizer.dto.CalendarMonth;
import com.llijat.t212visualizer.model.EarningsEvent;
import com.llijat.t212visualizer.model.Position;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Without stubbing, the mocks return empty lists: no positions and no earnings
@ExtendWith(MockitoExtension.class)
class CalendarServiceTest {

    @Mock
    private EarningsEventRepository repository;

    @Mock
    private PositionService positionService;

    private CalendarService service;

    @BeforeEach
    void setUp() {
        service = new CalendarService(repository, positionService);
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
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Madrid"));

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
}
