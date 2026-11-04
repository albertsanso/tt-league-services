package org.cttelsamicsterrassa.data.api.rest.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;

/**
 * Authenticates platform-to-platform calls that present an {@code X-API-Key} (FEAT-00101). The key is hashed with
 * SHA-256 and compared in constant time with the configured hashes. A match authenticates as {@code service:<name>}
 * with exactly the configured permissions and no roles. The key and its hash are never logged.
 */
public class ServiceCredentialAuthenticationFilter extends OncePerRequestFilter {

    static final String API_KEY_HEADER = "X-API-Key";
    static final String PRINCIPAL_PREFIX = "service:";

    private final List<ServiceCredentialProperties.Entry> credentials;

    public ServiceCredentialAuthenticationFilter(ServiceCredentialProperties properties) {
        this.credentials = properties.serviceCredentials();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        String apiKey = request.getHeader(API_KEY_HEADER);
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || apiKey == null) {
            filterChain.doFilter(request, response);
            return;
        }
        if (request.getHeader("Authorization") != null) {
            SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "Use either a bearer token or an API key, not both");
            return;
        }
        Optional<ServiceCredentialProperties.Entry> match = authenticate(apiKey);
        if (match.isEmpty()) {
            SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid API key");
            return;
        }
        ServiceCredentialProperties.Entry credential = match.get();
        List<GrantedAuthority> authorities = credential.permissions().stream()
                .sorted()
                .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                .toList();
        SecurityContextHolder.getContext().setAuthentication(new PreAuthenticatedAuthenticationToken(
                PRINCIPAL_PREFIX + credential.name(), null, authorities));
        filterChain.doFilter(request, response);
    }

    /** Compares against every configured hash, so the time taken does not reveal which entry matched. */
    private Optional<ServiceCredentialProperties.Entry> authenticate(String apiKey) {
        byte[] presented = sha256(apiKey);
        ServiceCredentialProperties.Entry match = null;
        for (ServiceCredentialProperties.Entry credential : credentials) {
            if (MessageDigest.isEqual(presented, credential.keyHash())) {
                match = credential;
            }
        }
        return Optional.ofNullable(match);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
