package com.swiftlabs.invoicing.access.user;

import com.swiftlabs.invoicing.access.auth.AppUserPrincipal;
import com.swiftlabs.invoicing.access.auth.SessionService;
import com.swiftlabs.invoicing.access.profile.Profile;
import com.swiftlabs.invoicing.access.profile.ProfileService;
import com.swiftlabs.invoicing.access.twofactor.TwoFactorService;
import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.common.CommonCodes;
import com.swiftlabs.invoicing.common.Endpoints;
import com.swiftlabs.invoicing.common.FormErrors;
import com.swiftlabs.invoicing.common.Language;
import com.swiftlabs.invoicing.common.MessageProperties;
import com.swiftlabs.invoicing.common.Messages;
import com.swiftlabs.invoicing.common.Privileges;
import com.swiftlabs.invoicing.common.ResourceNotFoundException;
import com.swiftlabs.invoicing.company.Company;
import com.swiftlabs.invoicing.company.CompanyAccessService;
import com.swiftlabs.invoicing.company.CompanyContext;
import com.swiftlabs.invoicing.company.CompanyService;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping(Endpoints.USERS)
@PreAuthorize(Privileges.Has.MANAGE_USERS)
public class UserController {

    private final UserService userService;
    private final ProfileService profileService;
    private final SessionService sessionService;
    private final TwoFactorService twoFactorService;
    private final CompanyAccessService accessService;
    private final CompanyService companyService;
    private final Messages messages;
    private final Clock clock;

    public UserController(UserService userService, ProfileService profileService, SessionService sessionService,
                          TwoFactorService twoFactorService, CompanyAccessService accessService, CompanyService companyService,
                          Messages messages, Clock clock) {
        this.accessService = accessService;
        this.companyService = companyService;
        this.messages = messages;
        this.userService = userService;
        this.profileService = profileService;
        this.sessionService = sessionService;
        this.twoFactorService = twoFactorService;
        this.clock = clock;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute(CommonCodes.Attributes.USERS, userService.findAll());
        // The profile of each user in the current company (none: no access to it)
        model.addAttribute(CommonCodes.Attributes.PROFILE_NAMES, accessService.profileNamesIn(CompanyContext.currentCompanyId()));
        model.addAttribute(CommonCodes.Attributes.NOW, LocalDateTime.now(clock));
        return CommonCodes.Views.ACCESS_USER_LIST;
    }

    @GetMapping(Endpoints.USER_NEW)
    public String newForm(@AuthenticationPrincipal AppUserPrincipal me, Model model) {
        UserForm form = new UserForm();
        // By default the new colleague works in the administrator's language
        form.setLanguage(messages.current());
        form.setAccesses(editableRows(null, me));
        model.addAttribute(CommonCodes.Attributes.USER, form);
        return formView(form, me, model);
    }

