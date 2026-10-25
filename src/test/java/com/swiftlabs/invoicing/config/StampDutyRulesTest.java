package com.swiftlabs.invoicing.config;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class StampDutyRulesTest {

    /** The defaults: 1 % up to 30 000 DA, 1.5 % up to 100 000 DA, 2 % above, at least 5 DA. */
    private final InvoiceProperties.StampDuty defaults = new InvoiceProperties.StampDuty(null, null, null, null);

    private BigDecimal duty(String amount) {
        return defaults.compute(new BigDecimal(amount));
    }

    @Test
    void theRateOfTheBracketAppliesToTheWholeAmountRoundedUpToTheDinar() {
        assertThat(duty("10000.00")).isEqualByComparingTo("100");
        assertThat(duty("30000.00")).isEqualByComparingTo("300");
        // 30 000,01 x 1.5 % = 450.0002, rounded up
        assertThat(duty("30000.01")).isEqualByComparingTo("451");
        assertThat(duty("100000.00")).isEqualByComparingTo("1500");
        assertThat(duty("150000.00")).isEqualByComparingTo("3000");
        assertThat(duty("1190.00")).isEqualByComparingTo("12");
    }

    @Test
    void minimumAndOptionalMaximum() {
        assertThat(duty("200.00")).isEqualByComparingTo("5");
        assertThat(duty("0")).isEqualByComparingTo("0");
        InvoiceProperties.StampDuty capped = new InvoiceProperties.StampDuty(true, null, null, new BigDecimal("2500"));
        assertThat(capped.compute(new BigDecimal("200000"))).isEqualByComparingTo("2500");
    }

    @Test
    void canBeTurnedOff() {
        assertThat(new InvoiceProperties.StampDuty(false, null, null, null).compute(new BigDecimal("10000"))).isEqualByComparingTo("0");
    }
}
