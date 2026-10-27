package com.swiftlabs.invoicing.company;

import java.util.regex.Pattern;

/**
 * Where the data lives. Each company has its own schema, {@code company_<id>}, holding its tables (db/tenant); the
 * shared schema holds what the whole installation shares: users, profiles, privileges, licence, the companies and
 * who may access them (db/migration).
 */
public final class CompanySchemas {

    private CompanySchemas() {
    }

    /** The shared tables; named on their entities ({@code @Table(schema = CompanySchemas.SHARED)}). */
    public static final String SHARED = "public";

    /** The first company: the data of an installation from before there could be several. */
    public static final long FIRST_ID = 1L;
    public static final String FIRST = "company_" + FIRST_ID;

    /** Schema names are written into SQL (search path, migrations): nothing but this shape is ever accepted. */
    private static final Pattern NAME = Pattern.compile("company_[0-9]{1,18}");

    public static String of(long companyId) {
        return "company_" + companyId;
    }

    /** The company's id from its schema; null for the shared schema. */
    public static Long idOf(String schema) {
        return SHARED.equals(schema) ? null : Long.valueOf(checked(schema).substring("company_".length()));
    }

    /** A company's schema, or the shared one (no company): never anything else. */
    static String checked(String schema) {
        if (SHARED.equals(schema)) {
            return schema;
        }
        if (schema == null || !NAME.matcher(schema).matches()) {
            throw new IllegalArgumentException("Not a company schema: " + schema);
        }
        return schema;
    }
}
