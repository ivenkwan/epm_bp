package dev.epm.admin.masterdata;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Approved hierarchy version row in {@code epm_hierarchy_version}. */
@Entity
@Table(name = "epm_hierarchy_version")
public class HierarchyVersionEntity extends PanacheEntityBase {

    @Id
    @Column(name = "hierarchy_id")
    private String hierarchyId;

    @Column(name = "description")
    private String description;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "activated_by")
    private String activatedBy;

    @Column(name = "activated_at_utc")
    private OffsetDateTime activatedAtUtc;

    public String getHierarchyId() {
        return hierarchyId;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public void setHierarchyId(String hierarchyId) {
        this.hierarchyId = hierarchyId;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public void setEffectiveTo(LocalDate effectiveTo) {
        this.effectiveTo = effectiveTo;
    }

    public void setActivatedBy(String activatedBy) {
        this.activatedBy = activatedBy;
    }

    public void setActivatedAtUtc(OffsetDateTime activatedAtUtc) {
        this.activatedAtUtc = activatedAtUtc;
    }
}
