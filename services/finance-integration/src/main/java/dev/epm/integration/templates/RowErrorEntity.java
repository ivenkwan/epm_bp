package dev.epm.integration.templates;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Coordinate-level row error in {@code epm_submission_row_error}. */
@Entity
@Table(name = "epm_submission_row_error")
public class RowErrorEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "error_id")
    private Long errorId;

    @Column(name = "submission_id")
    private String submissionId;

    @Column(name = "row_number")
    private int rowNumber;

    @Column(name = "column_name")
    private String columnName;

    private String code;

    private String message;

    public static RowErrorEntity of(
            String submissionId, int rowNumber, String columnName, String code, String message) {
        RowErrorEntity entity = new RowErrorEntity();
        entity.submissionId = submissionId;
        entity.rowNumber = rowNumber;
        entity.columnName = columnName;
        entity.code = code;
        entity.message = message;
        entity.persist();
        return entity;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public String getColumnName() {
        return columnName;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public static long countFor(String submissionId) {
        return find("submissionId", submissionId).count();
    }
}
