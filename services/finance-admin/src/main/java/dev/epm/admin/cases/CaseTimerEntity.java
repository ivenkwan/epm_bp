package dev.epm.admin.cases;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** Advisory timer register row in {@code epm_case_timer}. */
@Entity
@Table(name = "epm_case_timer")
public class CaseTimerEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "timer_id")
    private Long timerId;

    @Column(name = "case_id")
    private String caseId;

    private String milestone;

    @Column(name = "due_at_utc")
    private LocalDateTime dueAtUtc;

    @Column(name = "fired_at_utc")
    private LocalDateTime firedAtUtc;

    private String action;

    public static CaseTimerEntity schedule(
            String caseId, String milestone, LocalDateTime dueAtUtc, String action) {
        CaseTimerEntity entity = new CaseTimerEntity();
        entity.caseId = caseId;
        entity.milestone = milestone;
        entity.dueAtUtc = dueAtUtc;
        entity.action = action;
        entity.persist();
        return entity;
    }

    /**
     * Timer fire behavior: advisory actions only (remind/reassign/escalate/visibility). A timer can
     * never approve, close, certify, publish, post, pay, or adjust.
     */
    public void fire() {
        if (this.firedAtUtc == null) {
            this.firedAtUtc = LocalDateTime.now(java.time.ZoneOffset.UTC);
        }
    }

    public String getAction() {
        return action;
    }

    public Long getTimerId() {
        return timerId;
    }

    public String getCaseId() {
        return caseId;
    }
}
