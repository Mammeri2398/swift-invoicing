package com.swiftlabs.invoicing.access.privilege;

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
@PreAuthorize(Privileges.Has.MANAGE_PRIVILEGES)
@RequestMapping(Endpoints.PRIVILEGES)
public class PrivilegeController {

    private final PrivilegeService privilegeService;
    private final Messages messages;

    public PrivilegeController(PrivilegeService privilegeService, Messages messages) {
        this.privilegeService = privilegeService;
        this.messages = messages;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute(CommonCodes.Attributes.PRIVILEGES, privilegeService.findAll());
        return CommonCodes.Views.ACCESS_PRIVILEGE_LIST;
    }

    @GetMapping(Endpoints.PRIVILEGE_NEW)
    public String newForm(Model model) {
        model.addAttribute(CommonCodes.Attributes.PRIVILEGE, new PrivilegeForm());
        return CommonCodes.Views.ACCESS_PRIVILEGE_FORM;
    }

    @GetMapping(Endpoints.PRIVILEGE_EDIT)
    public String editForm(@PathVariable Long id, Model model) {
        Privilege privilege = privilegeService.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(MessageProperties.PRIVILEGE_ERROR_NOT_FOUND));
        model.addAttribute(CommonCodes.Attributes.PRIVILEGE, PrivilegeForm.from(privilege));
        return CommonCodes.Views.ACCESS_PRIVILEGE_FORM;
    }

    /** Invalid input is shown again with the errors next to each field. */
    @PostMapping(Endpoints.PRIVILEGE_SAVE)
    public String save(@Valid @ModelAttribute(CommonCodes.Attributes.PRIVILEGE) PrivilegeForm form, BindingResult result,
                       RedirectAttributes redirectAttributes) {
        if (!result.hasErrors()) {
            try {
                privilegeService.save(form);
                redirectAttributes.addFlashAttribute(CommonCodes.Attributes.SUCCESS, messages.get(MessageProperties.PRIVILEGE_SAVED));
                return Endpoints.REDIRECT + Endpoints.PRIVILEGES;
            } catch (BusinessException ex) {
                FormErrors.reject(result, ex);
            }
        }
        return CommonCodes.Views.ACCESS_PRIVILEGE_FORM;
    }

    @PostMapping(Endpoints.PRIVILEGE_DELETE)
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            privilegeService.delete(id);
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.SUCCESS, messages.get(MessageProperties.PRIVILEGE_DELETED));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.ERROR, messages.of(ex));
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.ERROR, messages.get(MessageProperties.PRIVILEGE_ERROR_DELETE_FAILED));
        }
        return Endpoints.REDIRECT + Endpoints.PRIVILEGES;
    }
}
