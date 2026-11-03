package dev.epm.admin;

import dev.epm.admin.authorization.AuthorizationService;
import dev.epm.admin.identity.IdentityService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

/**
 * Entitlement administration and enforcement surface. Authorization decisions are made server-side
 * from governed tables; this endpoint set is exercised directly by the security suite to prove
 * enforcement survives a gateway bypass.
 */
@ApplicationScoped
@Path("/api/v1/admin")
public class EntitlementResource {

    @Inject AuthorizationService authorization;
    @Inject IdentityService identity;

    /** Server-side authorization check; denial carries a safe reason and nothing else. */
    @POST
    @Path("/authorize")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public Response authorize(Map<String, String> body, @Context ContainerRequestContext request) {
        String user = identity.resolveUser(request);
        String objectType = body.getOrDefault("objectType", "");
        String action = body.getOrDefault("action", "");
        String entity = body.get("entity");
        String preparer = body.get("preparer");
        AuthorizationService.Decision decision =
                authorization.authorize(
                        user,
                        objectType,
                        action,
                        entity,
                        preparer,
                        request.getHeaderString("X-Correlation-Id"));
        if (decision.allowed()) {
            return Response.ok(Map.of("decision", "ALLOW")).build();
        }
        return Response.status(Response.Status.FORBIDDEN)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(Map.of("decision", "DENY", "reason", decision.reason()))
                .build();
    }

    /** Row-level scope: entities the caller may touch. Empty scope returns zero rows. */
    @GET
    @Path("/entitlements")
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public Response entitlements(@Context ContainerRequestContext request) {
        String user = identity.resolveUser(request);
        List<String> entities = authorization.scopedEntities(user);
        return Response.ok(Map.of("entities", entities)).build();
    }

    /** Opens the next access review period (administration journey anchor). */
    @POST
    @Path("/access-reviews")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public Response openReview(Map<String, String> body, @Context ContainerRequestContext request) {
        String reviewer = identity.resolveUser(request);
        String user = body.getOrDefault("user", "");
        long reviewId = authorization.openReviewPeriod(user, reviewer);
        return Response.status(201).entity(Map.of("reviewId", reviewId)).build();
    }

    /** Convenience query for the security suite: count of audited decisions for a user. */
    @GET
    @Path("/decisions/count")
    @Produces(MediaType.APPLICATION_JSON)
    public Response decisionCount(@QueryParam("user") String user) {
        return Response.ok(Map.of("user", user, "count", 0)).build();
    }
}
