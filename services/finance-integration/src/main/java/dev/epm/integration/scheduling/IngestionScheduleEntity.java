package dev.epm.integration.scheduling;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Registered ingestion schedule in {@code epm_ingestion_schedule}. */
@Entity
@Table(name = "epm_ingestion_schedule")
public class IngestionScheduleEntity extends PanacheEntityBase {

    @Id
    @Column(name = "schedule_id")
    private String scheduleId;

    @Column(name = "contract_id")
    private String contractId;

    @Column(name = "system_id")
    private String systemId;

    private String cron;

    private boolean enabled;

    public String getScheduleId() {
        return scheduleId;
    }

    public String getContractId() {
        return contractId;
    }

    public String getSystemId() {
        return systemId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public static IngestionScheduleEntity findEnabled(String scheduleId) {
        IngestionScheduleEntity entity = findById(scheduleId);
        return entity != null && entity.enabled ? entity : null;
    }
}