    @GetMapping(Endpoints.USER_EDIT)
    public String editForm(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal me, Model model) {
        User user = userService.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(MessageProperties.USER_ERROR_NOT_FOUND));
        UserForm form = UserForm.from(user, editableRows(user.getId(), me));
        model.addAttribute(CommonCodes.Attributes.USER, form);
        return formView(form, me, model);
    }

    /** Invalid input is shown again with the errors next to each field. */
    @PostMapping(Endpoints.USER_SAVE)
    public String save(@Valid @ModelAttribute(CommonCodes.Attributes.USER) UserForm form, BindingResult result,
                       @AuthenticationPrincipal AppUserPrincipal me, Model model, RedirectAttributes redirectAttributes) {
        if (!result.hasErrors()) {
            try {
                if (form.getId() == null) {
                    UserService.NewAccount account = userService.create(form, me.getId());
                    redirectAttributes.addFlashAttribute(CommonCodes.Attributes.TEMPORARY_PASSWORD, account.temporaryPassword());
                    redirectAttributes.addFlashAttribute(CommonCodes.Attributes.TEMPORARY_PASSWORD_USER, account.user().getUsername());
                    redirectAttributes.addFlashAttribute(CommonCodes.Attributes.SUCCESS, messages.get(MessageProperties.USER_CREATED));
                } else {
                    User user = userService.update(form, me.getId());
                    if (!user.isEnabled()) {
                        sessionService.expireSessions(user.getUsername(), null);
                    }
                    redirectAttributes.addFlashAttribute(CommonCodes.Attributes.SUCCESS, messages.get(MessageProperties.USER_UPDATED));
                }
                return Endpoints.REDIRECT + Endpoints.USERS;
            } catch (BusinessException ex) {
                FormErrors.reject(result, ex);
            }
        }
        return formView(form, me, model);
    }

    /** New temporary password (forgotten password without mail, locked account...). */
    @PostMapping(Endpoints.USER_RESET_PASSWORD)
    public String resetPassword(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            String temporaryPassword = userService.resetToTemporaryPassword(id);
            User user = userService.findById(id).orElseThrow();
            sessionService.expireSessions(user.getUsername(), null);
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.TEMPORARY_PASSWORD, temporaryPassword);
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.TEMPORARY_PASSWORD_USER, user.getUsername());
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.SUCCESS, messages.get(MessageProperties.USER_PASSWORD_RESET));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.ERROR, messages.of(ex));
        }
        return Endpoints.REDIRECT + Endpoints.USERS;
    }

    /** Lost phone and recovery codes: the user logs in with the password alone and sets it up again. */
    @PostMapping(Endpoints.USER_RESET_TWO_FACTOR)
    public String resetTwoFactor(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        User user = userService.findById(id).orElseThrow(() -> new ResourceNotFoundException(MessageProperties.USER_ERROR_NOT_FOUND));
        twoFactorService.reset(id);
        sessionService.expireSessions(user.getUsername(), null);
        redirectAttributes.addFlashAttribute(CommonCodes.Attributes.SUCCESS, messages.get(MessageProperties.USER_TWO_FACTOR_RESET,
            user.getUsername()));
        return Endpoints.REDIRECT + Endpoints.USERS;
    }

    @PostMapping(Endpoints.USER_DELETE)
    public String delete(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal me,
                         RedirectAttributes redirectAttributes) {
        try {
            userService.delete(id, me.getId());
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.SUCCESS, messages.get(MessageProperties.USER_DELETED));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.ERROR, messages.of(ex));
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute(CommonCodes.Attributes.ERROR, messages.get(MessageProperties.USER_ERROR_DELETE_FAILED));
        }
        return Endpoints.REDIRECT + Endpoints.USERS;
    }

    /**
     * One row per active company the administrator manages, with the user's profile there (none for a new user).
     * The administrator's own access to the company they work in is not editable: they cannot lock themselves out.
     */
    private List<UserForm.Access> editableRows(Long userId, AppUserPrincipal me) {
        Set<Long> managed = accessService.companiesManagedBy(me.getId());
        List<UserForm.Access> rows = new ArrayList<>();
        for (Company company : companyService.findActive()) {
            if (managed.contains(company.getId()) && !isOwnCurrentAccess(userId, company.getId(), me)) {
                Long profileId = userId == null ? null
                    : accessService.profileIn(userId, company.getId()).map(Profile::getId).orElse(null);
                rows.add(new UserForm.Access(company.getId(), profileId));
            }
        }
        return rows;
    }

    private static boolean isOwnCurrentAccess(Long userId, Long companyId, AppUserPrincipal me) {
        return Objects.equals(userId, me.getId()) && Objects.equals(companyId, me.getCompanyId());
    }

    /** The rows of the form (company names), and the user's other companies, shown but not editable here. */
    private String formView(UserForm form, AppUserPrincipal me, Model model) {
        Map<Long, String> names = new HashMap<>();
        companyService.findActive().forEach(company -> names.put(company.getId(), company.getName()));
        model.addAttribute(CommonCodes.Attributes.COMPANY_NAMES, names);
        Set<Long> editable = form.getAccesses().stream().map(UserForm.Access::getCompanyId).collect(Collectors.toSet());
        model.addAttribute(CommonCodes.Attributes.OTHER_ACCESSES, form.getId() == null ? List.of()
            : accessService.accessesOf(form.getId()).stream().filter(access -> !editable.contains(access.getCompany().getId())).toList());
        model.addAttribute(CommonCodes.Attributes.PROFILES, profileService.findAll());
        model.addAttribute(CommonCodes.Attributes.LANGUAGES, Language.values());
        return CommonCodes.Views.ACCESS_USER_FORM;
    }
}
