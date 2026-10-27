package com.swiftlabs.invoicing.company;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

/** Tells Hibernate which company a new session works in: the one of the current thread. */
class CurrentCompanyResolver implements CurrentTenantIdentifierResolver<String> {

    @Override
    public String resolveCurrentTenantIdentifier() {
        return CompanyContext.currentSchema();
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }
}
