package com.swiftlabs.invoicing.company;

import java.util.function.Supplier;

/**
 * The company the current thread works in: its schema is the one the database connections point to. Set for each
 * request from the signed-in user's company (CompanyFilter), and around each company by the background jobs
 * ({@link #runAs}). {@link CompanySchemas#SHARED} means no company: only the shared tables can be reached.
 *
 * A thread with nothing set (anonymous pages, background jobs) works in the first company, until those learn which
 * company they serve.
 */
public final class CompanyContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private CompanyContext() {
    }

    /** The schema of the current company, or {@link CompanySchemas#SHARED} when there is none. */
    public static String currentSchema() {
        String schema = CURRENT.get();
        return schema != null ? schema : CompanySchemas.FIRST;
    }

    /** The id of the current company; null when there is none. */
    public static Long currentCompanyId() {
        return CompanySchemas.idOf(currentSchema());
    }

    /** Runs {@code work} in the given company, then restores the previous one (the database session must be new). */
    public static <T> T runAs(String schema, Supplier<T> work) {
        try (Scope ignored = open(schema)) {
            return work.get();
        }
    }

    public static void runAs(String schema, Runnable work) {
        runAs(schema, () -> {
            work.run();
            return null;
        });
    }

    /** Works in {@code schema} until the scope is closed (try-with-resources), for code that throws checked exceptions. */
    public static Scope open(String schema) {
        String previous = CURRENT.get();
        CURRENT.set(CompanySchemas.checked(schema));
        return () -> {
            if (previous != null) {
                CURRENT.set(previous);
            } else {
                CURRENT.remove();
            }
        };
    }

    /** Closing restores the previous company; it throws nothing. */
    public interface Scope extends AutoCloseable {

        @Override
        void close();
    }
}
