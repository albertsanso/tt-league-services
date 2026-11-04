package org.cttelsamicsterrassa.data.api.rest.config.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Service credentials for platform-to-platform calls (FEAT-00101), bound from {@code security.service-credentials}.
 * Only the SHA-256 of each key is configured. Nothing is configured by default, and a malformed entry fails
 * binding so the application does not start with a silently weaker or different configuration.
 */
@ConfigurationProperties(prefix = "security")
public record ServiceCredentialProperties(List<Entry> serviceCredentials) {

    public ServiceCredentialProperties {
        serviceCredentials = serviceCredentials == null ? List.of() : List.copyOf(serviceCredentials);
        Set<String> names = new HashSet<>();
        Set<String> hashes = new HashSet<>();
        for (int index = 0; index < serviceCredentials.size(); index++) {
            Entry entry = serviceCredentials.get(index);
            if (!names.add(entry.name())) {
                throw new IllegalArgumentException(describe(index, entry.name()) + ": duplicate name");
            }
            if (!hashes.add(entry.keySha256())) {
                throw new IllegalArgumentException(describe(index, entry.name()) + ": duplicate key-sha256");
            }
        }
    }

    private static String describe(int index, String name) {
        return "security.service-credentials[" + index + "] (" + name + ")";
    }

    /**
     * One service credential: a lower-case name, the SHA-256 of its key as 64 hex characters, and the
     * {@link org.cttelsamicsterrassa.data.core.domain.auth.user.model.Permission} values it is granted.
     */
    public record Entry(String name, String keySha256, Set<String> permissions) {

        private static final Pattern NAME = Pattern.compile("[a-z0-9-]{1,40}");
        private static final Pattern SHA_256_HEX = Pattern.compile("[0-9a-fA-F]{64}");

        public Entry {
            if (name == null || !NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("security.service-credentials entry has an invalid name '"
                        + name + "': it must match [a-z0-9-]{1,40}");
            }
            if (keySha256 == null || !SHA_256_HEX.matcher(keySha256).matches()) {
                throw new IllegalArgumentException("security.service-credentials entry (" + name
                        + "): key-sha256 must be 64 hexadecimal characters");
            }
            keySha256 = keySha256.toLowerCase(Locale.ROOT);
            if (permissions == null || permissions.isEmpty()) {
                throw new IllegalArgumentException("security.service-credentials entry (" + name
                        + "): at least one permission is required");
            }
            for (String permission : permissions) {
                if (RbacCatalog.permission(permission).isEmpty()) {
                    throw new IllegalArgumentException("security.service-credentials entry (" + name
                            + "): unknown permission '" + permission + "'");
                }
            }
            permissions = Set.copyOf(permissions);
        }

        /** The decoded SHA-256 of the key, as a fresh copy. */
        public byte[] keyHash() {
            return HexFormat.of().parseHex(keySha256);
        }
    }
}
