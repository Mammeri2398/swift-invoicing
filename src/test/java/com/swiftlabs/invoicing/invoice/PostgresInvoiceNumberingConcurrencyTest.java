package com.swiftlabs.invoicing.invoice;

import com.swiftlabs.invoicing.PostgresTestcontainersConfiguration;

import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The concurrent numbering tests on real PostgreSQL (row locks and ON CONFLICT behave as in
 * production, not as emulated by H2). Skipped when Docker is not available.
 */
@Import(PostgresTestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class PostgresInvoiceNumberingConcurrencyTest extends InvoiceNumberingConcurrencyTest {
}
