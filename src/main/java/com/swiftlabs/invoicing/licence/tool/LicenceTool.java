package com.swiftlabs.invoicing.licence.tool;

import com.swiftlabs.invoicing.licence.Licence;
import com.swiftlabs.invoicing.licence.LicenceCodec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPair;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * The publisher's licence tool (never given to customers with the private key). Plain Java, no
 * Spring: run it from the build output, for example
 * <pre>
 *   java -cp target/classes com.swiftlabs.invoicing.licence.tool.LicenceTool keygen ~/.swift-invoicing/licence
 *   java -cp target/classes com.swiftlabs.invoicing.licence.tool.LicenceTool sign --key ~/.swift-invoicing/licence/licence-private.key \
 *        --id LIC-2026-0001 --company "Atlas Solutions" --nif 000016001234567 --users 3 --expires 2027-10-31
 *   java -cp target/classes com.swiftlabs.invoicing.licence.tool.LicenceTool verify --public src/main/resources/licence/public-key.txt SWIFT-...
 * </pre>
 */
public final class LicenceTool {

    private LicenceTool() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            usage();
            return;
        }
        switch (args[0]) {
            case "keygen" -> keygen(Path.of(args.length > 1 ? args[1] : "."));
            case "sign" -> sign(options(args));
            case "verify" -> verify(options(args), args[args.length - 1]);
            default -> usage();
        }
    }

    private static void keygen(Path directory) throws IOException {
        Path privateFile = directory.resolve("licence-private.key");
        Path publicFile = directory.resolve("licence-public.key");
        if (Files.exists(privateFile)) {
            System.err.println("Refused: " + privateFile + " already exists. Keys already given to customers depend on it.");
            System.exit(1);
        }
        Files.createDirectories(directory);
        KeyPair pair = LicenceCodec.newKeyPair();
        Files.writeString(privateFile, LicenceCodec.encodeKey(pair.getPrivate()) + "\n");
        Files.writeString(publicFile, LicenceCodec.encodeKey(pair.getPublic()) + "\n");
        try {
            Files.setPosixFilePermissions(privateFile, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException ignored) {
            // Windows: protect the folder by hand
        }
        System.out.println("Private key: " + privateFile + "  (keep it secret, back it up: it signs every licence)");
        System.out.println("Public key:  " + publicFile + "  (copy it to src/main/resources/licence/public-key.txt)");
    }

    private static void sign(Map<String, String> options) throws IOException {
        Licence licence = new Licence(required(options, "id"), required(options, "company"), required(options, "nif"),
            Integer.parseInt(required(options, "users")),
            LocalDate.parse(options.getOrDefault("issued", LocalDate.now().toString())), LocalDate.parse(required(options, "expires")));
        String key = LicenceCodec.sign(licence, LicenceCodec.privateKey(Files.readString(Path.of(required(options, "key")))));
        System.out.println(key);
    }

    private static void verify(Map<String, String> options, String key) throws IOException {
        Licence licence = LicenceCodec.verify(key, LicenceCodec.publicKey(Files.readString(Path.of(required(options, "public")))));
        System.out.println("Valid: " + licence);
    }

    private static Map<String, String> options(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (int i = 1; i + 1 < args.length; i++) {
            if (args[i].startsWith("--")) {
                options.put(args[i].substring(2), args[++i]);
            }
        }
        return options;
    }

    private static String required(Map<String, String> options, String name) {
        String value = options.get(name);
        if (value == null || value.isBlank()) {
            System.err.println("Missing --" + name);
            usage();
            System.exit(1);
        }
        return value;
    }

    private static void usage() {
        System.err.println("""
            Usage:
              keygen <directory>
              sign --key <private key file> --id <LIC-2026-0001> --company <name> --nif <NIF> --users <n> --expires <yyyy-mm-dd> [--issued <yyyy-mm-dd>]
              verify --public <public key file> <licence key>""");
    }
}
