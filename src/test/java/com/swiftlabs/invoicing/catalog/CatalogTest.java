package com.swiftlabs.invoicing.catalog;

import com.swiftlabs.invoicing.access.profile.ProfileRepository;
import com.swiftlabs.invoicing.access.user.User;
import com.swiftlabs.invoicing.access.user.UserRepository;
import com.swiftlabs.invoicing.company.CompanyAccessService;
import com.swiftlabs.invoicing.company.CompanySchemas;
import com.swiftlabs.invoicing.issuer.CompanyType;
import com.swiftlabs.invoicing.issuer.IssuerService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The catalogue of products and services, and picking its articles on the invoice form. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CatalogTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private IssuerService issuerService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyAccessService accessService;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    /** A real login: the page's buttons depend on the session's user. */
    private MockHttpSession administrator() throws Exception {
        User user = new User();
        user.setUsername("catalog-admin");
        user.setEmail("catalog-admin@example.test");
        user.setPassword(passwordEncoder.encode("Secret123"));
        accessService.grant(userRepository.save(user), CompanySchemas.FIRST_ID, profileRepository.findByName("Administrateur"));
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/perform-login").session(session).with(csrf()).param("username", "catalog-admin").param("password", "Secret123"));
        return session;
    }

    private CatalogItem item(String name, String price, String vatRate) {
        CatalogItemForm form = new CatalogItemForm();
        form.setName(name);
        form.setUnit("jour");
        form.setUnitPrice(new BigDecimal(price));
        form.setVatRate(new BigDecimal(vatRate));
        return catalogService.save(form);
    }

    @Test
    void administratorsManageTheCatalogue() {
        assertThat(jdbc.queryForObject("""
            SELECT COUNT(*) FROM profile_privilege pp JOIN privilege p ON p.id = pp.privilege_id
            WHERE pp.profile_id = 1 AND p.code = 'MANAGE_CATALOG'""", Integer.class)).isEqualTo(1);
    }

    @Test
    void anArticleIsSavedAndListed() throws Exception {
        MockHttpSession session = administrator();
        mockMvc.perform(post("/catalog/save").session(session).with(csrf())
                .param("name", "  Développement web ").param("unit", "jour").param("unitPrice", "45000").param("vatRate", "0"))
            .andExpect(redirectedUrl("/catalog"))
            .andExpect(flash().attribute("success", "Article « Développement web » enregistré."));

        mockMvc.perform(get("/catalog").session(session))
            .andExpect(content().string(containsString("Développement web")))
            .andExpect(content().string(containsString("45 000,00 DA")))
            .andExpect(content().string(containsString("Nouvel article")));
    }

    @Test
    void twoArticlesCannotHaveTheSameName() throws Exception {
        item("Maintenance", "10000", "0");
        mockMvc.perform(post("/catalog/save").session(administrator()).with(csrf())
                .param("name", "MAINTENANCE").param("unitPrice", "12000").param("vatRate", "0"))
            .andExpect(model().attributeHasFieldErrors("item", "name"))
            .andExpect(content().string(containsString("Un article « MAINTENANCE » existe déjà.")));
    }

    @Test
    void theVatRateComesFromTheConfiguredList() throws Exception {
        mockMvc.perform(post("/catalog/save").session(administrator()).with(csrf())
                .param("name", "Formation").param("unitPrice", "1000").param("vatRate", "7"))
            .andExpect(model().attributeHasFieldErrors("item", "vatRate"));
    }

    @Test
    @WithMockUser(authorities = "CREATE_INVOICE")
    void whoeverWritesInvoicesCanReadTheCatalogueButNotChangeIt() throws Exception {
        item("Audit", "30000", "0");
        mockMvc.perform(get("/catalog")).andExpect(status().isOk()).andExpect(content().string(containsString("Audit")));
        mockMvc.perform(get("/catalog/new")).andExpect(status().isForbidden());
        mockMvc.perform(post("/catalog/save").with(csrf()).param("name", "X").param("unitPrice", "1").param("vatRate", "0"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    void otherUsersDoNotSeeTheCatalogue() throws Exception {
        mockMvc.perform(get("/catalog")).andExpect(status().isForbidden());
    }

    @Test
    void theInvoiceFormOffersTheArticles() throws Exception {
        issuerService.getIssuer().setCompanyType(CompanyType.COMPANY);
        item("Hébergement annuel", "12000", "19");
        mockMvc.perform(get("/invoices/new").session(administrator()))
            .andExpect(content().string(containsString("<datalist id=\"catalog\">")))
            .andExpect(content().string(containsString("value=\"Hébergement annuel\"")))
            .andExpect(content().string(containsString("data-price=\"12000")))
            .andExpect(content().string(containsString("data-vat=\"19")))
            .andExpect(content().string(containsString("list=\"catalog\"")));
    }

    @Test
    void deletingAnArticleLeavesTheOthers() throws Exception {
        CatalogItem kept = item("Conseil", "5000", "0");
        CatalogItem removed = item("Support", "2000", "0");
        mockMvc.perform(post("/catalog/" + removed.getId() + "/delete").session(administrator()).with(csrf()))
            .andExpect(flash().attribute("success", "Article supprimé du catalogue."));
        assertThat(catalogService.findAll()).extracting(CatalogItem::getName).containsExactly(kept.getName());
    }

    @Test
    @WithMockUser(authorities = "CREATE_INVOICE")
    void theCatalogueSpeaksTheBrowserLanguageForThisUser() throws Exception {
        mockMvc.perform(get("/catalog").header("Accept-Language", "en"))
            .andExpect(content().string(containsString("Products and services")))
            .andExpect(content().string(not(containsString("Produits et services"))));
    }
}
