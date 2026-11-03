package dev.epm.core.api;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.util.UUID;

/**
 * Correlation propagation: honors an inbound {@code X-Correlation-Id} or generates one, exposes it
 * to resources and problem rendering, and echoes it on every response so a request can be traced
 * across services and telemetry.
 */
@Provider
public class CorrelationIdFilter implements ContainerRequestFilter, ContainerResponseFilter {

    public static final String HEADER = "X-Correlation-Id";
    static final String PROPERTY = "epm.correlationId";

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String correlationId = requestContext.getHeaderString(HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = "corr-" + UUID.randomUUID();
        }
        requestContext.setProperty(PROPERTY, correlationId);
    }

    @Override
    public void filter(
            ContainerRequestContext requestContext, ContainerResponseContext responseContext)
            throws IOException {
        Object correlationId = requestContext.getProperty(PROPERTY);
        if (correlationId instanceof String value) {
            responseContext.getHeaders().putSingle(HEADER, value);
        }
    }
}
