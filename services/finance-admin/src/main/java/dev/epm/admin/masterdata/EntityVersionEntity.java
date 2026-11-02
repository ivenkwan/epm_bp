package dev.epm.admin.masterdata;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Effective-dated entity version row in {@code epm_entity_version}; the composite key (entity,
 * effective-from) allows adjacent versions sharing boundary dates.
 */
@Entity
@Table(name = "epm_entity_version")
@IdClass(EntityVersionEntity.Key.class)
public class EntityVersionEntity extends PanacheEntityBase {

    @Id
    @Column(name = "entity_id")
    private String entityId;

    @Id
    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "entity_name")
    private String entityName;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "currency_code")
    private String currencyCode;

    @Column(name = "region")
    private String region;

    @Column(name = "restricted")
    private boolean restricted;

    public String getEntityId() {
        return entityId;
    }

    public String getEntityName() {
        return entityName;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public boolean isRestricted() {
        return restricted;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public void setEffectiveTo(LocalDate effectiveTo) {
        this.effectiveTo = effectiveTo;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public void setRestricted(boolean restricted) {
        this.restricted = restricted;
    }

    /** Entities whose interval covers the requested date (from inclusive, to exclusive). */
    public static EntityVersionEntity resolveAt(String entityId, LocalDate date) {
        return find(
                        "entityId = :entityId"
                                + " and effectiveFrom <= :atDate"
                                + " and (effectiveTo is null or effectiveTo > :atDate)",
                        java.util.Map.of("entityId", entityId, "atDate", date))
                .firstResult();
    }

    /** Composite key for effective-dated entity versions. */
    public static class Key implements Serializable {
        private static final long serialVersionUID = 1L;

        private String entityId;
        private LocalDate effectiveFrom;

        public Key() {
            // JPA
        }

        public Key(String entityId, LocalDate effectiveFrom) {
            this.entityId = entityId;
            this.effectiveFrom = effectiveFrom;
        }

        @Override
        public boolean equals(Object other) {
            if (other == null || getClass() != other.getClass()) {
                return false;
            }
            Key that = (Key) other;
            return Objects.equals(entityId, that.entityId)
                    && Objects.equals(effectiveFrom, that.effectiveFrom);
        }

        @Override
        public int hashCode() {
            return Objects.hash(entityId, effectiveFrom);
        }
    }
}
