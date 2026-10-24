package com.swiftlabs.invoicing.issuer;

import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.dashboard.DashboardService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
    private final DashboardService dashboardService;

    public IssuerController(IssuerService issuerService, DashboardService dashboardService) {
        this.issuerService = issuerService;
        this.dashboardService = dashboardService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('VIEW_ISSUER')")
    public String editForm(Model model, Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            Issuer issuer = issuerService.getIssuer();
            // After a failed save the submitted form comes back as a flash attribute
            if (!model.containsAttribute("issuer")) {
                model.addAttribute("issuer", IssuerForm.from(issuer));
            }
            model.addAttribute("stats", dashboardService.stats(null));
            model.addAttribute("canEdit", authentication.getAuthorities().stream()
                .anyMatch(a -> "EDIT_ISSUER".equals(a.getAuthority())));
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
            issuerService.save(form);
            redirectAttributes.addFlashAttribute("success", "Profil émetteur enregistré.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            redirectAttributes.addFlashAttribute("issuer", form);
        }
        return "redirect:/settings/issuer";
    }
}
