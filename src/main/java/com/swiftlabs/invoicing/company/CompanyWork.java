package com.swiftlabs.invoicing.company;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * Runs work in another company, in its own transaction and database session. A database session belongs to the
 * company it was opened in: a request keeps one open from its start (open-in-view) in the signed-in user's company,
 * so reusing it would read and write that company. The session of the caller is set aside meanwhile, and restored.
 *
 * The only way to work in another company than the current one; {@link CompanyContext#runAs} alone is for threads
 * with no session open (background jobs).
 */
@Component
public class CompanyWork {

    private final EntityManagerFactory entityManagerFactory;
    private final TransactionTemplate required;
    private final TransactionTemplate requiresNew;

    public CompanyWork(EntityManagerFactory entityManagerFactory, PlatformTransactionManager transactionManager) {
        this.entityManagerFactory = entityManagerFactory;
        this.required = new TransactionTemplate(transactionManager);
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public <T> T inCompany(String schema, Supplier<T> work) {
        return CompanyContext.runAs(schema, () -> {
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                // A transaction suspended by REQUIRES_NEW takes its session with it
                return requiresNew.execute(status -> work.get());
            }
            // No transaction, but maybe the request's session (open-in-view): set aside for a new one
            Object requestSession = TransactionSynchronizationManager.hasResource(entityManagerFactory)
                ? TransactionSynchronizationManager.unbindResource(entityManagerFactory) : null;
            try {
                return required.execute(status -> work.get());
            } finally {
                if (requestSession != null) {
                    TransactionSynchronizationManager.bindResource(entityManagerFactory, requestSession);
                }
            }
        });
    }

    public void inCompany(String schema, Runnable work) {
        inCompany(schema, () -> {
            work.run();
            return null;
        });
    }
}
