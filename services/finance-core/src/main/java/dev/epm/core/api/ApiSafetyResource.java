package dev.epm.core.api;

import dev.epm.core.audit.AuditService;
import dev.epm.core.outbox.OutboxService;
import dev.epm.core.persistence.RevisionService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * R0 API-safety demonstration surface: idempotent submission creation, revision ETags with If-Match
 * conditional updates, and problem+json failures. These are the platform contracts every later
 * domain endpoint inherits; the R0 actor is a header placeholder replaced by the OIDC identity in
 * Task 8.
 */
@ApplicationScoped
@Path("/api/v1/core/submissions")
public class ApiSafetyResource {

    private static final Pattern MONEY = Pattern.compile("^-?\\d+\\.\\d{2}$");
    private static final Pattern ISO_DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    @Inject RevisionService revisions;
    @Inject AuditService audit;
    @Inject OutboxService outbox;
    @Inject IdempotencyService idempotency;
    @Inject CorrelationRenderer correlation;

    /** Creates a submission exactly once per (key, actor, canonical payload). */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public Response create(
            String body,
            @HeaderParam("Idempotency-Key") String idempotencyKey,
            @HeaderParam("X-Actor") String actorHeader,
            @Context ContainerRequestContext request) {
        String actor = actor(actorHeader);
        String correlationId = correlation.correlationId(request);

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ProblemException(
                    400,
                    "MISSING_IDEMPOTENCY_KEY",
                    "Idempotency-Key required",
                    "Mutations must carry an Idempotency-Key header.",
                    null);
        }

        // Idempotent replay: same key + same canonical payload returns the original result.
        var replay = idempotency.lookup(idempotencyKey, actor, "CREATE_SUBMISSION", body);
        if (replay.isPresent()) {
            return Response.status(replay.get().status())
                    .header("Idempotency-Replayed", "true")
                    .entity(replay.get().body())
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .build();
        }

        SubmissionInput input = parseAndValidate(body);
        String submissionId = "sub-" + java.util.UUID.randomUUID();

        revisions.ensureObject("submission", submissionId);
        int revision =
                revisions.allocateRevision(
                        "submission",
                        submissionId,
                        actor,
                        "submission received",
                        correlationId,
                        LocalDate.parse(input.effectiveDate),
                        body);
        audit.append(
                "submission",
                submissionId,
                revision,
                actor,
                AuditService.AuditAction.CREATE,
                "submission received",
                correlationId,
                java.util.Map.of(),
                java.util.Map.of("effectiveDate", input.effectiveDate, "currency", input.currency),
                List.of());
        outbox.append("submission", submissionId, "SUBMISSION_RECEIVED", body, correlationId);

