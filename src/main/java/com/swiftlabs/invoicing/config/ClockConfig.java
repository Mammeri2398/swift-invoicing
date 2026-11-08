package com.swiftlabs.invoicing.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Time source, injectable so time-based rules (lockout, token expiry) can be tested. Its zone is the business's
 * (app.time-zone, Algeria by default), not the server's: between midnight and 1 a.m. a server in UTC would still
 * be on the day before, and on 1 January on the year before.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(@Value("${app.time-zone:Africa/Algiers}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
