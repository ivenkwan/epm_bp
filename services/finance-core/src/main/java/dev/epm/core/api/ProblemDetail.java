package dev.epm.core.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * RFC 7807-style problem details with the platform extensions: stable {@code code}, {@code
 * correlationId}, and field {@code violations}. Serialized as {@code application/problem+json};
 * never contains stack traces or secrets.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProblemDetail(
        String type,
        String title,
        int status,
        String detail,
        String instance,
        String code,
        String correlationId,
        List<Violation> violations) {

    /** One field-level violation with a stable code and a human-safe message. */
    public record Violation(String field, String code, String message)
            implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
    }

    static ProblemDetail of(
            int status,
            String code,
            String title,
            String detail,
            String correlationId,
            List<Violation> violations) {
        return new ProblemDetail(
                "about:blank", title, status, detail, null, code, correlationId, violations);
    }
}
