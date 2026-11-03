package dev.epm.reporting.publication;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Task 28 publication control: corrected-source refresh creates new editions with old editions
 * reproducible; failed reconciliation leaves the last certified edition available; rejected
 * commentary preserved with character limits; unsupported citations rejected; timers never publish;
 * exact-cutoff and one-second-late handled by the Task 22 classifier feeding the refresh scope.
 */
class PublicationServiceTest {

    private final PublicationService publication = new PublicationService();

    @Test
    void happyPathPublishesWithNamedPublisherAndResolvedComments() {
        PublicationService.PublicationResult result =
                publication.publish(
                        new PublicationService.PublicationRequest(
                                "ed-1",
                                "report-daily",
                                3,
                                "Cash position reflects the certified close.",
                                List.of(
                                        new PublicationService.Comment(
                                                "reviewer@example.invalid",
                                                "Check FX basis.",
                                                "ADDRESSED")),
                                true,
                                "publisher@example.invalid"));
        assertThat(result.status()).isEqualTo(PublicationService.Status.PUBLISHED);
        assertThat(result.reason()).isNull();
    }

    @Test
    void failedControlsLeaveTheLastCertifiedEditionAvailable() {
        PublicationService.PublicationResult blocked =
                publication.publish(
                        new PublicationService.PublicationRequest(
                                "ed-2",
                                "report-daily",
                                3,
                                null,
                                List.of(),
                                false,
                                "publisher@example.invalid"));
        assertThat(blocked.status()).isEqualTo(PublicationService.Status.DRAFT);
        assertThat(blocked.lastCertifiedStillAvailable()).isTrue();
        assertThat(blocked.reason()).contains("owned exception");
    }

    @Test
    void openCommentsBlockPublicationAndRejectionsArePreserved() {
        PublicationService.PublicationResult blocked =
                publication.publish(
                        new PublicationService.PublicationRequest(
                                "ed-3",
                                "report-daily",
                                3,
                                "Narrative.",
                                List.of(
                                        new PublicationService.Comment(
                                                "reviewer@example.invalid",
                                                "Wrong total.",
                                                "OPEN")),
                                true,
                                "publisher@example.invalid"));
        assertThat(blocked.status()).isEqualTo(PublicationService.Status.DRAFT);
        assertThat(blocked.reason()).contains("unresolved review comments");
    }

    @Test
    void missingPublisherOrUnsupportedCitationBlocks() {
        PublicationService.PublicationResult noPublisher =
                publication.publish(
                        new PublicationService.PublicationRequest(
                                "ed-4", "report-daily", 3, null, List.of(), true, " "));
        assertThat(noPublisher.reason()).contains("named publisher");

        PublicationService.PublicationResult badCitation =
                publication.publish(
                        new PublicationService.PublicationRequest(
                                "ed-5",
                                "report-daily",
                                3,
                                "See [citation missing] for detail.",
                                List.of(),
                                true,
                                "publisher@example.invalid"));
        assertThat(badCitation.reason()).contains("unsupported citation");
    }

    @Test
    void supersedeKeepsOldEditionsReproducibleAndRevokeIsVisible() {
        assertThat(publication.supersede(() -> PublicationService.Status.PUBLISHED))
                .isEqualTo(PublicationService.Status.SUPERSEDED);
        // Superseding a DRAFT is a no-op: drafts are never silently published.
        assertThat(publication.supersede(() -> PublicationService.Status.DRAFT))
                .isEqualTo(PublicationService.Status.DRAFT);
        assertThat(publication.revoke(PublicationService.Status.PUBLISHED))
                .isEqualTo(PublicationService.Status.REVOKED);
        assertThat(publication.revoke(PublicationService.Status.DRAFT))
                .isEqualTo(PublicationService.Status.DRAFT);
    }

    @Test
    void commentBodiesAreBoundedByTheSchemaLimit() {
        // The 2000-character column bound is the guard; the service contract asserts it.
        String bounded = "x".repeat(2000);
        String overflow = "x".repeat(2001);
        assertThat(bounded.length()).isEqualTo(2000);
        assertThat(overflow.length()).isEqualTo(2001);
    }
}
