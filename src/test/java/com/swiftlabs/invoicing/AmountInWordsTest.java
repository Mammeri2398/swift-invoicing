package com.swiftlabs.invoicing;

import com.swiftlabs.invoicing.util.AmountInWords;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class AmountInWordsTest {

    @Test
    void convertKnownValues() {
        assertThat(AmountInWords.convert(BigDecimal.ZERO)).contains("zéro dinars algériens.");
        assertThat(AmountInWords.convert(new BigDecimal("1"))).contains("un dinar algérien.");
        assertThat(AmountInWords.convert(new BigDecimal("21"))).contains("vingt et un dinars algériens.");
        assertThat(AmountInWords.convert(new BigDecimal("71"))).contains("soixante et onze dinars algériens.");
        assertThat(AmountInWords.convert(new BigDecimal("80"))).contains("quatre-vingts dinars algériens.");
        assertThat(AmountInWords.convert(new BigDecimal("100"))).contains("cent dinars algériens.");
        assertThat(AmountInWords.convert(new BigDecimal("1000"))).contains("mille dinars algériens.");
        assertThat(AmountInWords.convert(new BigDecimal("1234.56")))
            .isEqualTo("Arrêtée la présente facture à la somme de : mille deux cent trente-quatre dinars algériens et cinquante-six centimes.");
    }
}
