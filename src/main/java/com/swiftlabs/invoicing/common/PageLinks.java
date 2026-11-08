package com.swiftlabs.invoicing.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Links to the other pages of a list, keeping the current filters (the other request parameters).
 * Used by views: {@code th:href="${pageLinks.to(page.number + 1)}"}.
 */
public final class PageLinks {

    private static final String PAGE = "page";

    private final String path;
    private final Map<String, String[]> parameters;

    public PageLinks(HttpServletRequest request) {
        this.path = request.getRequestURI();
        this.parameters = new LinkedHashMap<>(request.getParameterMap());
        this.parameters.remove(PAGE);
    }

    /** URL of the given page (0-based); the first page has no "page" parameter. */
    public String to(int page) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(path);
        parameters.forEach((name, values) -> {
            for (String value : values) {
                if (!value.isEmpty()) {
                    builder.queryParam(name, value);
                }
            }
        });
        if (page > 0) {
            builder.queryParam(PAGE, page);
        }
        return builder.encode().build().toUriString();
    }

    /**
     * The current page in another language: the same address and parameters, with {@code lang} replaced. A page
     * reached with a parameter (the token of a password reset, a looked up number) keeps it.
     */
    public static String withLanguage(String code) {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        return UriComponentsBuilder.fromPath(request.getRequestURI()).query(request.getQueryString())
            .replaceQueryParam("lang", code).build().toUriString();
    }
}
