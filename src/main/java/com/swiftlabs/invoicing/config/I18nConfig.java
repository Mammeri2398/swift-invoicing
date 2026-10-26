package com.swiftlabs.invoicing.config;

import com.swiftlabs.invoicing.common.Messages;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.util.Locale;

/** French, English and Arabic: the texts (see Messages) and the language of each request. */
@Configuration
public class I18nConfig implements WebMvcConfigurer {

    // Outside a request (scheduled backups, start-up): French, never the language of the PC or server
    static {
        LocaleContextHolder.setDefaultLocale(Locale.FRENCH);
    }

    @Bean
    public MessageSource messageSource() {
        return Messages.newMessageSource();
    }

    /** The bean name is the one DispatcherServlet looks for. */
    @Bean
    public LocaleResolver localeResolver() {
        return new UserLocaleResolver();
    }

    /** ?lang=en on any page (the login page offers it): see UserLocaleResolver#setLocale. */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName("lang");
        interceptor.setIgnoreInvalidLocale(true);
        registry.addInterceptor(interceptor);
    }
}
