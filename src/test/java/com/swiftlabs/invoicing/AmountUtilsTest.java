package com.swiftlabs.invoicing;

import com.swiftlabs.invoicing.util.AmountUtils;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class AmountUtilsTest {

    @Test
    void lineTotalRoundingHalfUp() {
        // 1.000 * 0.005 = 0.005 -> arrondi HALF_UP = 0,01
        assertThat(AmountUtils.lineTotal(new BigDecimal("1.000"), new BigDecimal("0.005"))).isEqualByComparingTo(new BigDecimal("0.01"));
        // 1.000 * 0.004 = 0.004 -> arrondi HALF_UP = 0,00
        assertThat(AmountUtils.lineTotal(new BigDecimal("1.000"), new BigDecimal("0.004"))).isEqualByComparingTo(BigDecimal.ZERO);
        // 2.000 * 100.00 = 200,00
        assertThat(AmountUtils.lineTotal(new BigDecimal("2.000"), new BigDecimal("100.00"))).isEqualByComparingTo(new BigDecimal("200.00"));
        // 3.000 * 0.335 = 1,005 -> 1,01
        assertThat(AmountUtils.lineTotal(new BigDecimal("3.000"), new BigDecimal("0.335"))).isEqualByComparingTo(new BigDecimal("1.01"));
    }

    @Test
    void sumLines() {
        List<BigDecimal> totals = new ArrayList<BigDecimal>();
        totals.add(new BigDecimal("100.00"));
        totals.add(new BigDecimal("250.50"));
        totals.add(new BigDecimal("7.25"));
        assertThat(AmountUtils.sumLines(totals)).isEqualByComparingTo(new BigDecimal("357.75"));

        List<BigDecimal> empty = new ArrayList<BigDecimal>();
        assertThat(AmountUtils.sumLines(empty)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void formatMoney() {
        assertThat(AmountUtils.format(new BigDecimal("1234567.50"))).isEqualTo("1 234 567,50 DA");
        assertThat(AmountUtils.format(BigDecimal.ZERO)).isEqualTo("0,00 DA");
        assertThat(AmountUtils.format(null)).isEqualTo("0,00 DA");
    }
}
