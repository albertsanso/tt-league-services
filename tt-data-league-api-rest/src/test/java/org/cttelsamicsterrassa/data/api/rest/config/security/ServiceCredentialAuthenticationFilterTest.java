package org.cttelsamicsterrassa.data.api.rest.config.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceCredentialAuthenticationFilterTest {

    private static final String KEY = UUID.randomUUID().toString();

    private final ServiceCredentialAuthenticationFilter filter = new ServiceCredentialAuthenticationFilter(
            new ServiceCredentialProperties(List.of(
                    new ServiceCredentialProperties.Entry("orchestrator", sha256Hex(KEY),
                            Set.of("imports:write", "matches:read")),
                    new ServiceCredentialProperties.Entry("other", sha256Hex(UUID.randomUUID().toString()),
                            Set.of("matches:read")))));

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Test
    void aRequestWithoutAnApiKeyContinuesUnauthenticated() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/match");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
        assertEquals(200, response.getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void aValidKeyAuthenticatesAsTheServiceWithOnlyItsConfiguredPermissions() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/match");
        request.addHeader("X-API-Key", KEY);
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertEquals("service:orchestrator", authentication.getName());
        assertTrue(authentication.isAuthenticated());
        Set<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        assertEquals(Set.of("imports:write", "matches:read"), authorities);
        assertTrue(authorities.stream().noneMatch(authority -> authority.startsWith("ROLE_")));
    }

    @Test
    void aWrongKeyIs401AndStopsTheChain() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/match");
        request.addHeader("X-API-Key", KEY + "x");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertNull(chain.getRequest());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void anApiKeyIs401WhenNoCredentialIsConfigured() throws Exception {
        var noCredentials = new ServiceCredentialAuthenticationFilter(new ServiceCredentialProperties(null));
        var request = new MockHttpServletRequest("GET", "/api/v1/match");
        request.addHeader("X-API-Key", KEY);
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        noCredentials.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertNull(chain.getRequest());
    }

    @Test
    void anApiKeyTogetherWithAnAuthorizationHeaderIs400() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/match");
        request.addHeader("X-API-Key", KEY);
        request.addHeader("Authorization", "Bearer abc");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(400, response.getStatus());
        assertNull(chain.getRequest());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void optionsRequestsPassThroughEvenWithAWrongKey() throws Exception {
        var request = new MockHttpServletRequest("OPTIONS", "/api/v1/match");
        request.addHeader("X-API-Key", "wrong");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
        assertEquals(200, response.getStatus());
    }
}
