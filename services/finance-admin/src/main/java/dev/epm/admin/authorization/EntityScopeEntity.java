package dev.epm.admin.authorization;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** Entity scope grant row in {@code epm_entity_scope}. */
@Entity
@Table(name = "epm_entity_scope")
public class EntityScopeEntity extends PanacheEntityBase {

    @Id
    @jakarta.persistence.Column(name = "user_id")
    private String userId;

    @Id
    @jakarta.persistence.Column(name = "entity_id")
    private String entityId;

    @jakarta.persistence.Column(name = "granted_by")
    private String grantedBy;

    @jakarta.persistence.Column(name = "granted_at_utc")
    private OffsetDateTime grantedAtUtc;

    @jakarta.persistence.Column(name = "review_due_utc")
    private OffsetDateTime reviewDueUtc;

    @jakarta.persistence.Column(name = "revoked_at_utc")
    private OffsetDateTime revokedAtUtc;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getGrantedBy() {
        return grantedBy;
    }

    public void setGrantedBy(String grantedBy) {
        this.grantedBy = grantedBy;
    }

    public OffsetDateTime getGrantedAtUtc() {
        return grantedAtUtc;
    }

    public void setGrantedAtUtc(OffsetDateTime grantedAtUtc) {
        this.grantedAtUtc = grantedAtUtc;
    }

    public OffsetDateTime getReviewDueUtc() {
        return reviewDueUtc;
    }

    public void setReviewDueUtc(OffsetDateTime reviewDueUtc) {
        this.reviewDueUtc = reviewDueUtc;
    }

    public OffsetDateTime getRevokedAtUtc() {
        return revokedAtUtc;
    }

    public void setRevokedAtUtc(OffsetDateTime revokedAtUtc) {
        this.revokedAtUtc = revokedAtUtc;
    }
}
