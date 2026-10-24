package com.swiftlabs.invoicing.issuer;

import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.dashboard.DashboardService;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;

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
            model.addAttribute("hasLogo", issuer.hasLogo());
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

    /** The logo, for the preview on this page (the PDF embeds it directly). */
    @GetMapping("/logo")
    @PreAuthorize("hasAuthority('VIEW_ISSUER')")
    public ResponseEntity<byte[]> logo() {
        Issuer issuer = issuerService.getIssuer();
        if (!issuer.hasLogo()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(issuer.getLogoContentType()))
            .cacheControl(CacheControl.noCache().cachePrivate())
            .body(issuer.getLogoData());
    }

    @PostMapping("/logo")
    @PreAuthorize("hasAuthority('EDIT_ISSUER')")
    public String uploadLogo(@RequestParam("logo") MultipartFile file, RedirectAttributes redirectAttributes) {
        try {
            issuerService.updateLogo(file.getBytes());
            redirectAttributes.addFlashAttribute("success", "Logo enregistré : il sera imprimé sur les factures.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        } catch (IOException ex) {
            redirectAttributes.addFlashAttribute("error", "Le fichier n'a pas pu être lu.");
        }
        return "redirect:/settings/issuer";
    }

    @PostMapping("/logo/delete")
    @PreAuthorize("hasAuthority('EDIT_ISSUER')")
    public String removeLogo(RedirectAttributes redirectAttributes) {
        issuerService.removeLogo();
        redirectAttributes.addFlashAttribute("success", "Logo supprimé.");
        return "redirect:/settings/issuer";
    }
}
