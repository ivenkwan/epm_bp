package dev.epm.admin.masterdata;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** Hierarchy node row in {@code epm_hierarchy_node} (from inclusive, to exclusive). */
@Entity
@Table(name = "epm_hierarchy_node")
public class HierarchyNodeEntity extends PanacheEntityBase {

    @Id
    @Column(name = "hierarchy_id")
    private String hierarchyId;

    @Id
    @Column(name = "entity_id")
    private String entityId;

    @Id
    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "parent_entity_id")
    private String parentEntityId;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    public String getHierarchyId() {
        return hierarchyId;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getParentEntityId() {
        return parentEntityId;
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

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public void setParentEntityId(String parentEntityId) {
        this.parentEntityId = parentEntityId;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public void setEffectiveTo(LocalDate effectiveTo) {
        this.effectiveTo = effectiveTo;
    }
}
