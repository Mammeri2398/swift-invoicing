package com.swiftlabs.invoicing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final UserDetailsService userDetailsService;

    public SecurityConfig(UserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/js/**", "/fonts/**", "/images/**", "/favicon.ico").permitAll()
                        .requestMatchers("/login").permitAll()
                        .requestMatchers("/forgot-password", "/forgot-password/send").permitAll()
                        .requestMatchers("/reset-password", "/reset-password/save").permitAll()
                        .requestMatchers("/invoices/lookup").permitAll()
                        .requestMatchers("/users/**").hasAuthority("MANAGE_USERS")
                        .requestMatchers("/profiles/**").hasAuthority("MANAGE_PROFILES")
                        .requestMatchers("/privileges/**").hasAuthority("MANAGE_PRIVILEGES")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/perform-login")
                        .defaultSuccessUrl("/dashboard", true)
                        .failureUrl("/login?error=true")
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/perform-logout")
                        .logoutSuccessUrl("/login?logout=true")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )
                .exceptionHandling(ex -> ex
                        .accessDeniedPage("/access-denied")
                )
                .csrf(csrf -> csrf
                        .csrfTokenRequestHandler(eagerCsrfTokenHandler())
                )
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                );

        return http.build();
    }

    /**
     * Loads the CSRF token at the start of every request instead of on first use.
     * With deferred loading, Thymeleaf asks for the token while rendering a form; on pages
     * without a session yet (login) the token can then no longer be stored because the
     * response is already committed, and the page is cut off. Keeps the BREACH-safe masking.
     */
    private XorCsrfTokenRequestAttributeHandler eagerCsrfTokenHandler() {
        XorCsrfTokenRequestAttributeHandler handler = new XorCsrfTokenRequestAttributeHandler();
        handler.setCsrfRequestAttributeName(null);
        return handler;
    }
}