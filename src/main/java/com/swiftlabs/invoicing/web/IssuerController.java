package com.swiftlabs.invoicing.web;

import com.swiftlabs.invoicing.domain.Issuer;
import com.swiftlabs.invoicing.dto.IssuerForm;
import com.swiftlabs.invoicing.exception.BusinessException;
import com.swiftlabs.invoicing.service.IssuerService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/settings/issuer")
public class IssuerController {

    private final IssuerService issuerService;

    public IssuerController(IssuerService issuerService) {
        this.issuerService = issuerService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('VIEW_ISSUER')")
    public String editForm(Model model, RedirectAttributes redirectAttributes) {
        try {
            Issuer issuer = issuerService.getIssuer();
            model.addAttribute("issuer", toForm(issuer));
            return "issuer/form";
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/";
        }
    }

    @PostMapping("/save")
    @PreAuthorize("hasAuthority('EDIT_ISSUER')")
    public String save(@ModelAttribute IssuerForm form, RedirectAttributes redirectAttributes) {
        try {
            Issuer issuer = toEntity(form);
            issuerService.save(issuer);
            redirectAttributes.addFlashAttribute("success", "Profil émetteur enregistré.");
            return "redirect:/settings/issuer";
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            redirectAttributes.addFlashAttribute("issuer", form);
            return "redirect:/settings/issuer";
        }
    }

    private IssuerForm toForm(Issuer issuer) {
        IssuerForm form = new IssuerForm();
        form.setId(issuer.getId());
        form.setFullName(issuer.getFullName());
        form.setTradeName(issuer.getTradeName());
        form.setAddress(issuer.getAddress());
        form.setCity(issuer.getCity());
        form.setPhone(issuer.getPhone());
        form.setEmail(issuer.getEmail());
        form.setAeCardNumber(issuer.getAeCardNumber());
        form.setNif(issuer.getNif());
        form.setNis(issuer.getNis());
        form.setActivityDescription(issuer.getActivityDescription());
        form.setBankOrCcpAccount(issuer.getBankOrCcpAccount());
        return form;
    }

    private Issuer toEntity(IssuerForm form) {
        Issuer issuer = new Issuer();
        if (form.getId() != null) {
            issuer.setId(form.getId());
        } else {
            issuer.setId(1L);
        }
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
        return issuer;
    }
}
