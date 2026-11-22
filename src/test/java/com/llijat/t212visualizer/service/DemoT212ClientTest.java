package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.T212AccountSummaryDto;
import com.llijat.t212visualizer.dto.T212DividendDto;
import com.llijat.t212visualizer.dto.T212TransactionDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoT212ClientTest {

    private final DemoT212Client client = new DemoT212Client();

    private BigDecimal sumOfTypes(String... types) {
        BigDecimal total = BigDecimal.ZERO;
        for (T212TransactionDto dto : client.fetchTransactions("any").items()) {
            for (String type : types) {
                if (dto.type().equals(type)) total = total.add(dto.amount());
            }
        }
        return total;
    }

    @Test
    void summary_totalValueIsCashPlusInvestments() {
        T212AccountSummaryDto summary = client.fetchAccountSummary();

        BigDecimal expected = summary.cash().availableToTrade().add(summary.investments().currentValue());

        assertEquals(0, expected.compareTo(summary.totalValue()));
    }

    @Test
    void summary_totalReturnMatchesItsParts() {
        T212AccountSummaryDto summary = client.fetchAccountSummary();
        BigDecimal cashback = new BigDecimal("0.69"); // the only DEPOSIT under 5 €

        // What the page calculates: total value minus net deposits
        BigDecimal netDeposits = sumOfTypes("DEPOSIT", "WITHDRAW").subtract(cashback);
        BigDecimal totalReturn = summary.totalValue().subtract(netDeposits);

        // Gains on open and closed positions, plus cashback, interest and fees
        BigDecimal parts = summary.investments().unrealizedProfitLoss()
                .add(summary.investments().realizedProfitLoss())
                .add(cashback)
                .add(sumOfTypes("INTEREST_ON_FREE_CASH", "FEE"));

        assertEquals(0, parts.compareTo(totalReturn));
    }

    @Test
    void transactions_comeInOnePage() {
        assertNull(client.fetchTransactions("any").nextPagePath());
    }
    // ===== Dividends received =====

    @Test
    void dividends_fallOnTheDaysTheDemoAnnouncementsGive_threeQuartersBack() {
        List<T212DividendDto> globex = client.fetchDividends("any").items().stream()
                .filter(payment -> payment.ticker().equals("GLBX_US_EQ")).toList();
        LocalDate next = DemoAlphaVantageClient.nextPayment("GLBX");

        assertEquals(List.of(next.minusMonths(3), next.minusMonths(6), next.minusMonths(9)),
                globex.stream().map(T212DividendDto::paidDay).toList());
    }

    @Test
    void dividends_areInEuros_afterTheUsWithholding() {
        // Globex: 0.11 $ a share x 4.2 shares x 0.85 / 1.08 = 0.36 €
        T212DividendDto globex = client.fetchDividends("any").items().stream()
                .filter(payment -> payment.ticker().equals("GLBX_US_EQ")).findFirst().orElseThrow();

        assertEquals(new BigDecimal("0.11"), globex.grossAmountPerShare());
        assertEquals(new BigDecimal("0.36"), globex.amountInEuro());
        assertEquals(new BigDecimal("4.2"), globex.quantity());
    }

    @Test
    void dividends_leaveOutTheCompaniesThatNeverPay_andTheEuropeanOne() {
        assertTrue(client.fetchDividends("any").items().stream()
                .noneMatch(payment -> payment.ticker().equals("HOOL_US_EQ") || payment.ticker().equals("WAYN_EU_EQ")));
    }

    @Test
    void dividends_comeNewestFirst_inOnePage() {
        List<LocalDate> days = client.fetchDividends("any").items().stream().map(T212DividendDto::paidDay).toList();

        assertEquals(days.stream().sorted(java.util.Comparator.reverseOrder()).toList(), days);
        assertNull(client.fetchDividends("any").nextPagePath());
    }
}
