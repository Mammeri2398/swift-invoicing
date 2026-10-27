package com.swiftlabs.invoicing.access.profile;

import com.swiftlabs.invoicing.access.privilege.Privilege;
import com.swiftlabs.invoicing.access.privilege.PrivilegeRepository;
import com.swiftlabs.invoicing.access.user.User;
import com.swiftlabs.invoicing.access.user.UserRepository;
import com.swiftlabs.invoicing.company.CompanyAccessService;
import com.swiftlabs.invoicing.company.CompanySchemas;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(authorities = "MANAGE_PROFILES")
class ProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private PrivilegeRepository privilegeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyAccessService accessService;

    @Autowired
    private EntityManager entityManager;

    private Long privilegeId(String code) {
        return privilegeRepository.findByCode(code).getId();
    }

    private MockHttpServletRequestBuilder save(Long id, String name, String... privilegeCodes) {
        MockHttpServletRequestBuilder request = post("/profiles/save").with(csrf()).param("name", name);
        if (id != null) {
            request.param("id", id.toString());
        }
        for (String code : privilegeCodes) {
            request.param("privilegeIds", privilegeId(code).toString());
        }
        return request;
    }

    /** Reloads from the database, not from the test's persistence context. */
    private Set<String> privilegeCodes(String profileName) {
        entityManager.flush();
        entityManager.clear();
        return profileRepository.findByName(profileName).getPrivileges().stream()
            .map(Privilege::getCode).collect(Collectors.toSet());
    }

    private String[] allCodes() {
        return privilegeRepository.findAll().stream().map(Privilege::getCode).toArray(String[]::new);
    }

    @Test
    void newProfilesGetIdsAfterTheSeededAdministrateur() throws Exception {
        // Before V4 the identity sequence still pointed at 1 (taken by Administrateur)
        for (String name : new String[] {"Gestionnaire", "Lecteur"}) {
            mockMvc.perform(save(null, name))
                .andExpect(redirectedUrl("/profiles"))
                .andExpect(flash().attribute("success", "Profil enregistré."));
        }

        Profile gestionnaire = profileRepository.findByName("Gestionnaire");
        Profile lecteur = profileRepository.findByName("Lecteur");
        assertThat(gestionnaire.getId()).isGreaterThan(1L);
        assertThat(lecteur.getId()).isGreaterThan(gestionnaire.getId());
        assertThat(profileRepository.findByName("Administrateur").getId()).isEqualTo(1L);
    }

    @Test
    void createStoresCheckedPrivileges() throws Exception {
        mockMvc.perform(save(null, "Lecteur", "VIEW_DASHBOARD", "VIEW_INVOICES"))
            .andExpect(redirectedUrl("/profiles"));

        assertThat(privilegeCodes("Lecteur")).containsExactlyInAnyOrder("VIEW_DASHBOARD", "VIEW_INVOICES");
    }

    @Test
    void editingAdministrateurKeepsItsPrivileges() throws Exception {
        int before = privilegeCodes("Administrateur").size();

        // What the form submits for Administrateur: every box checked, description changed
        mockMvc.perform(save(1L, "Administrateur", allCodes()).param("description", "Nouvelle description"))
            .andExpect(redirectedUrl("/profiles"))
            .andExpect(flash().attribute("success", "Profil enregistré."));

        assertThat(privilegeCodes("Administrateur")).hasSize(before);
        assertThat(profileRepository.findByName("Administrateur").getDescription()).isEqualTo("Nouvelle description");
    }

    @Test
    void editReplacesPrivilegesWithCheckedOnes() throws Exception {
        mockMvc.perform(save(null, "Gestionnaire", "VIEW_INVOICES", "CREATE_INVOICE", "VIEW_CLIENTS"));
        Long id = profileRepository.findByName("Gestionnaire").getId();

        mockMvc.perform(save(id, "Gestionnaire", "VIEW_INVOICES", "ISSUE_INVOICE"))
            .andExpect(redirectedUrl("/profiles"));

        assertThat(privilegeCodes("Gestionnaire")).containsExactlyInAnyOrder("VIEW_INVOICES", "ISSUE_INVOICE");
    }

    @Test
    void cannotRemoveManageProfilesFromLastActiveHolder() throws Exception {
        // Only the seeded admin (profile Administrateur) holds MANAGE_PROFILES
        String[] withoutManageProfiles = privilegeRepository.findAll().stream().map(Privilege::getCode)
            .filter(code -> !code.equals("MANAGE_PROFILES")).toArray(String[]::new);

        mockMvc.perform(save(1L, "Administrateur", withoutManageProfiles))
            .andExpect(status().isOk())
            .andExpect(view().name("access/profile/form"))
            .andExpect(model().attributeHasFieldErrors("profile", "privilegeIds"))
            .andExpect(content().string(containsString("MANAGE_PROFILES : impossible de le retirer")));

        assertThat(privilegeCodes("Administrateur")).contains("MANAGE_PROFILES").hasSize(allCodes().length);
    }

    @Test
    void canRemoveManageProfilesWhenAnotherActiveUserKeepsIt() throws Exception {
        mockMvc.perform(save(null, "Sécurité", "MANAGE_PROFILES"));
        User other = new User();
        other.setUsername("secu");
        other.setEmail("secu@example.test");
        other.setPassword("x");
        other.setEnabled(true);
        accessService.grant(userRepository.save(other), CompanySchemas.FIRST_ID, profileRepository.findByName("Sécurité"));

        mockMvc.perform(save(1L, "Administrateur", "VIEW_DASHBOARD"))
            .andExpect(redirectedUrl("/profiles"));

        assertThat(privilegeCodes("Administrateur")).containsExactly("VIEW_DASHBOARD");
    }

    @Test
    void editFormShowsCheckboxesForStoredPrivileges() throws Exception {
        mockMvc.perform(save(null, "Lecteur", "VIEW_INVOICES"));
        Long id = profileRepository.findByName("Lecteur").getId();
        String invoices = privilegeId("VIEW_INVOICES").toString();
        String clients = privilegeId("VIEW_CLIENTS").toString();

        String html = mockMvc.perform(get("/profiles/" + id + "/edit"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat(html).containsPattern("value=\"" + invoices + "\"[^>]*checked");
        assertThat(html).containsPattern("<input[^>]*value=\"" + clients + "\"[^>]*/>");
        assertThat(html).doesNotContainPattern("value=\"" + clients + "\"[^>]*checked");
        assertThat(html).contains("name=\"privilegeIds\"");
    }

    @Test
    void failedSaveRedisplaysSubmittedValuesWithError() throws Exception {
        // Shown again directly (no redirect), the error next to the name, the checked boxes kept
        String clients = privilegeId("VIEW_CLIENTS").toString();
        String html = mockMvc.perform(save(null, "Administrateur", "VIEW_CLIENTS"))
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrors("profile", "name"))
            .andExpect(content().string(containsString("Ce nom de profil existe déjà.")))
            .andReturn().getResponse().getContentAsString();
        assertThat(html).containsPattern("value=\"" + clients + "\"[^>]*checked");
    }

    @Test
    void listShowsFlashMessages() throws Exception {
        mockMvc.perform(get("/profiles").flashAttr("success", "Profil enregistré."))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Profil enregistré.")));
    }
}
