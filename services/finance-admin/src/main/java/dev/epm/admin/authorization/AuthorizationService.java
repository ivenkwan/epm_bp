package dev.epm.admin.authorization;

import dev.epm.admin.identity.IdentityService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Server-side authorization: the active versioned authority matrix, row-level entity scopes,
 * segregation of duties, self-approval guards, and access-review recency. Every check is audited
 * (allow and deny) and denial responses never echo restricted values. Enforcement happens here
 * regardless of what the gateway or browser believe.
 */
@ApplicationScoped
public class AuthorizationService {

    private final IdentityService identity;

    @Inject
    AuthorizationService(IdentityService identity) {
        this.identity = identity;
    }

    /** Full authorization decision for one (user, object, action, entity) request. */
    @Transactional
    public Decision authorize(
            String userId,
            String objectType,
            String action,
            String targetEntity,
            String preparerOfRecord,
            String correlationId) {
        Set<String> roles = identity.rolesOf(userId);
        String rolesRendered = identity.renderRoles(new ArrayList<>(roles));

        Integer policyId = PolicyEntity.activePolicyId();
        if (policyId == null) {
            return deny(
                    userId,
                    rolesRendered,
                    objectType,
                    action,
                    targetEntity,
                    "no active policy",
                    correlationId);
        }

        // Segregation of duties first: conflicting role pairs can never pass.
        for (String roleA : roles) {
            for (String roleB : roles) {
                if (!roleA.equals(roleB)
                        && SodConstraintEntity.countConflicts(policyId, roleA, roleB) > 0) {
                    EscalationEntity.of(
                            userId,
                            "SOD_CONFLICT",
                            "user holds conflicting roles " + roleA + " and " + roleB);
                    return deny(
                            userId,
                            rolesRendered,
                            objectType,
                            action,
                            targetEntity,
                            "segregation of duties conflict",
                            correlationId);
                }
            }
        }

        boolean permitted = false;
        for (String role : roles) {
            if (PermissionEntity.countMatching(policyId, role, objectType, action) > 0) {
                permitted = true;
                break;
            }
        }
        if (!permitted) {
            return deny(
                    userId,
                    rolesRendered,
                    objectType,
                    action,
                    targetEntity,
                    "no role grants this action on this object type",
                    correlationId);
        }

        // No person approves their own controlled work.
        if (("APPROVE".equals(action) || "CERTIFY".equals(action) || "PUBLISH".equals(action))
                && userId.equals(preparerOfRecord)) {
            return deny(
                    userId,
                    rolesRendered,
                    objectType,
                    action,
                    targetEntity,
                    "self-approval is prohibited",
                    correlationId);
        }

        // Row-level scope: the target entity must be granted, unrevoked, and review-current.
        if (targetEntity != null) {
            EntityScopeEntity scope =
                    EntityScopeEntity.find("userId = ?1 and entityId = ?2", userId, targetEntity)
                            .firstResult();
            if (scope == null || scope.getRevokedAtUtc() != null) {
                return deny(
                        userId,
                        rolesRendered,
                        objectType,
                        action,
                        targetEntity,
                        "target entity is outside the user scope",
                        correlationId);
            }
            if (scope.getReviewDueUtc() != null
                    && scope.getReviewDueUtc().isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
                EscalationEntity.of(
                        userId,
                        "ACCESS_REVIEW_OVERDUE",
                        "scope review overdue for entity " + targetEntity);
                return deny(
                        userId,
                        rolesRendered,
                        objectType,
                        action,
                        targetEntity,
                        "access review overdue",
                        correlationId);
            }
        }

        return allow(userId, rolesRendered, objectType, action, targetEntity, correlationId);
    }

    /** Entities visible to the user under current grants; an empty scope yields zero rows. */
    @Transactional
    public List<String> scopedEntities(String userId) {
        List<String> visible = new ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<EntityScopeEntity> grants = EntityScopeEntity.find("userId", userId).list();
        for (EntityScopeEntity grant : grants) {
            if (grant.getRevokedAtUtc() == null
                    && (grant.getReviewDueUtc() == null || grant.getReviewDueUtc().isAfter(now))) {
                visible.add(grant.getEntityId());
            }
        }
        return visible;
    }

    /** Opens the next review period for a user; reviewers sign independently later. */
    @Transactional
    public long openReviewPeriod(String userId, String grantedBy) {
        AccessReviewEntity review = new AccessReviewEntity();
        review.setUserId(userId);
        review.setPeriodDue(LocalDate.now(ZoneOffset.UTC).plusMonths(3));
        review.setStatus("PENDING");
        review.persist();
        return review.getReviewId() == null ? 0L : review.getReviewId();
    }

    private Decision allow(
            String userId,
            String roles,
            String objectType,
            String action,
            String entity,
            String correlationId) {
        AccessDecisionEntity.record(
                userId, roles, objectType, action, entity, true, "authorized", correlationId);
        return new Decision(true, "authorized");
    }

    private Decision deny(
            String userId,
            String roles,
            String objectType,
            String action,
            String entity,
            String reason,
            String correlationId) {
        AccessDecisionEntity.record(
                userId, roles, objectType, action, entity, false, reason, correlationId);
        return new Decision(false, reason);
    }

    /** Safe decision result: carries no restricted values or role details. */
    public record Decision(boolean allowed, String reason) {}
}
