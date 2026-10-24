package com.swiftlabs.invoicing.issuer;

import com.swiftlabs.invoicing.common.BusinessException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class IssuerService {

    /** The issuer profile is a single row. */
    private static final long ISSUER_ID = 1L;

    private final IssuerRepository issuerRepository;

    public IssuerService(IssuerRepository issuerRepository) {
        this.issuerRepository = issuerRepository;
    }

    public Issuer getIssuer() {
        return issuerRepository.findById(ISSUER_ID).orElseThrow(() ->
            new BusinessException("Profil émetteur introuvable. Veuillez le configurer dans les paramètres."));
    }

    @Transactional
    public Issuer save(IssuerForm form) {
        // Validate before touching the managed row, so a rejected form never gets flushed
        requireText(form.getFullName(), "Le nom complet est obligatoire.");
        requireText(form.getAeCardNumber(), "Le numéro de carte auto-entrepreneur est obligatoire.");
        requireText(form.getNif(), "Le NIF est obligatoire.");

        // Fields not on the form (logo) are kept
        Issuer issuer = getIssuer();
        issuer.setFullName(form.getFullName());
        issuer.setTradeName(form.getTradeName());
        issuer.setAddress(form.getAddress());
        issuer.setCity(form.getCity());
        issuer.setPhone(form.getPhone());
        issuer.setEmail(form.getEmail());
        issuer.setAeCardNumber(form.getAeCardNumber());
        issuer.setNif(form.getNif());
        issuer.setNis(form.getNis());
        issuer.setActivityDescription(form.getActivityDescription());
        issuer.setBankOrCcpAccount(form.getBankOrCcpAccount());
        return issuerRepository.save(issuer);
    }

    private void requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new BusinessException(message);
        }
    }
}
