package dev.epm.core.api;

import java.util.List;

/** Carries problem semantics across resource code into the exception mapper. */
public class ProblemException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int status;
    private final String code;
    private final String title;
    private final String detail;
    // Exceptions never leave the JVM; the transient list avoids a serializable-field
    // warning on the List erasure while keeping the violations fully readable in-JVM.
    private final transient List<ProblemDetail.Violation> violations;

    /** Full problem; {@code detail} must be human-safe (no internals). */
    public ProblemException(
            int status,
            String code,
            String title,
            String detail,
            List<ProblemDetail.Violation> violations) {
        super(title + (detail == null ? "" : (": " + detail)));
        this.status = status;
        this.code = code;
        this.title = title;
        this.detail = detail;
        this.violations = violations == null ? List.of() : violations;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String title() {
        return title;
    }

    public String detail() {
        return detail;
    }

    public List<ProblemDetail.Violation> violations() {
        return violations;
    }
}
