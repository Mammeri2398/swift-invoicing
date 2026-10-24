package com.swiftlabs.invoicing.access.twofactor;

import com.swiftlabs.invoicing.access.auth.AppUserPrincipal;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

import java.io.IOException;

/**
 * After a correct password: users with two-factor login are asked for their code first; the others
 * go back to the page that required the login, otherwise "/".
 */
public class TwoFactorLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final SavedRequestAwareAuthenticationSuccessHandler target = new SavedRequestAwareAuthenticationSuccessHandler();

    public TwoFactorLoginSuccessHandler() {
        target.setDefaultTargetUrl("/");
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
        throws IOException, ServletException {
        if (authentication.getPrincipal() instanceof AppUserPrincipal principal && principal.isTwoFactorEnabled()) {
            TwoFactorLogin.begin(authentication, request, response);
            response.sendRedirect(request.getContextPath() + TwoFactorLogin.VERIFY_URL);
            return;
        }
        target.onAuthenticationSuccess(request, response, authentication);
    }

    /** Where the login finally leads, once the code is accepted. */
    void continueTo(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
        throws IOException, ServletException {
        target.onAuthenticationSuccess(request, response, authentication);
    }
}
