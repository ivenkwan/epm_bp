package dev.epm.reporting.semantic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Governed KPI card row in {@code epm_kpi_card}. */
@Entity
@Table(name = "epm_kpi_card")
public class KpiCardEntity extends PanacheEntityBase {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Id
    @Column(name = "kpi_id")
    private String kpiId;

    private String name;

    @Column(name = "formula")
    private String formula;

    private String unit;

    private String grain;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dimensions")
    private String dimensions;

    private String owner;

    private String cadence;

    private String sensitivity;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_mappings")
    private String sourceMappings;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "thresholds")
    private String thresholds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "worked_example")
    private String workedExample;

    @Column(name = "drill_policy")
    private String drillPolicy;

    private Integer version;

    private String status;

    @Column(name = "certified_by")
    private String certifiedBy;

    @Column(name = "certified_at_utc")
    private OffsetDateTime certifiedAtUtc;

    public JsonNode workedExampleNode() {
        try {
            return MAPPER.readTree(workedExample == null ? "{}" : workedExample);
        } catch (java.io.IOException e) {
            throw new SemanticException("unreadable worked example");
        }
    }

    public String getKpiId() {
        return kpiId;
    }

    public String getFormula() {
        return formula;
    }

    public String getStatus() {
        return status;
    }

    public String getOwner() {
        return owner;
    }

    public Integer getVersion() {
        return version;
    }

    public String getCertifiedBy() {
        return certifiedBy;
    }

    public String getDrillPolicy() {
        return drillPolicy;
    }

    public void setKpiId(String kpiId) {
        this.kpiId = kpiId;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setFormula(String formula) {
        this.formula = formula;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public void setGrain(String grain) {
        this.grain = grain;
    }

    public void setDimensions(String dimensions) {
        this.dimensions = dimensions;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public void setCadence(String cadence) {
        this.cadence = cadence;
    }

    public void setSensitivity(String sensitivity) {
        this.sensitivity = sensitivity;
    }

    public void setSourceMappings(String sourceMappings) {
        this.sourceMappings = sourceMappings;
    }

    public void setThresholds(String thresholds) {
        this.thresholds = thresholds;
    }

    public void setWorkedExample(String workedExample) {
        this.workedExample = workedExample;
    }

    public void setDrillPolicy(String drillPolicy) {
        this.drillPolicy = drillPolicy;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setCertifiedBy(String certifiedBy) {
        this.certifiedBy = certifiedBy;
    }

    public void setCertifiedAtUtc(OffsetDateTime certifiedAtUtc) {
        this.certifiedAtUtc = certifiedAtUtc;
    }

    /** Semantic catalog governance failures. */
    public static final class SemanticException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public SemanticException(String message) {
            super(message);
        }
    }
}
