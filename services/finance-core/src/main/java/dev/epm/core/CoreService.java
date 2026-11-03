package dev.epm.core;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.time.Instant;

/** Minimal service root for the R0 toolchain; real capabilities land with their owning tasks. */
@ApplicationScoped
@Path("/api/v1/core")
public class CoreService {

    /** Liveness signal carrying the service name and UTC timestamp of the response. */
    @GET
    @Path("/health")
    @Produces(MediaType.APPLICATION_JSON)
    public ServiceStatus health() {
        return new ServiceStatus("finance-core", "R0_TOOLCHAIN", Instant.now().toString());
    }

    /** Response payload for the service health signal. */
    public record ServiceStatus(String service, String release, String respondedAtUtc) {}
}
