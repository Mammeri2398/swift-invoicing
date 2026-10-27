package com.swiftlabs.invoicing.config;

import com.swiftlabs.invoicing.company.CompanyFormGuard;

import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.format.datetime.standard.DateTimeFormatterRegistrar;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final CompanyFormGuard companyFormGuard;

    public WebConfig(CompanyFormGuard companyFormGuard) {
        this.companyFormGuard = companyFormGuard;
    }

    /** A form shown in one company and sent after switching to another (in another tab) is refused. */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(companyFormGuard);
    }

    /** Dates bound and rendered as ISO (yyyy-MM-dd), the format of HTML date inputs. */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        DateTimeFormatterRegistrar registrar = new DateTimeFormatterRegistrar();
        registrar.setUseIsoFormat(true);
        registrar.registerFormatters(registry);
    }
}
