package com.swiftlabs.invoicing.company;

import com.swiftlabs.invoicing.access.profile.Profile;
import com.swiftlabs.invoicing.access.user.User;
import com.swiftlabs.invoicing.access.user.UserRepository;
import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.common.MessageProperties;
import com.swiftlabs.invoicing.common.ResourceNotFoundException;
import com.swiftlabs.invoicing.issuer.IssuerForm;
import com.swiftlabs.invoicing.issuer.IssuerService;
import com.swiftlabs.invoicing.licence.LicenceService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** The companies of the installation: creating one (with its own schema), renaming, deactivating. */
@Service
public class CompanyService {

    private static final Logger log = LoggerFactory.getLogger(CompanyService.class);

    private final CompanyRepository repository;
    private final CompanyAccessRepository accessRepository;
    private final CompanyAccessService accessService;
    private final CompanyMigrations migrations;
    private final IssuerService issuerService;
    private final LicenceService licenceService;
    private final UserRepository userRepository;
    private final CompanyWork companyWork;
    private final TransactionTemplate transaction;

    public CompanyService(CompanyRepository repository, CompanyAccessRepository accessRepository, CompanyAccessService accessService,
                          CompanyMigrations migrations, IssuerService issuerService, LicenceService licenceService,
                          UserRepository userRepository, CompanyWork companyWork, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.accessRepository = accessRepository;
        this.accessService = accessService;
        this.migrations = migrations;
        this.issuerService = issuerService;
        this.licenceService = licenceService;
        this.userRepository = userRepository;
        this.companyWork = companyWork;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public List<Company> findActive() {
        return repository.findByActiveTrueOrderByName();
    }

    /** Every company, active ones first, by name. */
    @Transactional(readOnly = true)
    public List<Company> findAll() {
        return repository.findAllByOrderByActiveDescNameAsc();
    }

    @Transactional(readOnly = true)
    public Company findById(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException(MessageProperties.COMPANY_ERROR_NOT_FOUND));
    }

    /** How many users may work in each company, by company id. */
    @Transactional(readOnly = true)
    public Map<Long, Long> userCounts() {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : accessRepository.countByCompany()) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    /**
     * A new company with its own, empty schema and nothing but its name (its details: see {@link #create(NewCompanyForm,
     * Long, Profile)}). The company row is committed before the schema is created: Flyway works on its own connections.
     */
    public Company create(String name) {
        licenceService.checkCompanyLimit(repository.countByActiveTrue() + 1);
        Company company = repository.saveAndFlush(new Company(name.trim()));
        company.setSchemaName(CompanySchemas.of(company.getId()));
        company = repository.saveAndFlush(company);
        migrations.migrate(company.getSchemaName());
        log.info("Company {} created ({})", company.getName(), company.getSchemaName());
        return company;
    }

    /**
     * A new company with its legal identifiers, opened to its creator with {@code profile} (the profile they have in
     * the company they work in). Identifiers refused by the rules of the Paramètres page remove the company again:
     * a company never exists with the placeholders of a new schema.
     */
    public Company create(NewCompanyForm form, Long creatorId, Profile profile) {
        Company company = create(form.getName());
        try {
            companyWork.inCompany(company.getSchemaName(), () -> issuerService.save(form.applyTo(IssuerForm.from(issuerService.getIssuer()))));
        } catch (RuntimeException ex) {
            remove(company);
            throw ex;
        }
        transaction.executeWithoutResult(status -> {
            User creator = userRepository.findById(creatorId).orElseThrow();
            accessService.grant(creator, company.getId(), profile);
        });
        return company;
    }

    /** The name in the company switcher (its invoices print the name of its Paramètres page). */
    @Transactional
    public Company rename(Long id, String name) {
        Company company = findById(id);
        company.setName(name.trim());
        return company;
    }

    /**
     * A deactivated company keeps all its data but nobody can open it any more; it can be activated again. The
     * company one works in and the last active one cannot be deactivated.
     */
    @Transactional
    public Company setActive(Long id, boolean active, Long currentCompanyId) {
        Company company = findById(id);
        if (active == company.isActive()) {
            return company;
        }
        if (active) {
            licenceService.checkCompanyLimit(repository.countByActiveTrue() + 1);
        } else if (Objects.equals(id, currentCompanyId)) {
            throw new BusinessException(MessageProperties.COMPANY_ERROR_DEACTIVATE_CURRENT);
        } else if (repository.countByActiveTrue() <= 1) {
            throw new BusinessException(MessageProperties.COMPANY_ERROR_LAST_ACTIVE);
        }
        company.setActive(active);
        return company;
    }

    /** Undoes a creation that failed: its schema, its row. */
    private void remove(Company company) {
        migrations.dropSchema(company.getSchemaName());
        transaction.executeWithoutResult(status -> repository.deleteById(company.getId()));
        log.warn("Company {} removed: its creation failed", company.getName());
    }
}
