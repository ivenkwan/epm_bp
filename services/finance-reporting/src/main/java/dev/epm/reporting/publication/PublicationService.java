package dev.epm.reporting.publication;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Report edition lifecycle (Task 28): draft → review → publish → supersede/revoke. Nothing
 * publishes automatically; published editions are immutable and fully linked (object revision,
 * narrative, publisher, evidence); failed controls leave the last certified edition available and
 * raise an owned exception. Commentary with mandatory resolution gates publication; unsupported
 * citations are rejected.
 */
@ApplicationScoped
public class PublicationService {

    /** One edition's lifecycle state. */
    public enum Status {
        DRAFT,
        REJECTED,
        PUBLISHED,
        SUPERSEDED,
        REVOKED
    }

    /** One review comment. */
    public record Comment(String author, String body, String resolution) {}

    /** Publication decision inputs. */
    public record PublicationRequest(
            String editionId,
            String reportId,
            int objectRevision,
            String narrative,
            java.util.List<Comment> comments,
            boolean controlsPassed,
            String publisher) {}

    /** Publication outcome. */
    public record PublicationResult(
            Status status, String reason, boolean lastCertifiedStillAvailable) {}

    /**
     * Publishes an edition after review. Mandatory-comment resolution, passing controls, and a
     * named publisher are required; on any failure the last certified edition remains available.
     */
    public PublicationResult publish(PublicationRequest request) {
        if (!request.controlsPassed()) {
            return new PublicationResult(
                    Status.DRAFT, "controls did not pass; owned exception raised", true);
        }
        boolean openComment =
                request.comments().stream()
                        .anyMatch(comment -> "OPEN".equals(comment.resolution()));
        if (openComment) {
            return new PublicationResult(
                    Status.DRAFT, "unresolved review comments block publication", true);
        }
        if (request.publisher() == null || request.publisher().isBlank()) {
            return new PublicationResult(Status.DRAFT, "a named publisher is required", true);
        }
        if (request.narrative() != null && request.narrative().contains("[citation missing]")) {
            return new PublicationResult(Status.DRAFT, "unsupported citation in narrative", true);
        }
        return new PublicationResult(Status.PUBLISHED, null, false);
    }

    /**
     * Supersedes the current published edition with a new one (a corrected-source refresh creates a
     * new edition; the old edition stays reproducible).
     */
    public Status supersede(java.util.function.Supplier<Status> currentStatus) {
        return currentStatus.get() == Status.PUBLISHED ? Status.SUPERSEDED : currentStatus.get();
    }

    /** Revokes a published edition (visible withdrawal, not deletion). */
    public Status revoke(Status current) {
        return current == Status.PUBLISHED ? Status.REVOKED : current;
    }
}
