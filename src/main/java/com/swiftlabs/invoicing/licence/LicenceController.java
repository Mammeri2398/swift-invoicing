package com.swiftlabs.invoicing.licence;

import com.swiftlabs.invoicing.access.user.UserRepository;
import com.swiftlabs.invoicing.common.Messages;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.format.DateTimeFormatter;

/** Réglages > Licence: where the install stands, and where the customer pastes a key. */
@Controller
@RequestMapping(LicenceFilter.LICENCE_URL)
public class LicenceController {

    private final LicenceService licenceService;
    private final UserRepository userRepository;
    private final Messages messages;

    public LicenceController(LicenceService licenceService, UserRepository userRepository, Messages messages) {
        this.licenceService = licenceService;
        this.userRepository = userRepository;
        this.messages = messages;
    }

    /** {@code ?readonly}: arrived here from a change refused by the read-only mode. */
    @GetMapping
    @PreAuthorize("hasAuthority('VIEW_ISSUER')")
    public String page(@RequestParam(name = "readonly", required = false) String readonly, Model model) {
        model.addAttribute("refused", readonly != null);
        model.addAttribute("activeUsers", userRepository.countByEnabledTrue());
        model.addAttribute("maxUsers", licenceService.isRequired() ? licenceService.maxUsers() : 0);
        return "licence/licence";
    }

    @PostMapping
    @PreAuthorize("hasAuthority('EDIT_ISSUER')")
    public String activate(@RequestParam String key, RedirectAttributes redirectAttributes) {
        try {
            Licence licence = licenceService.activate(key);
            redirectAttributes.addFlashAttribute("success", messages.get("licence.activated", licence.id(), licence.company(),
                String.valueOf(licence.users()), licence.expires().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))));
        } catch (InvalidLicenceException ex) {
            redirectAttributes.addFlashAttribute("error", messages.of(ex));
        }
        return "redirect:" + LicenceFilter.LICENCE_URL;
    }
}
