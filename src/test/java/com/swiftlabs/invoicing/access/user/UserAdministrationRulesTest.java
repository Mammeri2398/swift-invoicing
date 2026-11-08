package com.swiftlabs.invoicing.access.user;

import com.swiftlabs.invoicing.access.privilege.Privilege;
import com.swiftlabs.invoicing.access.privilege.PrivilegeForm;
import com.swiftlabs.invoicing.access.privilege.PrivilegeRepository;
import com.swiftlabs.invoicing.access.privilege.PrivilegeService;
import com.swiftlabs.invoicing.access.profile.Profile;
import com.swiftlabs.invoicing.access.profile.ProfileRepository;
import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.common.MessageProperties;
import com.swiftlabs.invoicing.common.Privileges;
import com.swiftlabs.invoicing.common.ResourceNotFoundException;
import com.swiftlabs.invoicing.company.CompanyAccessService;
import com.swiftlabs.invoicing.company.CompanySchemas;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** What managing users and privileges may not do: give more than one holds, read other companies, lock everyone out. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserAdministrationRulesTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private PrivilegeRepository privilegeRepository;

    @Autowired
    private PrivilegeService privilegeService;

    @Autowired
    private CompanyAccessService accessService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MockMvc mockMvc;

    private Profile userManager;
    private Profile reader;
    private User manager;

    private Profile profile(String name, String... codes) {
        Profile profile = new Profile();
        profile.setName(name);
        profile.getPrivileges().addAll(privilegeRepository.findAll().stream()
            .filter(privilege -> Set.of(codes).contains(privilege.getCode())).toList());
        return profileRepository.save(profile);
    }

    private User user(String username, Profile profile) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.test");
        user.setPassword(passwordEncoder.encode("Secret123"));
        user.setEnabled(true);
        user = userRepository.save(user);
        accessService.grant(user, CompanySchemas.FIRST_ID, profile);
        return user;
    }

    private static UserForm form(String username, Profile profile) {
        UserForm form = new UserForm();
        form.setUsername(username);
        form.setEmail(username + "@example.test");
        form.setAccesses(List.of(new UserForm.Access(CompanySchemas.FIRST_ID, profile.getId())));
        return form;
    }

    @BeforeEach
    void profiles() {
        userManager = profile("Gestion des utilisateurs", Privileges.MANAGE_USERS, Privileges.VIEW_DASHBOARD);
        reader = profile("Lecture seule", Privileges.VIEW_DASHBOARD);
        manager = user("gestionnaire", userManager);
    }

    @Test
    void aUserManagerGivesOnlyProfilesTheyHoldThemselves() {
        Profile administrateur = profileRepository.findByName("Administrateur");
        assertThatThrownBy(() -> userService.create(form("intrus", administrateur), manager.getId()))
            .isInstanceOf(BusinessException.class)
            .extracting(ex -> ((BusinessException) ex).getCode()).isEqualTo(MessageProperties.USER_ERROR_PROFILE_ABOVE_OWN);
        assertThat(userRepository.findByUsername("intrus")).isEmpty();

        assertThat(userService.create(form("lecteur", reader), manager.getId()).user().getId()).isNotNull();
    }

    @Test
    void usersOfCompaniesOneDoesNotManageAreNotFound() {
        User colleague = user("collegue", reader);
        User plain = user("simple", reader);
        assertThat(userService.requireVisible(colleague.getId(), manager.getId())).isEqualTo(colleague);
        // Without MANAGE_USERS in the colleague's company, the account is as good as nonexistent
        assertThatThrownBy(() -> userService.requireVisible(colleague.getId(), plain.getId()))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void theLastUserManagerCanBeNeitherDisabledNorDeletedNorDemoted() {
        // Everyone else switched off: the manager is the last one who can manage the users of the company
        userRepository.findAll().stream().filter(user -> !user.getId().equals(manager.getId())).forEach(user -> user.setEnabled(false));
        userRepository.flush();
        User other = user("autre", reader);

        UserForm disabled = UserForm.from(manager, List.of(new UserForm.Access(CompanySchemas.FIRST_ID, userManager.getId())));
        disabled.setEnabled(false);
        assertThatThrownBy(() -> userService.update(disabled, null))
            .isInstanceOf(BusinessException.class)
            .extracting(ex -> ((BusinessException) ex).getCode()).isEqualTo(MessageProperties.USER_ERROR_LAST_USER_MANAGER);

        UserForm demoted = UserForm.from(manager, List.of(new UserForm.Access(CompanySchemas.FIRST_ID, reader.getId())));
        assertThatThrownBy(() -> userService.update(demoted, null))
            .isInstanceOf(BusinessException.class)
            .extracting(ex -> ((BusinessException) ex).getCode()).isEqualTo(MessageProperties.USER_ERROR_LAST_USER_MANAGER);

        assertThatThrownBy(() -> userService.delete(manager.getId(), null))
            .isInstanceOf(BusinessException.class)
            .extracting(ex -> ((BusinessException) ex).getCode()).isEqualTo(MessageProperties.USER_ERROR_LAST_USER_MANAGER);

        // Once someone else can, it is allowed
        accessService.grant(other, CompanySchemas.FIRST_ID, userManager);
        userService.delete(manager.getId(), null);
        assertThat(userRepository.findById(manager.getId())).isEmpty();
    }

    @Test
    void thePrivilegesTheApplicationChecksCannotBeDeleted() {
        Privilege manageUsers = privilegeRepository.findAll().stream()
            .filter(privilege -> Privileges.MANAGE_USERS.equals(privilege.getCode())).findFirst().orElseThrow();
        assertThatThrownBy(() -> privilegeService.delete(manageUsers.getId()))
            .isInstanceOf(BusinessException.class)
            .extracting(ex -> ((BusinessException) ex).getCode()).isEqualTo(MessageProperties.PRIVILEGE_ERROR_BUILT_IN);
        assertThat(privilegeRepository.findById(manageUsers.getId())).isPresent();

        PrivilegeForm custom = new PrivilegeForm();
        custom.setCode("CUSTOM_REPORTS");
        custom.setName("Rapports personnalisés");
        Long id = privilegeService.save(custom).getId();
        privilegeService.delete(id);
        assertThat(privilegeRepository.findById(id)).isEmpty();
    }

    @Test
    @WithMockUser(authorities = {"VIEW_CLIENTS"})
    void clientBalancesAreShownOnlyToWhoSeesTheInvoices() throws Exception {
        mockMvc.perform(get("/clients"))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("Solde dû"))));
    }

    @Test
    @WithMockUser(authorities = {"MANAGE_USERS"})
    void aMalformedIdIsAPageNotFound() throws Exception {
        mockMvc.perform(get("/users/abc/edit")).andExpect(status().isNotFound());
    }
}
