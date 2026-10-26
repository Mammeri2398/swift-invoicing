package com.swiftlabs.invoicing.licence;

import com.swiftlabs.invoicing.access.user.UserRepository;
import com.swiftlabs.invoicing.common.CommonCodes;
import com.swiftlabs.invoicing.common.Endpoints;
import com.swiftlabs.invoicing.common.MessageProperties;
import com.swiftlabs.invoicing.common.Messages;
import com.swiftlabs.invoicing.common.Privileges;

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
@RequestMapping(Endpoints.LICENCE)
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
    @PreAuthorize(Privileges.Has.VIEW_ISSUER)
    public String page(@RequestParam(name = "readonly", required = false) String readonly, Model model) {
        model.addAttribute(CommonCodes.Attributes.REFUSED, readonly != null);
        model.addAttribute(CommonCodes.Attributes.ACTIVE_USERS, userRepository.countByEnabledTrue());
        model.addAttribute(CommonCodes.Attributes.MAX_USERS, licenceService.isRequired() ? licenceService.maxUsers() : 0);
        return CommonCodes.Views.LICENCE_LICENCE;
    }

    @PostMapping
    @PreAuthorize(Privileges.Has.EDIT_ISSUER)
    public String activate(@RequestParam String key, RedirectAttributes redirectAttributes) {
        try {
            Licence licence = licenceService.activate(key);
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.SUCCESS, messages.get(MessageProperties.LICENCE_ACTIVATED,
                licence.id(), licence.company(),
                String.valueOf(licence.users()), licence.expires().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))));
        } catch (InvalidLicenceException ex) {
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.ERROR, messages.of(ex));
        }
        return Endpoints.REDIRECT + Endpoints.LICENCE;
    }
}
