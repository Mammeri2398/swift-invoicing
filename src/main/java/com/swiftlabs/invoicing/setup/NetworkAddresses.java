package com.swiftlabs.invoicing.setup;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The addresses the other PCs of the office type to reach an on-premise install: the computer's
 * name and its local network addresses, with the port. Empty outside an on-premise install.
 */
@Component
public class NetworkAddresses {

    /*
     * Adapters the other PCs cannot reach: containers, virtual machines and VPNs. Short system names
     * are matched at the start of the interface name, product names anywhere in its description.
     */
    private static final List<String> VIRTUAL_NAME_PREFIXES = List.of("docker", "br-", "veth", "virbr", "vmnet", "vboxnet",
        "tun", "tap", "zt", "wsl");
    private static final List<String> VIRTUAL_DESCRIPTIONS = List.of("virtualbox", "vmware", "hyper-v", "vethernet",
        "tailscale", "wireguard");

    private final boolean onPremise;
    private final int port;

    public NetworkAddresses(@Value("${app.setup.wizard:false}") boolean onPremise, @Value("${server.port:8080}") int port) {
        this.onPremise = onPremise;
        this.port = port;
    }

    public List<String> urls() {
        if (!onPremise) {
            return List.of();
        }
        List<String> urls = new ArrayList<>();
        try {
            urls.add("http://" + InetAddress.getLocalHost().getHostName() + ":" + port);
        } catch (UnknownHostException ignored) {
            // No name: the IP addresses below still work
        }
        try {
            for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!network.isUp() || network.isLoopback() || network.isVirtual() || isVirtualAdapter(network)) {
                    continue;
                }
                for (InetAddress address : Collections.list(network.getInetAddresses())) {
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                        urls.add("http://" + address.getHostAddress() + ":" + port);
                    }
                }
            }
        } catch (SocketException ignored) {
            // Interfaces not readable: the name above is enough
        }
        return urls;
    }

    static boolean isVirtualAdapter(NetworkInterface network) {
        String name = network.getName().toLowerCase(Locale.ROOT);
        String description = network.getDisplayName() != null ? network.getDisplayName().toLowerCase(Locale.ROOT) : "";
        return VIRTUAL_NAME_PREFIXES.stream().anyMatch(name::startsWith) || VIRTUAL_DESCRIPTIONS.stream().anyMatch(description::contains);
    }
}
