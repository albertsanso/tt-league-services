package org.cttelsamicsterrassa.data.pipeline.runtime.security;

import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Builds the decoder for platform tokens. The platform signs with jjwt {@code signWith(key)}, which picks the
 * algorithm from the secret's UTF-8 length: 32-47 bytes HS256, 48-63 bytes HS384, 64 bytes or more HS512. The
 * decoder picks the same one. Expiry is checked with the default 60 second clock skew.
 */
public final class PlatformJwtDecoderFactory {

    private PlatformJwtDecoderFactory() {
    }

    public static MacAlgorithm algorithmFor(byte[] secret) {
        if (secret.length >= 64) {
            return MacAlgorithm.HS512;
        }
        if (secret.length >= 48) {
            return MacAlgorithm.HS384;
        }
        if (secret.length >= 32) {
            return MacAlgorithm.HS256;
        }
        throw new IllegalArgumentException("The JWT secret must be at least 32 UTF-8 bytes");
    }

    public static JwtDecoder create(String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        MacAlgorithm algorithm = algorithmFor(bytes);
        String jcaName = switch (algorithm) {
            case HS512 -> "HmacSHA512";
            case HS384 -> "HmacSHA384";
            default -> "HmacSHA256";
        };
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(bytes, jcaName))
                .macAlgorithm(algorithm)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefault());
        return decoder;
    }
}
