package dev.epm.core.api;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.container.ContainerRequestContext;

/** Resolves the active correlation id for problem rendering and resource handlers. */
@ApplicationScoped
public class CorrelationRenderer {

    /** The correlation id attached to the current request, or a stable fallback. */
    public String correlationId(ContainerRequestContext request) {
        if (request == null) {
            return "corr-unknown";
        }
        Object value = request.getProperty(CorrelationIdFilter.PROPERTY);
        return value instanceof String id && !id.isBlank() ? id : "corr-unknown";
    }
}
