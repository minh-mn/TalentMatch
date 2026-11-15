package com.talentmatch.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityConfigTest {
    @Test
    void successfulLoginResumesTheRequestedPage() throws Exception {
        MockHttpServletRequest protectedRequest = new MockHttpServletRequest("GET", "/payment/project/42");
        protectedRequest.setScheme("http");
        protectedRequest.setServerName("localhost");
        protectedRequest.setServerPort(8080);
        new HttpSessionRequestCache().saveRequest(protectedRequest, new MockHttpServletResponse());

        MockHttpServletRequest loginRequest = new MockHttpServletRequest("POST", "/login");
        loginRequest.setSession(protectedRequest.getSession());
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthenticationSuccessHandler handler = new SecurityConfig().successHandler();

        handler.onAuthenticationSuccess(loginRequest, response, customerAuthentication());

        assertTrue(response.getRedirectedUrl().startsWith("http://localhost:8080/payment/project/42"));
    }

    @Test
    void successfulLoginUsesRoleDashboardWhenThereIsNoSavedPage() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new SecurityConfig().successHandler().onAuthenticationSuccess(
                new MockHttpServletRequest("POST", "/login"), response, customerAuthentication());

        assertEquals("/customer/dashboard", response.getRedirectedUrl());
    }

    private Authentication customerAuthentication() {
        var user = User.withUsername("client@example.test").password("unused").roles("CUSTOMER").build();
        return new UsernamePasswordAuthenticationToken(user, "unused", user.getAuthorities());
    }
}
