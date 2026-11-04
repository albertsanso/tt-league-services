package org.cttelsamicsterrassa.data.pipeline.runtime.security;

import java.util.ArrayList;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Maps the platform claims: the principal name is {@code sub}; every {@code permissions} entry becomes an authority
 * as is ({@code matches:write}) and every {@code roles} entry becomes {@code ROLE_<role>}. Missing or non-list
 * claims give no authorities.
 */
public final class PlatformJwtAuthenticationConverter
        implements Converter<Jwt, AbstractOAuth2TokenAuthenticationToken<Jwt>> {

    @Override
    public AbstractOAuth2TokenAuthenticationToken<Jwt> convert(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new InvalidBearerTokenException("The token has no subject");
        }
        List<GrantedAuthority> authorities = new ArrayList<>();
        for (String permission : strings(jwt.getClaim("permissions"))) {
            authorities.add(new SimpleGrantedAuthority(permission));
        }
        for (String role : strings(jwt.getClaim("roles"))) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        }
        return new JwtAuthenticationToken(jwt, authorities, subject);
    }

    private static List<String> strings(Object claim) {
        List<String> values = new ArrayList<>();
        if (claim instanceof Iterable<?> items) {
            for (Object item : items) {
                if (item instanceof String text && !text.isBlank()) {
                    values.add(text);
                }
            }
        }
        return values;
    }
}
