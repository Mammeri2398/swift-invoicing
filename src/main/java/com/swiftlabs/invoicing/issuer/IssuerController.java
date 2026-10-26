package com.swiftlabs.invoicing.issuer;

import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.common.CommonCodes;
import com.swiftlabs.invoicing.common.Endpoints;
import com.swiftlabs.invoicing.common.FormErrors;
import com.swiftlabs.invoicing.common.MessageProperties;
import com.swiftlabs.invoicing.common.Messages;
import com.swiftlabs.invoicing.common.Privileges;
import com.swiftlabs.invoicing.dashboard.DashboardService;
import com.swiftlabs.invoicing.setup.NetworkAddresses;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;

@Controller
@RequestMapping(Endpoints.ISSUER)
public class IssuerController {

    private final IssuerService issuerService;
    private final DashboardService dashboardService;
    private final NetworkAddresses networkAddresses;
    private final Messages messages;

    public IssuerController(IssuerService issuerService, DashboardService dashboardService, NetworkAddresses networkAddresses,
                            Messages messages) {
        this.issuerService = issuerService;
        this.dashboardService = dashboardService;
        this.networkAddresses = networkAddresses;
        this.messages = messages;
    }

    @GetMapping
    @PreAuthorize(Privileges.Has.VIEW_ISSUER)
    public String editForm(Model model, Authentication authentication) {
        model.addAttribute(CommonCodes.Attributes.ISSUER, IssuerForm.from(issuerService.getIssuer()));
        return formView(model, authentication);
    }

    /** Invalid input is shown again with the errors next to each field. */
    @PostMapping(Endpoints.ISSUER_SAVE)
    @PreAuthorize(Privileges.Has.EDIT_ISSUER)
    public String save(@Valid @ModelAttribute(CommonCodes.Attributes.ISSUER) IssuerForm form, BindingResult result, Model model,
                       Authentication authentication, RedirectAttributes redirectAttributes) {
        if (!result.hasErrors()) {
            try {
                issuerService.save(form);
                redirectAttributes.addFlashAttribute(CommonCodes.Attributes.SUCCESS, messages.get(MessageProperties.ISSUER_SAVED));
                return Endpoints.REDIRECT + Endpoints.ISSUER;
            } catch (BusinessException ex) {
                FormErrors.reject(result, ex);
            }
        }
        return formView(model, authentication);
    }

    /** The logo, for the preview on this page (the PDF embeds it directly). */
    @GetMapping(Endpoints.ISSUER_LOGO)
    @PreAuthorize(Privileges.Has.VIEW_ISSUER)
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

    @PostMapping(Endpoints.ISSUER_LOGO)
    @PreAuthorize(Privileges.Has.EDIT_ISSUER)
    public String uploadLogo(@RequestParam("logo") MultipartFile file, RedirectAttributes redirectAttributes) {
        try {
            issuerService.updateLogo(file.getBytes());
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.SUCCESS, messages.get(MessageProperties.ISSUER_LOGO_SAVED));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.ERROR, messages.of(ex));
        } catch (IOException ex) {
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.ERROR, messages.get(MessageProperties.ISSUER_LOGO_UNREADABLE));
        }
        return Endpoints.REDIRECT + Endpoints.ISSUER;
    }

    @PostMapping(Endpoints.ISSUER_LOGO_DELETE)
    @PreAuthorize(Privileges.Has.EDIT_ISSUER)
    public String removeLogo(RedirectAttributes redirectAttributes) {
        issuerService.removeLogo();
        redirectAttributes.addFlashAttribute(CommonCodes.Attributes.SUCCESS, messages.get(MessageProperties.ISSUER_LOGO_DELETED));
        return Endpoints.REDIRECT + Endpoints.ISSUER;
    }

    private String formView(Model model, Authentication authentication) {
        Issuer issuer = issuerService.getIssuer();
        model.addAttribute(CommonCodes.Attributes.HAS_LOGO, issuer.hasLogo());
        model.addAttribute(CommonCodes.Attributes.STATS, dashboardService.stats(null));
        model.addAttribute(CommonCodes.Attributes.NETWORK_ADDRESSES, networkAddresses.urls());
        model.addAttribute(CommonCodes.Attributes.CAN_EDIT, authentication.getAuthorities().stream()
            .anyMatch(a -> Privileges.EDIT_ISSUER.equals(a.getAuthority())));
        return CommonCodes.Views.ISSUER_FORM;
    }
}
