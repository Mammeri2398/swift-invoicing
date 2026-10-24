package com.swiftlabs.invoicing.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class HealthEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Environment environment;

    @Test
    void healthIsPublicAndShowsOnlyTheStatus() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            // No component details (database, disk space...): only the status and the probe group names
            .andExpect(jsonPath("$.components").doesNotExist())
            .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void probesAreAvailable() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @WithMockUser(authorities = {"MANAGE_USERS", "MANAGE_PROFILES", "MANAGE_PRIVILEGES"})
    void noOtherActuatorEndpointIsExposed() throws Exception {
        for (String endpoint : new String[] {"env", "beans", "configprops", "heapdump", "loggers", "mappings"}) {
            mockMvc.perform(get("/actuator/" + endpoint)).andExpect(status().isNotFound());
        }
    }

    @Test
    void otherActuatorUrlsAreNotPublic() throws Exception {
        mockMvc.perform(get("/actuator/env")).andExpect(status().is3xxRedirection());
    }

    @Test
    void testsRunWithTheTestProfileOnTopOfTheRealConfiguration() {
        assertThat(environment.getActiveProfiles()).isEmpty();
        assertThat(environment.getDefaultProfiles()).containsExactly("test");
        // From src/main/resources/application.properties, not overridden for tests
        assertThat(environment.getProperty("management.endpoint.health.show-details")).isEqualTo("never");
        assertThat(environment.getProperty("server.servlet.session.cookie.secure")).isEqualTo("true");
    }
}
