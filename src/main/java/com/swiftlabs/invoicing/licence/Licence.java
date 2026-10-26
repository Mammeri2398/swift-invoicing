package com.swiftlabs.invoicing.licence;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Properties;

/**
 * What a licence grants: one company (identified by its NIF), a number of user accounts, until a
 * date. Written as simple "key=value" lines, then signed (see LicenceCodec).
 */
public record Licence(String id, String company, String nif, int users, LocalDate issued, LocalDate expires) {

    public Licence {
        if (id == null || id.isBlank() || company == null || company.isBlank() || nif == null || nif.isBlank()
                || users < 1 || issued == null || expires == null || expires.isBefore(issued)) {
            throw new IllegalArgumentException("Incomplete licence: id, company, nif, users >= 1, issued and expires are required");
        }
    }

    /** The signed text: one "key=value" line per field, in a fixed order. */
    String payload() {
        return "id=" + id + "\n" + "company=" + escape(company) + "\n" + "nif=" + nif + "\n" + "users=" + users + "\n"
            + "issued=" + issued + "\n" + "expires=" + expires + "\n";
    }

    static Licence parse(String payload) {
        Properties fields = new Properties();
        try {
            fields.load(new StringReader(payload));
            return new Licence(fields.getProperty("id"), fields.getProperty("company"), fields.getProperty("nif"),
                Integer.parseInt(fields.getProperty("users", "0")), LocalDate.parse(fields.getProperty("issued", "")),
                LocalDate.parse(fields.getProperty("expires", "")));
        } catch (IOException | NumberFormatException | DateTimeParseException ex) {
            throw new IllegalArgumentException("Unreadable licence content", ex);
        }
    }

    /** Company names may contain characters that are special in .properties files (=, :, \, accents). */
    private static String escape(String value) {
        Properties one = new Properties();
        one.setProperty("v", value);
        StringWriter out = new StringWriter();
        try {
            one.store(out, null);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return out.toString().lines().filter(line -> line.startsWith("v=")).findFirst().orElseThrow().substring(2);
    }
}
