package com.swiftlabs.invoicing.config;

import com.swiftlabs.invoicing.access.auth.AppUserPrincipal;
import com.swiftlabs.invoicing.common.Language;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.LocaleResolver;

import java.time.Duration;
import java.util.Collections;
import java.util.Locale;

/**
 * The language of a request:
 * <ol>
 *   <li>a logged-in user's own language (Mon compte), whatever the browser;</li>
 *   <li>before login, the one picked on the login page (?lang=, kept in a cookie);</li>
 *   <li>else the browser's preferred language among French, English and Arabic;</li>
 *   <li>else French.</li>
 * </ol>
 */
public class UserLocaleResolver implements LocaleResolver {

    static final String COOKIE = "lang";
    /** The language picked by ?lang= applies to the request that picks it, before the cookie comes back. */
    private static final String PICKED = UserLocaleResolver.class.getName() + ".PICKED";

    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal user) {
            return user.getLanguage().getLocale();
        }
        if (request.getAttribute(PICKED) instanceof Language picked) {
            return picked.getLocale();
        }
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (COOKIE.equals(cookie.getName())) {
                    var chosen = Language.fromCode(cookie.getValue());
                    if (chosen.isPresent()) {
                        return chosen.get().getLocale();
                    }
                }
            }
        }
        // Only a browser that says so: a request without Accept-Language is in French
        if (request.getHeader("Accept-Language") != null) {
            for (Locale locale : Collections.list(request.getLocales())) {
                var supported = Language.fromCode(locale.getLanguage());
                if (supported.isPresent()) {
                    return supported.get().getLocale();
                }
            }
        }
        return Language.FR.getLocale();
    }

    /** Called by ?lang= (LocaleChangeInterceptor): remembered in a cookie for the pages before login. */
    @Override
    public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
        Language picked = Language.of(locale);
        request.setAttribute(PICKED, picked);
        if (response == null) {
            return;
        }
        Cookie cookie = new Cookie(COOKIE, picked.getCode());
        cookie.setPath(request.getContextPath().isEmpty() ? "/" : request.getContextPath());
        cookie.setMaxAge((int) Duration.ofDays(365).toSeconds());
        cookie.setHttpOnly(true);
        response.addCookie(cookie);
    }
}
