package com.swiftlabs.invoicing.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Pages link their CSS, scripts and fonts under versioned URLs, which browsers may keep: no download on every page. */
@SpringBootTest
@AutoConfigureMockMvc
class StaticResourcesTest {

    @Autowired
    private MockMvc mockMvc;

    private static String find(String text, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        assertThat(matcher.find()).as(regex).isTrue();
        return matcher.group(1);
    }

    @Test
    void stylesheetsScriptsAndFontsAreVersionedAndCached() throws Exception {
        String page = mockMvc.perform(get("/login")).andReturn().getResponse().getContentAsString();
        String css = find(page, "href=\"(/css/app-[0-9a-f]{32}\\.css)\"");
        String js = find(page, "src=\"(/js/app-[0-9a-f]{32}\\.js)\"");

        for (String url : new String[] {css, js}) {
            String cacheControl = mockMvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getHeader("Cache-Control");
            assertThat(cacheControl).as(url).contains("max-age=31536000").doesNotContain("no-store");
        }
        // The fonts named by the stylesheet are versioned too
        String stylesheet = mockMvc.perform(get(css)).andReturn().getResponse().getContentAsString();
        assertThat(stylesheet).containsPattern("material-symbols-outlined-[0-9a-f]{32}\\.woff2");
    }
}
