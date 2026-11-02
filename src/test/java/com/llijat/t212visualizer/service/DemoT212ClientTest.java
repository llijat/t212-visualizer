package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.T212AccountSummaryDto;
import com.llijat.t212visualizer.dto.T212TransactionDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DemoT212ClientTest {

    private final DemoT212Client client = new DemoT212Client();

    private BigDecimal sumOfTypes(String... types) {
        BigDecimal total = BigDecimal.ZERO;
        for (T212TransactionDto dto : client.fetchTransactions("cualquiera").items()) {
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
        BigDecimal cashback = new BigDecimal("0.69"); // el único DEPOSIT de menos de 5 €

        // Lo mismo que calcula la página: valor total menos depositado neto
        BigDecimal netDeposits = sumOfTypes("DEPOSIT", "WITHDRAW").subtract(cashback);
        BigDecimal totalReturn = summary.totalValue().subtract(netDeposits);

        // Lo ganado con las abiertas y las vendidas, más el cashback, los intereses y las comisiones
        BigDecimal parts = summary.investments().unrealizedProfitLoss()
                .add(summary.investments().realizedProfitLoss())
                .add(cashback)
                .add(sumOfTypes("INTEREST_ON_FREE_CASH", "FEE"));

        assertEquals(0, parts.compareTo(totalReturn));
    }

    @Test
    void transactions_comeInOnePage() {
        assertNull(client.fetchTransactions("cualquiera").nextPagePath());
    }
}
