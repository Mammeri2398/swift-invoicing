package com.swiftlabs.invoicing.access.profile;

import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.common.CommonCodes;
import com.swiftlabs.invoicing.common.Endpoints;
import com.swiftlabs.invoicing.common.FormErrors;
import com.swiftlabs.invoicing.common.MessageProperties;
import com.swiftlabs.invoicing.common.Messages;
import com.swiftlabs.invoicing.common.Privileges;
import com.swiftlabs.invoicing.common.ResourceNotFoundException;

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

@Controller
@PreAuthorize(Privileges.Has.MANAGE_PROFILES)
@RequestMapping(Endpoints.PROFILES)
public class ProfileController {

    private final ProfileService profileService;
    private final Messages messages;

    public ProfileController(ProfileService profileService, Messages messages) {
        this.profileService = profileService;
        this.messages = messages;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute(CommonCodes.Attributes.PROFILES, profileService.findAll());
        return CommonCodes.Views.ACCESS_PROFILE_LIST;
    }

    @GetMapping(Endpoints.PROFILE_NEW)
    public String newForm(Model model) {
        model.addAttribute(CommonCodes.Attributes.PROFILE, new ProfileForm());
        return formView(model);
    }

    @GetMapping(Endpoints.PROFILE_EDIT)
    public String editForm(@PathVariable Long id, Model model) {
        Profile profile = profileService.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(MessageProperties.PROFILE_ERROR_NOT_FOUND));
        model.addAttribute(CommonCodes.Attributes.PROFILE, ProfileForm.from(profile));
        return formView(model);
    }

    /** Invalid input is shown again with the errors next to each field. */
    @PostMapping(Endpoints.PROFILE_SAVE)
    public String save(@Valid @ModelAttribute(CommonCodes.Attributes.PROFILE) ProfileForm form, BindingResult result, Model model,
                       RedirectAttributes redirectAttributes) {
        if (!result.hasErrors()) {
            try {
                profileService.save(form);
                redirectAttributes.addFlashAttribute(CommonCodes.Attributes.SUCCESS, messages.get(MessageProperties.PROFILE_SAVED));
                return Endpoints.REDIRECT + Endpoints.PROFILES;
            } catch (BusinessException ex) {
                FormErrors.reject(result, ex);
            }
        }
        return formView(model);
    }

    @PostMapping(Endpoints.PROFILE_DELETE)
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            profileService.delete(id);
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.SUCCESS, messages.get(MessageProperties.PROFILE_DELETED));
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.ERROR, messages.get(MessageProperties.PROFILE_ERROR_DELETE_FAILED));
        }
        return Endpoints.REDIRECT + Endpoints.PROFILES;
    }

    private String formView(Model model) {
        model.addAttribute(CommonCodes.Attributes.ALL_PRIVILEGES, profileService.findAllPrivileges());
        return CommonCodes.Views.ACCESS_PROFILE_FORM;
    }
}