        String responseBody =
                "{\"submissionId\":\""
                        + submissionId
                        + "\",\"revision\":"
                        + revision
                        + ",\"status\":\"RECEIVED\",\"recordedAt\":\""
                        + revisions
                                .recordedAtUtc("submission", submissionId, revision)
                                .orElseThrow()
                        + "\",\"links\":{\"self\":\"/api/v1/core/submissions/"
                        + submissionId
                        + "\"}}";
        idempotency.store(
                idempotencyKey, actor, "CREATE_SUBMISSION", body, 201, responseBody, correlationId);
        return Response.status(201)
                .entity(responseBody)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .build();
    }

    /** Reads the current submission revision with a strong ETag. */
    @GET
    @Path("/{submissionId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response get(@PathParam("submissionId") String submissionId) {
        var latest = revisions.latestRevision("submission", submissionId);
        if (latest.isEmpty()) {
            throw new ProblemException(
                    404, "SUBMISSION_NOT_FOUND", "Submission not found", null, null);
        }
        int revision = latest.get();
        String etag = "\"revision-" + revision + "\"";
        String recordedAt =
                revisions
                        .recordedAtUtc("submission", submissionId, revision)
                        .map(java.time.OffsetDateTime::toString)
                        .orElse("");
        return Response.ok(
                        "{\"submissionId\":\""
                                + submissionId
                                + "\",\"revision\":"
                                + revision
                                + ",\"status\":\"RECEIVED\",\"recordedAt\":\""
                                + recordedAt
                                + "\",\"etag\":\""
                                + revision
                                + "\"}")
                .header("ETag", etag)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .build();
    }

    /** Conditional update: a stale If-Match never creates a second revision. */
    @PUT
    @Path("/{submissionId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public Response update(
            @PathParam("submissionId") String submissionId,
            String body,
            @HeaderParam("If-Match") String ifMatch,
            @HeaderParam("X-Actor") String actorHeader,
            @Context ContainerRequestContext request) {
        String actor = actor(actorHeader);
        String correlationId = correlation.correlationId(request);

        if (ifMatch == null || ifMatch.isBlank()) {
            throw new ProblemException(
                    400,
                    "MISSING_IF_MATCH",
                    "If-Match required",
                    "Updates must carry the current revision ETag.",
                    null);
        }
        var latest = revisions.latestRevision("submission", submissionId);
        if (latest.isEmpty()) {
            throw new ProblemException(
                    404, "SUBMISSION_NOT_FOUND", "Submission not found", null, null);
        }
        String currentEtag = "\"revision-" + latest.get() + "\"";
        if (!currentEtag.equals(ifMatch.trim())) {
            throw new ProblemException(
                    409,
                    "STALE_REVISION",
                    "Stale revision",
                    "The provided If-Match revision is no longer current.",
                    null);
        }

        SubmissionInput input = parseAndValidate(body);
        int revision =
                revisions.allocateRevision(
                        "submission",
                        submissionId,
                        actor,
                        "submission updated",
                        correlationId,
                        LocalDate.parse(input.effectiveDate),
                        body);
        audit.append(
                "submission",
                submissionId,
                revision,
                actor,
                AuditService.AuditAction.UPDATE,
                "submission updated",
                correlationId,
                java.util.Map.of("revision", String.valueOf(latest.get())),
                java.util.Map.of("revision", String.valueOf(revision)),
                List.of());
        outbox.append("submission", submissionId, "SUBMISSION_UPDATED", body, correlationId);

        String responseBody =
                "{\"submissionId\":\""
                        + submissionId
                        + "\",\"revision\":"
                        + revision
                        + ",\"status\":\"RECEIVED\"}";
        return Response.ok(responseBody)
                .header("ETag", "\"revision-" + revision + "\"")
                .type(MediaType.APPLICATION_JSON_TYPE)
                .build();
    }

    private static String actor(String actorHeader) {
        return actorHeader == null || actorHeader.isBlank() ? "anonymous" : actorHeader;
    }

    private static SubmissionInput parseAndValidate(String body) {
        List<ProblemDetail.Violation> violations = new ArrayList<>();
        com.fasterxml.jackson.databind.JsonNode node;
        try {
            node =
                    new com.fasterxml.jackson.databind.ObjectMapper()
                            .readTree(body == null || body.isBlank() ? "{}" : body);
        } catch (java.io.IOException e) {
            throw new ProblemException(422, "MALFORMED_JSON", "Malformed request body", null, null);
        }
        String effectiveDate = text(node, "effectiveDate");
        String currency = text(node, "currency");
        String openingCash = text(node, "openingCash");

        if (effectiveDate == null || !ISO_DATE.matcher(effectiveDate).matches()) {
            violations.add(
                    new ProblemDetail.Violation(
                            "effectiveDate", "PERIOD_INVALID", "Provide an ISO effective date."));
        }
        if (currency == null || !currency.matches("^[A-Z]{3}$")) {
            violations.add(
                    new ProblemDetail.Violation(
                            "currency", "CURRENCY_INVALID", "Provide a three-letter currency."));
        }
        if (openingCash == null || !MONEY.matcher(openingCash).matches()) {
            violations.add(
                    new ProblemDetail.Violation(
                            "openingCash",
                            "PRECISION_EXCEEDED",
                            "Use no more than two decimal places."));
        }
        if (!violations.isEmpty()) {
            throw new ProblemException(
                    422, "VALIDATION_FAILED", "Submission validation failed", null, violations);
        }
        return new SubmissionInput(effectiveDate, currency, openingCash);
    }

    private static String text(com.fasterxml.jackson.databind.JsonNode node, String field) {
        var value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private record SubmissionInput(String effectiveDate, String currency, String openingCash) {}
}
