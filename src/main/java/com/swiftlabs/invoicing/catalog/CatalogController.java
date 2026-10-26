package com.swiftlabs.invoicing.catalog;

import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.common.CommonCodes;
import com.swiftlabs.invoicing.common.Endpoints;
import com.swiftlabs.invoicing.common.FormErrors;
import com.swiftlabs.invoicing.common.MessageProperties;
import com.swiftlabs.invoicing.common.Messages;
import com.swiftlabs.invoicing.common.Privileges;
import com.swiftlabs.invoicing.config.InvoiceProperties;
import com.swiftlabs.invoicing.issuer.IssuerService;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

/** The catalogue: read by whoever writes invoices, changed by whoever manages it. */
@Controller
@RequestMapping(Endpoints.CATALOG)
public class CatalogController {

    private final CatalogService catalogService;
    private final IssuerService issuerService;
    private final InvoiceProperties properties;
    private final Messages messages;

    public CatalogController(CatalogService catalogService, IssuerService issuerService, InvoiceProperties properties,
                             Messages messages) {
        this.catalogService = catalogService;
        this.issuerService = issuerService;
        this.properties = properties;
        this.messages = messages;
    }

    @GetMapping
    @PreAuthorize(Privileges.Has.MANAGE_CATALOG + " or " + Privileges.Has.CREATE_INVOICE)
    public String list(Model model) {
        model.addAttribute(CommonCodes.Attributes.ITEMS, catalogService.findAll());
        model.addAttribute(CommonCodes.Attributes.COMPANY, issuerService.getIssuer().isCompany());
        return CommonCodes.Views.CATALOG_LIST;
    }

    @GetMapping(Endpoints.CATALOG_NEW)
    @PreAuthorize(Privileges.Has.MANAGE_CATALOG)
    public String newForm(Model model) {
        CatalogItemForm form = new CatalogItemForm();
        // A company's usual rate first (19 %), none for an auto-entrepreneur
        form.setVatRate(issuerService.getIssuer().isCompany() ? properties.vatRates().get(0) : BigDecimal.ZERO);
        model.addAttribute(CommonCodes.Attributes.ITEM, form);
        return formView(model);
    }

    @GetMapping(Endpoints.CATALOG_EDIT)
    @PreAuthorize(Privileges.Has.MANAGE_CATALOG)
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute(CommonCodes.Attributes.ITEM, CatalogItemForm.from(catalogService.findById(id)));
        return formView(model);
    }

    @PostMapping(Endpoints.CATALOG_SAVE)
    @PreAuthorize(Privileges.Has.MANAGE_CATALOG)
    public String save(@Valid @ModelAttribute(CommonCodes.Attributes.ITEM) CatalogItemForm form, BindingResult result, Model model,
                       RedirectAttributes redirectAttributes) {
        if (!result.hasErrors()) {
            try {
                CatalogItem item = catalogService.save(form);
                redirectAttributes.addFlashAttribute(CommonCodes.Attributes.SUCCESS,
                    messages.get(MessageProperties.CATALOG_SAVED, item.getName()));
                return Endpoints.REDIRECT + Endpoints.CATALOG;
            } catch (BusinessException ex) {
                FormErrors.reject(result, ex);
            }
        }
        return formView(model);
    }

    @PostMapping(Endpoints.CATALOG_DELETE)
    @PreAuthorize(Privileges.Has.MANAGE_CATALOG)
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            catalogService.delete(id);
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.SUCCESS, messages.get(MessageProperties.CATALOG_DELETED));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.ERROR, messages.of(ex));
        }
        return Endpoints.REDIRECT + Endpoints.CATALOG;
    }

    private String formView(Model model) {
        model.addAttribute(CommonCodes.Attributes.COMPANY, issuerService.getIssuer().isCompany());
        model.addAttribute(CommonCodes.Attributes.VAT_RATES, properties.vatRates());
        return CommonCodes.Views.CATALOG_FORM;
    }
}
