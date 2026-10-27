package com.swiftlabs.invoicing.company;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** The companies of the installation. */
@Service
public class CompanyService {

    private final CompanyRepository repository;
    private final CompanyMigrations migrations;

    public CompanyService(CompanyRepository repository, CompanyMigrations migrations) {
        this.repository = repository;
        this.migrations = migrations;
    }

    @Transactional(readOnly = true)
    public List<Company> findActive() {
        return repository.findByActiveTrueOrderByName();
    }

    /**
     * A new company with its own, empty schema (its details are set by its setup). The company row is committed
     * before the schema is created: Flyway works on its own connections.
     */
    public Company create(String name) {
        Company company = repository.saveAndFlush(new Company(name.trim()));
        company.setSchemaName(CompanySchemas.of(company.getId()));
        company = repository.saveAndFlush(company);
        migrations.migrate(company.getSchemaName());
        return company;
    }
}
