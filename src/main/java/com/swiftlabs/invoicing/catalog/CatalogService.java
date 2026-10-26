package com.swiftlabs.invoicing.catalog;

import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.common.MessageProperties;
import com.swiftlabs.invoicing.common.ResourceNotFoundException;
import com.swiftlabs.invoicing.config.InvoiceProperties;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** The products and services of the catalogue. Deleting one never touches the documents that used it. */
@Service
@Transactional(readOnly = true)
public class CatalogService {

    private final CatalogItemRepository repository;
    private final InvoiceProperties properties;

    public CatalogService(CatalogItemRepository repository, InvoiceProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /** By name, as offered on the invoice form. */
    public List<CatalogItem> findAll() {
        return repository.findAllByOrderByNameAsc();
    }

    public CatalogItem findById(Long id) {
        return repository.findById(id).orElseThrow(() ->
            new ResourceNotFoundException(MessageProperties.CATALOG_ERROR_NOT_FOUND, String.valueOf(id)));
    }

    @Transactional
    public CatalogItem save(CatalogItemForm form) {
        String name = form.getName().trim();
        boolean taken = form.getId() == null
            ? repository.existsByNameIgnoreCase(name)
            : repository.existsByNameIgnoreCaseAndIdNot(name, form.getId());
        if (taken) {
            throw BusinessException.onField("name", MessageProperties.CATALOG_ERROR_NAME_USED, name);
        }
        BigDecimal vatRate = form.getVatRate() == null ? BigDecimal.ZERO : form.getVatRate();
        if (properties.vatRates().stream().noneMatch(rate -> rate.compareTo(vatRate) == 0)) {
            throw BusinessException.onField("vatRate", MessageProperties.CATALOG_ERROR_VAT_RATE);
        }
        CatalogItem item = form.getId() != null ? findById(form.getId()) : new CatalogItem();
        item.setName(name);
        item.setUnit(form.getUnit() == null || form.getUnit().isBlank() ? null : form.getUnit().trim());
        item.setUnitPrice(form.getUnitPrice());
        item.setVatRate(vatRate);
        return repository.save(item);
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(findById(id));
    }
}
