package dev.epm.admin.authorization;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** Escalation row in {@code epm_escalation}`: reminders only, never grants access. */
@Entity
@Table(name = "epm_escalation")
public class EscalationEntity extends PanacheEntityBase {

    @jakarta.persistence.Id
    @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    @jakarta.persistence.Column(name = "escalation_id")
    private Long escalationId;

    @jakarta.persistence.Column(name = "user_id")
    private String userId;

    private String kind;
    private String detail;

    @jakarta.persistence.Column(name = "raised_at_utc")
    private OffsetDateTime raisedAtUtc;

    @jakarta.persistence.Column(name = "resolved_at_utc")
    private OffsetDateTime resolvedAtUtc;

    public Long getEscalationId() {
        return escalationId;
    }

    public String getUserId() {
        return userId;
    }

    public String getKind() {
        return kind;
    }

    public String getDetail() {
        return detail;
    }

    public OffsetDateTime getRaisedAtUtc() {
        return raisedAtUtc;
    }

    public static EscalationEntity of(String userId, String kind, String detail) {
        EscalationEntity entity = new EscalationEntity();
        entity.userId = userId;
        entity.kind = kind;
        entity.detail = detail;
        entity.raisedAtUtc = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC);
        entity.persist();
        return entity;
    }
}
