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

    /**
     * Whether an address typed in the browser (scheme://host:port) is one of this computer's own: one of
     * {@link #urls()}, or this computer itself (localhost). Anything else is another machine's name.
     */
    public boolean isOwn(String origin) {
        String normalized = origin.toLowerCase(Locale.ROOT);
        return normalized.equals("http://localhost:" + port) || normalized.equals("http://127.0.0.1:" + port)
            || urls().stream().anyMatch(url -> url.toLowerCase(Locale.ROOT).equals(normalized));
    }

    /** The address to give when the one typed cannot be trusted: the computer's name, else localhost. */
    public String preferredUrl() {
        List<String> urls = urls();
        return urls.isEmpty() ? "http://localhost:" + port : urls.get(0);
    }

    static boolean isVirtualAdapter(NetworkInterface network) {
        String name = network.getName().toLowerCase(Locale.ROOT);
        String description = network.getDisplayName() != null ? network.getDisplayName().toLowerCase(Locale.ROOT) : "";
        return VIRTUAL_NAME_PREFIXES.stream().anyMatch(name::startsWith) || VIRTUAL_DESCRIPTIONS.stream().anyMatch(description::contains);
    }
}
