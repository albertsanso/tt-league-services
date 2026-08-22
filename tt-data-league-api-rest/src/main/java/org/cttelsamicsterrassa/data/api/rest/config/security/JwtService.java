package org.cttelsamicsterrassa.data.api.rest.config.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Component
public class JwtService {
    private static final String TEST_SECRET = "test-only-signing-key-012345678901";
    private static final long DEFAULT_EXPIRATION_MILLIS = 30L * 60L * 60L * 1000L;

    private final SecretKey signingKey;
    private final long expirationMillis;

    public JwtService() {
        this(TEST_SECRET, DEFAULT_EXPIRATION_MILLIS, true);
    }

    public JwtService(String secret) {
        this(secret, DEFAULT_EXPIRATION_MILLIS, true);
    }

    @Autowired
    public JwtService(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.expiration-millis:108000000}") long expirationMillis) {
        this(secret, expirationMillis, true);
    }

    private JwtService(String secret, long expirationMillis, boolean configured) {
        if (secret == null || secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("security.jwt.secret must contain at least 32 UTF-8 bytes");
        }
        if (expirationMillis <= 0) {
            throw new IllegalArgumentException("security.jwt.expiration-millis must be positive");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        this.expirationMillis = expirationMillis;
    }

    public String generateToken(String username) {
        return generateToken(username, List.of(), List.of());
    }

    public String generateToken(String username, Collection<String> roleNames) {
        return generateToken(username, roleNames,
                RbacCatalog.permissionNames(roleNames));
    }

    public String generateToken(
            String username,
            Collection<String> roleNames,
            Collection<String> permissionNames) {
        Map<String, Object> claims = new HashMap<>();
        String jti = UUID.randomUUID().toString();
        claims.put("jti", jti);
        claims.put("roles", List.copyOf(roleNames));
        claims.put("permissions", List.copyOf(permissionNames));

        long now = System.currentTimeMillis();
        long expiryMillis = now + expirationMillis;

        return Jwts.builder()
                .setSubject(username)
                .addClaims(claims)
                .setIssuedAt(new Date())
                .setExpiration(new Date(expiryMillis))
                .signWith(signingKey)
                .compact();
    }

    public List<String> extractRoles(String token) {
        return extractStringList(token, "roles");
    }

    public List<String> extractPermissions(String token) {
        return extractStringList(token, "permissions");
    }

    public String extractJti(String token) {
        return extractClaim(token, claims -> claims.get("jti", String.class));
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public boolean validateToken(String token, UserDetails userDetails) {
        String extractedUsername = extractUsername(token);
        return extractedUsername != null
                && extractedUsername.equals(userDetails.getUsername())
                && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimResolver) {
        final Claims claims = extractAllClaims(token);
        return claimResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private List<String> extractStringList(String token, String claimName) {
        Object values = extractClaim(token, claims -> claims.get(claimName));
        if (!(values instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
    }
}
