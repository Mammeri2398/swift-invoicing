package com.swiftlabs.invoicing.company;

import java.util.function.Supplier;

/**
 * The company the current thread works in: its schema is the one the database connections point to. Set for a
 * request by the web layer, and around each company by the background jobs ({@link #runAs}).
 *
 * Until a company can be chosen at sign-in, a thread without one works in the first company.
 */
public final class CompanyContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private CompanyContext() {
    }

    /** The schema of the current company. */
    public static String currentSchema() {
        String schema = CURRENT.get();
        return schema != null ? schema : CompanySchemas.FIRST;
    }

    /** Runs {@code work} in the given company, then restores the previous one (the database session must be new). */
    public static <T> T runAs(String schema, Supplier<T> work) {
        String previous = CURRENT.get();
        CURRENT.set(CompanySchemas.checked(schema));
        try {
            return work.get();
        } finally {
            if (previous != null) {
                CURRENT.set(previous);
            } else {
                CURRENT.remove();
            }
        }
    }

    public static void runAs(String schema, Runnable work) {
        runAs(schema, () -> {
            work.run();
            return null;
        });
    }
}
