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
    private final ActivityLog activityLog;

    public WebConfig(CompanyFormGuard companyFormGuard, ActivityLog activityLog) {
        this.companyFormGuard = companyFormGuard;
        this.activityLog = activityLog;
    }

    /**
     * Every page and action is written to the log (activity), refused ones included; then a form shown in one
     * company and sent after switching to another (in another tab) is refused.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(activityLog);
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
