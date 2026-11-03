package dev.epm.admin.identity;

import dev.epm.admin.authorization.UserRoleEntity;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashSet;
import java.util.Set;

/** Governed role assignments; the browser and gateway can add nothing here. */
@ApplicationScoped
public class RoleStore {

    /** Roles granted to the user (unrevoked by design: grants are versioned rows). */
    public Set<String> rolesOf(String userId) {
        Set<String> roles = new HashSet<>();
        for (UserRoleEntity granted :
                UserRoleEntity.<UserRoleEntity>find("userId", userId).list()) {
            roles.add(granted.getRoleId());
        }
        return roles;
    }

    /** Grants a role with provenance. */
    public void grant(String userId, String roleId, String grantedBy) {
        if (UserRoleEntity.findById(new UserRoleEntity.UserRoleId(userId, roleId)) == null) {
            UserRoleEntity entity = new UserRoleEntity();
            entity.setUserId(userId);
            entity.setRoleId(roleId);
            entity.setGrantedBy(grantedBy);
            entity.setGrantedAtUtc(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC));
            entity.persist();
        }
    }
}
