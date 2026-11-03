package dev.epm.core.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Stable problem+json media type (jakarta.ws.rs does not ship a typed constant). */
record ProblemJsonType() {
    static final String MEDIA_TYPE = "application/problem+json";
}

/** Renders every failure as safe {@code application/problem+json} with a correlation id. */
@Provider
public class ProblemExceptionMapper implements ExceptionMapper<Exception> {

    @Context ContainerRequestContext request;

    @Inject CorrelationRenderer renderer;

    @Override
    public Response toResponse(Exception exception) {
        String correlationId = renderer.correlationId(request);
        if (exception instanceof ProblemException problem) {
            ProblemDetail detail =
                    ProblemDetail.of(
                            problem.status(),
                            problem.code(),
                            problem.title(),
                            problem.detail(),
                            correlationId,
                            problem.violations());
            return Response.status(problem.status())
                    .type(jakarta.ws.rs.core.MediaType.valueOf(ProblemJsonType.MEDIA_TYPE))
                    .entity(detail)
                    .build();
        }
        if (exception instanceof jakarta.ws.rs.WebApplicationException web) {
            // Routing and client errors (404 unknown route, 405, ...) keep their status
            // but are still rendered as safe problem+json.
            int status = web.getResponse().getStatus();
            ProblemDetail detail =
                    ProblemDetail.of(
                            status,
                            "HTTP_" + status,
                            web.getResponse().getStatusInfo().getReasonPhrase(),
                            null,
                            correlationId,
                            null);
            return Response.status(status)
                    .type(jakarta.ws.rs.core.MediaType.valueOf(ProblemJsonType.MEDIA_TYPE))
                    .entity(detail)
                    .build();
        }
        // Unmapped failure: 500 with no internals leaked; the message never leaves the JVM.
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(jakarta.ws.rs.core.MediaType.valueOf(ProblemJsonType.MEDIA_TYPE))
                .entity(
                        ProblemDetail.of(
                                500,
                                "INTERNAL_ERROR",
                                "Unexpected server error",
                                null,
                                correlationId,
                                null))
                .build();
    }
}
