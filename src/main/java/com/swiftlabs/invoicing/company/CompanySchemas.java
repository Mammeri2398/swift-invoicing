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
    public static final String FIRST = "company_1";

    /** Schema names are written into SQL (search path, migrations): nothing but this shape is ever accepted. */
    private static final Pattern NAME = Pattern.compile("company_[0-9]{1,18}");

    public static String of(long companyId) {
        return "company_" + companyId;
    }

    static String checked(String schema) {
        if (schema == null || !NAME.matcher(schema).matches()) {
            throw new IllegalArgumentException("Not a company schema: " + schema);
        }
        return schema;
    }
}
