package dev.epm.reporting.semantic;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One governed evaluation of a KPI card version against an input snapshot. */
@Entity
@Table(name = "epm_kpi_evaluation")
public class KpiEvaluationEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "evaluation_id")
    private Long evaluationId;

    @Column(name = "kpi_id")
    private String kpiId;

    @Column(name = "kpi_version")
    private Integer kpiVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_snapshot")
    private String inputSnapshot;

    @Column(name = "result_value")
    private String resultValue;

    @Column(name = "result_status")
    private String resultStatus;

    @Column(name = "evaluated_at_utc")
    private OffsetDateTime evaluatedAtUtc;

    public static KpiEvaluationEntity record(
            String kpiId,
            Integer kpiVersion,
            String inputSnapshot,
            String resultValue,
            String resultStatus) {
        KpiEvaluationEntity entity = new KpiEvaluationEntity();
        entity.kpiId = kpiId;
        entity.kpiVersion = kpiVersion;
        entity.inputSnapshot = inputSnapshot;
        entity.resultValue = resultValue;
        entity.resultStatus = resultStatus;
        entity.evaluatedAtUtc = OffsetDateTime.now(java.time.ZoneOffset.UTC);
        entity.persist();
        return entity;
    }

    public String getResultValue() {
        return resultValue;
    }

    public String getResultStatus() {
        return resultStatus;
    }
}
