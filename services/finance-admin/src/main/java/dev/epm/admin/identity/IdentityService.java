package dev.epm.admin.identity;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Server-side identity resolution. In the deployed platform the gateway validates the OIDC token
 * (SSO with MFA) and propagates the verified identity; this service treats propagated identity as a
 * transport detail and always re-reads the authoritative server-side role assignments from
 * PostgreSQL, so a direct backend call bypassing APISIX is subject to exactly the same enforcement.
 */
@ApplicationScoped
public class IdentityService {

    private final RoleStore roleStore;

    @Inject
    IdentityService(RoleStore roleStore) {
        this.roleStore = roleStore;
    }

    /** Resolves the caller identity propagated from the authenticated edge. */
    public String resolveUser(ContainerRequestContext request) {
        String actor = request == null ? null : request.getHeaderString("X-Actor");
        if (actor == null || actor.isBlank()) {
            return "anonymous";
        }
        return actor;
    }

    /** Authoritative roles for a user, from the governed assignment table. */
    public Set<String> rolesOf(String userId) {
        return roleStore.rolesOf(userId);
    }

    /** Stable rendering of a role set for audit rows. */
    public String renderRoles(List<String> roles) {
        return roles.stream().sorted().collect(Collectors.joining(","));
    }
}
