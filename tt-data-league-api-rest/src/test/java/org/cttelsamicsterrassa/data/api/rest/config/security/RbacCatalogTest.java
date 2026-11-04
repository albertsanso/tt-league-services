package org.cttelsamicsterrassa.data.api.rest.config.security;

import org.cttelsamicsterrassa.data.core.domain.auth.user.model.Permission;
import org.cttelsamicsterrassa.data.core.domain.auth.user.model.UserRole;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RbacCatalogTest {

    @Test
    void importsWriteIsGrantedToAdminOnly() {
        assertTrue(UserRole.ADMIN.permissions().contains(Permission.IMPORTS_WRITE));
        for (UserRole role : UserRole.values()) {
            if (role != UserRole.ADMIN) {
                assertFalse(role.permissions().contains(Permission.IMPORTS_WRITE), role.name());
            }
        }
        assertEquals("imports:write", RbacCatalog.IMPORTS_WRITE);
        assertTrue(RbacCatalog.permissionNames(Set.of("ADMIN")).contains(RbacCatalog.IMPORTS_WRITE));
    }

    @Test
    void permissionsAreLookedUpByTheirValue() {
        assertEquals(Optional.of(Permission.IMPORTS_WRITE), RbacCatalog.permission("imports:write"));
        assertEquals(Optional.of(Permission.MATCHES_READ), RbacCatalog.permission("matches:read"));
        assertTrue(RbacCatalog.permission("IMPORTS_WRITE").isEmpty());
        assertTrue(RbacCatalog.permission(null).isEmpty());
    }
}
