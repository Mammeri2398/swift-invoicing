package com.swiftlabs.invoicing.service;

import com.swiftlabs.invoicing.domain.Issuer;
import com.swiftlabs.invoicing.exception.BusinessException;
import com.swiftlabs.invoicing.repository.IssuerRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class IssuerService {

    private final IssuerRepository issuerRepository;

    @Value("${invoice.vat-mention:TVA non applicable}")
    private String vatMention;

    @Value("${invoice.annual-ceiling:5000000}")
    private Long annualCeiling;

    public IssuerService(IssuerRepository issuerRepository) {
        this.issuerRepository = issuerRepository;
    }

    public Issuer getIssuer() {
        return issuerRepository.findById(1L).orElseThrow(() ->
            new BusinessException("Profil émetteur introuvable. Veuillez le configurer dans les paramètres."));
    }

    @Transactional
    public Issuer save(Issuer issuer) {
        if (issuer.getId() == null) {
            issuer.setId(1L);
        }
        Issuer existing = issuerRepository.findById(1L).orElse(null);
        if (existing != null && !existing.getId().equals(issuer.getId())) {
            // keep single row
            issuer.setId(1L);
        }
        return issuerRepository.save(issuer);
    }

    public String getVatMention() {
        return vatMention;
    }

    public Long getAnnualCeiling() {
        return annualCeiling;
    }
}
