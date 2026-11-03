package dev.epm.integration.contracts;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** Immutable status-transition evidence row in {@code epm_source_contract_transition}. */
@Entity
@Table(name = "epm_source_contract_transition")
public class ContractTransitionEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transition_id")
    private Long transitionId;

    @Column(name = "contract_id")
    private String contractId;

    private Integer version;

    @Column(name = "from_status")
    private String fromStatus;

    @Column(name = "to_status")
    private String toStatus;

    private String actor;

    private String reason;

    @Column(name = "at_utc")
    private LocalDateTime atUtc;

    public static ContractTransitionEntity record(
            String contractId,
            Integer version,
            String from,
            String to,
            String actor,
            String reason) {
        ContractTransitionEntity entity = new ContractTransitionEntity();
        entity.contractId = contractId;
        entity.version = version;
        entity.fromStatus = from == null ? "NONE" : from;
        entity.toStatus = to;
        entity.actor = actor;
        entity.reason = reason;
        entity.atUtc = LocalDateTime.now(java.time.ZoneOffset.UTC);
        entity.persist();
        return entity;
    }

    public String getToStatus() {
        return toStatus;
    }

    public String getActor() {
        return actor;
    }
}
