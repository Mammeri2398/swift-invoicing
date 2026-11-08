package com.swiftlabs.invoicing.access.auth;

import com.swiftlabs.invoicing.setup.NetworkAddresses;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** On-premise (no app.base-url), the reset link only uses an address of this computer, never one sent by the browser. */
class ResetLinkAddressTest {

    private final NetworkAddresses addresses = new NetworkAddresses(true, 7070);

    @Test
    void thisComputersOwnAddressIsKept() {
        assertThat(PasswordResetService.linkOrigin("http://localhost:7070", addresses)).isEqualTo("http://localhost:7070");
        for (String own : addresses.urls()) {
            assertThat(PasswordResetService.linkOrigin(own, addresses)).isEqualTo(own);
        }
    }

    @Test
    void anotherMachinesNameFromTheHostHeaderIsIgnored() {
        String origin = PasswordResetService.linkOrigin("http://attacker-pc:7070", addresses);
        assertThat(origin).doesNotContain("attacker").isEqualTo(addresses.preferredUrl());
        assertThat(PasswordResetService.linkOrigin("http://localhost:8080", addresses)).isEqualTo(addresses.preferredUrl());
    }
}
