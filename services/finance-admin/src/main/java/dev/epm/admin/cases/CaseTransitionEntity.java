package dev.epm.admin.cases;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** Durable state-machine transition evidence in {@code epm_case_transition}. */
@Entity
@Table(name = "epm_case_transition")
public class CaseTransitionEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transition_id")
    private Long transitionId;

    @Column(name = "case_id")
    private String caseId;

    @Column(name = "from_state")
    private String fromState;

    @Column(name = "to_state")
    private String toState;

    private String actor;

    private String note;

    @Column(name = "at_utc")
    private LocalDateTime atUtc;

    public static CaseTransitionEntity record(
            String caseId, String from, String to, String actor, String note) {
        CaseTransitionEntity entity = new CaseTransitionEntity();
        entity.caseId = caseId;
        entity.fromState = from;
        entity.toState = to;
        entity.actor = actor;
        entity.note = note;
        entity.atUtc = LocalDateTime.now(java.time.ZoneOffset.UTC);
        entity.persist();
        return entity;
    }

    public String getCaseId() {
        return caseId;
    }

    public String getFromState() {
        return fromState;
    }

    public String getToState() {
        return toState;
    }

    public String getActor() {
        return actor;
    }

    public String getNote() {
        return note;
    }
}
