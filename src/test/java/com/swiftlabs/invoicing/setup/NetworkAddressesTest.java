package com.swiftlabs.invoicing.setup;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NetworkAddressesTest {

    @Test
    void onPremiseTheComputerIsReachableByNameOrLocalAddress() {
        assertThat(new NetworkAddresses(true, 7070).urls())
            .isNotEmpty()
            .allMatch(url -> url.startsWith("http://") && url.endsWith(":7070"));
    }

    @Test
    void elsewhereNothingIsShown() {
        assertThat(new NetworkAddresses(false, 7070).urls()).isEmpty();
    }
}
