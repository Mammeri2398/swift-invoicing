package com.swiftlabs.invoicing.help;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Aide: the guide in each language, and "À propos". */
@SpringBootTest
@AutoConfigureMockMvc
class HelpTest {

    private static final Path CONTENT = Path.of("src/main/resources/templates/help/content");
    private static final Pattern SECTION = Pattern.compile("<section id=\"([\\w-]+)\"");
    private static final Pattern TOC_LINK = Pattern.compile("<a href=\"#([\\w-]+)\"");

    @Autowired
    private MockMvc mockMvc;

    private static List<String> all(Pattern pattern, String text) {
        List<String> found = new ArrayList<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    @Test
    void theHelpNeedsALogin() throws Exception {
        mockMvc.perform(get("/help")).andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithMockUser
    void theGuideAndAboutAreShown() throws Exception {
        mockMvc.perform(get("/help"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Guide d&#39;utilisation")))
            .andExpect(content().string(containsString("id=\"invoices\"")))
            .andExpect(content().string(containsString("Créer et émettre une facture")))
            .andExpect(content().string(containsString("id=\"about\"")))
            .andExpect(content().string(containsString("Version")));
    }

    @Test
    @WithMockUser
    void theGuideFollowsTheLanguage() throws Exception {
        mockMvc.perform(get("/help").header("Accept-Language", "en"))
            .andExpect(content().string(containsString("User guide")))
            .andExpect(content().string(containsString("id=\"invoices\"")));
        mockMvc.perform(get("/help").header("Accept-Language", "ar"))
            .andExpect(content().string(containsString("dir=\"rtl\"")))
            .andExpect(content().string(containsString("دليل الاستخدام")))
            .andExpect(content().string(containsString("id=\"invoices\"")));
    }

    /** Every language has the same topics, in the same order, each listed in its table of contents. */
    @Test
    void theThreeGuidesHaveTheSameSections() throws IOException {
        String french = Files.readString(CONTENT.resolve("fr.html"));
        List<String> sections = all(SECTION, french);
        assertThat(sections).hasSizeGreaterThan(10);
        for (String language : List.of("fr", "en", "ar")) {
            String guide = Files.readString(CONTENT.resolve(language + ".html"));
            String toc = guide.substring(guide.indexOf("th:fragment=\"toc\""), guide.indexOf("th:fragment=\"sections\""));
            assertThat(all(SECTION, guide)).as(language + " sections").isEqualTo(sections);
            assertThat(all(TOC_LINK, toc)).as(language + " table of contents").isEqualTo(sections);
        }
    }
}
