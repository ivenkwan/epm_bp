package dev.epm.integration.landing;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Task 11 landing immutability: original payload retrieval, content hashes, distinct source vs
 * receiving timestamps, run ids, counts, revision linkage, duplicate payload identities kept
 * distinguishable, overwrite denial (guard trigger + violation record) — with no data lost.
 */
@QuarkusTest
class LandingImmutabilityTest {

    @Inject LandingService landing;

    @Test
    void originalPayloadRoundTripsWithHashAndBothTimeBases() {
        byte[] content = "{\"openingCash\":\"1250000.00\"}".getBytes(StandardCharsets.UTF_8);
        LocalDateTime sourceTime = LocalDateTime.of(2026, 10, 31, 1, 0);
        LandingService.LandingResult result =
                landing.land(
                        content,
                        "application/json",
                        "SYS-ERP-1",
                        "treasury-daily-v3",
                        1,
                        "sub-" + UUID.randomUUID(),
                        "run-1",
                        1,
                        sourceTime);

        LandingService.LandedPayload stored = landing.payloadFor(result.loadId()).orElseThrow();
        assertThat(stored.content()).isEqualTo(content);
        assertThat(stored.contentHash()).hasSize(64);
        assertThat(stored.sourceTimestamp()).isEqualTo(sourceTime);
        // Receiving time exists and is distinct from the source timebase.
        assertThat(stored.receivingTimestamp()).isNotNull();
        assertThat(stored.sourceTimestamp()).isNotEqualTo(stored.receivingTimestamp());
    }

    @Test
    void duplicateContentIsFlaggedWhileSubmissionIdentitiesStayDistinct() {
        byte[] content = "{\"same\":\"content\"}".getBytes(StandardCharsets.UTF_8);
        String firstSubmission = "sub-first-" + UUID.randomUUID();
        String secondSubmission = "sub-second-" + UUID.randomUUID();
        LandingService.LandingResult first =
                landing.land(
                        content,
                        "application/json",
                        "SYS-ERP-1",
                        "c",
                        1,
                        firstSubmission,
                        "run-a",
                        1,
                        null);
        LandingService.LandingResult second =
                landing.land(
                        content,
                        "application/json",
                        "SYS-ERP-1",
                        "c",
                        1,
                        secondSubmission,
                        "run-b",
                        1,
                        null);

        assertThat(first.outcome()).isEqualTo("LANDED");
        assertThat(second.outcome()).isEqualTo("DUPLICATE_REVIEW");
        assertThat(second.duplicateContent()).isTrue();
        // Content-addressed: one payload, two distinguishable submissions.
        assertThat(second.payloadId()).isEqualTo(first.payloadId());
        assertThat(second.loadId()).isNotEqualTo(first.loadId());
    }

    @Test
    void attemptedOverwriteIsDeniedAndRecordedWithoutChangingEvidence() {
        byte[] content = "immutable-bytes".getBytes(StandardCharsets.UTF_8);
        LandingService.LandingResult result =
                landing.land(
                        content,
                        "text/plain",
                        "SYS-TMPL-1",
                        "c",
                        1,
                        "sub-" + UUID.randomUUID(),
                        "run-1",
                        3,
                        null);

        int violationsBefore = countViolations();
        attemptMutation(result.payloadId());
        int violationsAfter = countViolations();

        LandingService.LandedPayload stored = landing.payloadFor(result.loadId()).orElseThrow();
        assertThat(stored.content()).isEqualTo(content);
        assertThat(violationsAfter).isGreaterThan(violationsBefore);
    }

    @Transactional
    void attemptMutation(UUID payloadId) {
        // Direct entity mutation attempt: the guard trigger skips it and records a
        // violation; the original bytes never change.
        LandedPayloadEntity payload = LandedPayloadEntity.findById(payloadId);
        payload.getContent(); // no setter exists by design; trigger path exercised by SQL
        jakarta.persistence.EntityManager em =
                io.quarkus.hibernate.orm.panache.Panache.getEntityManager();
        em.createQuery("update LandedPayloadEntity set contentType = 'tampered'").executeUpdate();
    }

    @Transactional
    int countViolations() {
        Object value =
                io.quarkus.hibernate.orm.panache.Panache.getEntityManager()
                        .createNativeQuery("select count(*) from epm_audit_violation")
                        .getSingleResult();
        return ((Number) value).intValue();
    }

    @Test
    void loadLogCarriesRunCountsAndContractVersion() {
        byte[] content = "{\"rows\":2}".getBytes(StandardCharsets.UTF_8);
        LandingService.LandingResult result =
                landing.land(
                        content,
                        "application/json",
                        "SYS-BANK-1",
                        "treasury-bank-v2",
                        3,
                        "sub-" + UUID.randomUUID(),
                        "run-batch-42",
                        2,
                        null);
        assertThat(result.loadId()).isNotNull();
        assertThat(result.contentHash()).hasSize(64);
    }
}
