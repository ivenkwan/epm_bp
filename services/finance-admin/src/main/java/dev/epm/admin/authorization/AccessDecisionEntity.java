package dev.epm.admin.authorization;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** Immutable decision audit row in {@code epm_access_decision} (allow and deny). */
@Entity
@Table(name = "epm_access_decision")
public class AccessDecisionEntity extends PanacheEntityBase {

    @jakarta.persistence.Id
    @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    @jakarta.persistence.Column(name = "decision_id")
    private Long decisionId;

    @jakarta.persistence.Column(name = "user_id")
    private String userId;

    private String roles;

    @jakarta.persistence.Column(name = "object_type")
    private String objectType;

    private String action;

    @jakarta.persistence.Column(name = "target_entity")
    private String targetEntity;

    private String decision;
    private String reason;

    @jakarta.persistence.Column(name = "correlation_id")
    private String correlationId;

    @jakarta.persistence.Column(name = "decided_at_utc")
    private OffsetDateTime decidedAtUtc;

    public static AccessDecisionEntity record(
            String userId,
            String roles,
            String objectType,
            String action,
            String targetEntity,
            boolean allow,
            String reason,
            String correlationId) {
        AccessDecisionEntity entity = new AccessDecisionEntity();
        entity.userId = userId;
        entity.roles = roles;
        entity.objectType = objectType;
        entity.action = action;
        entity.targetEntity = targetEntity;
        entity.decision = allow ? "ALLOW" : "DENY";
        entity.reason = reason;
        entity.correlationId = correlationId;
        entity.decidedAtUtc = OffsetDateTime.now(java.time.ZoneOffset.UTC);
        entity.persist();
        return entity;
    }

    public String getDecision() {
        return decision;
    }

    public String getReason() {
        return reason;
    }

    public String getUserId() {
        return userId;
    }

    public static long countFor(String userId) {
        return find("userId = ?1", userId).count();
    }
}
