package dev.epm.admin.authorization;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** Access review row in {@code epm_access_review}. */
@Entity
@Table(name = "epm_access_review")
public class AccessReviewEntity extends PanacheEntityBase {

    @jakarta.persistence.Id
    @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    @jakarta.persistence.Column(name = "review_id")
    private Long reviewId;

    @jakarta.persistence.Column(name = "user_id")
    private String userId;

    @jakarta.persistence.Column(name = "period_due")
    private java.time.LocalDate periodDue;

    private String status;
    private String reviewer;

    @jakarta.persistence.Column(name = "signed_at_utc")
    private OffsetDateTime signedAtUtc;

    public Long getReviewId() {
        return reviewId;
    }

    public String getUserId() {
        return userId;
    }

    public java.time.LocalDate getPeriodDue() {
        return periodDue;
    }

    public String getStatus() {
        return status;
    }

    public String getReviewer() {
        return reviewer;
    }

    public OffsetDateTime getSignedAtUtc() {
        return signedAtUtc;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setPeriodDue(java.time.LocalDate periodDue) {
        this.periodDue = periodDue;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setReviewer(String reviewer) {
        this.reviewer = reviewer;
    }

    public void setSignedAtUtc(OffsetDateTime signedAtUtc) {
        this.signedAtUtc = signedAtUtc;
    }

    public static AccessReviewEntity latestFor(String userId) {
        return find("userId = ?1 order by periodDue desc", userId).firstResult();
    }
}
