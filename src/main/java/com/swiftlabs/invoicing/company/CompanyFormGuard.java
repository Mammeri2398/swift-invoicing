package com.swiftlabs.invoicing.company;

import com.swiftlabs.invoicing.access.auth.AppUserPrincipal;
import com.swiftlabs.invoicing.common.CommonCodes;
import com.swiftlabs.invoicing.common.Endpoints;
import com.swiftlabs.invoicing.common.MessageProperties;
import com.swiftlabs.invoicing.common.Messages;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.springframework.web.servlet.support.RequestDataValueProcessor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Two tabs share one session: switching company in one changes it in the other. Every form therefore carries the
 * company it was shown in (a hidden field, added like the CSRF token), and a form sent from another company is
 * refused instead of being saved in the wrong one.
 */
@Component
public class CompanyFormGuard implements HandlerInterceptor {

    /** The hidden field of every POST form. */
    public static final String FIELD = "_company";

    private static final String METHOD_ATTRIBUTE = CompanyFormGuard.class.getName() + ".method";

    private final Messages messages;

    public CompanyFormGuard(Messages messages) {
        this.messages = messages;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String sent = request.getParameter(FIELD);
        Long current = currentCompanyId();
        if (sent == null || current == null || "GET".equals(request.getMethod()) || sent.equals(String.valueOf(current))
            || request.getRequestURI().startsWith(request.getContextPath() + Endpoints.COMPANIES)) {
            return true;
        }
        String target = request.getContextPath() + "/";
        RequestContextUtils.getOutputFlashMap(request).put(CommonCodes.Attributes.ERROR, messages.get(MessageProperties.COMPANY_ERROR_SWITCHED));
        RequestContextUtils.saveOutputFlashMap(target, request, response);
        response.sendRedirect(target);
        return false;
    }

    private static Long currentCompanyId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal
            ? principal.getCompanyId() : null;
    }

    /** Adds the company field to the forms rendered with th:action, next to Spring Security's CSRF token. */
    @Component
    static class HiddenField implements BeanPostProcessor {

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
            if (bean instanceof RequestDataValueProcessor delegate && !(bean instanceof WithCompany)) {
                return new WithCompany(delegate);
            }
            return bean;
        }
    }

    private record WithCompany(RequestDataValueProcessor delegate) implements RequestDataValueProcessor {

        @Override
        public String processAction(HttpServletRequest request, String action, String method) {
            request.setAttribute(METHOD_ATTRIBUTE, method);
            return delegate.processAction(request, action, method);
        }

        @Override
        public String processFormFieldValue(HttpServletRequest request, String name, String value, String type) {
            return delegate.processFormFieldValue(request, name, value, type);
        }

        @Override
        public Map<String, String> getExtraHiddenFields(HttpServletRequest request) {
            Map<String, String> fields = new LinkedHashMap<>();
            Map<String, String> others = delegate.getExtraHiddenFields(request);
            if (others != null) {
                fields.putAll(others);
            }
            Long company = currentCompanyId();
            // Not on GET forms (filters, search): the field would only clutter their URLs
            if (company != null && !"get".equalsIgnoreCase((String) request.getAttribute(METHOD_ATTRIBUTE))) {
                fields.put(FIELD, String.valueOf(company));
            }
            return fields;
        }

        @Override
        public String processUrl(HttpServletRequest request, String url) {
            return delegate.processUrl(request, url);
        }
    }
}
