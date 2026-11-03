package dev.epm.core.certification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Task 20 certification control: exact/over-tolerance and exact-boundary reconciliation;
 * certification fails closed on every missing precondition (missing evidence, unexplained
 * difference, stale contract → blocking case, preparer-certifier conflict); one certifier decision
 * per revision; rejection history preserved.
 */
@QuarkusTest
class CertificationControlTest {

    @Inject ReconciliationService reconciliation;
    @Inject CertificationService certification;

    private static final String TOLERANCE = "0.01";

    @Test
    void reconciliationExactBoundaryIsInclusive() {
        // Golden fixture TREC1: difference exactly equals tolerance → matches.
        ReconciliationService.Statement exact =
                reconciliation.reconcile(
                        "recon-1",
                        UUID.randomUUID(),
                        "recon-1.0.0",
                        new BigDecimal("1000.00"),
                        new BigDecimal("1000.01"),
                        new BigDecimal("1000.01"),
                        new BigDecimal(TOLERANCE));
        assertThat(exact.status()).isEqualTo("MATCHED_AT_EXACT_TOLERANCE");

        ReconciliationService.Statement over =
                reconciliation.reconcile(
                        "recon-2",
                        UUID.randomUUID(),
                        "recon-1.0.0",
                        new BigDecimal("1000.00"),
                        new BigDecimal("1000.02"),
                        new BigDecimal("1000.02"),
                        new BigDecimal(TOLERANCE));
        assertThat(over.status()).isEqualTo("BLOCKED_OVER_TOLERANCE");
        assertThat(over.explanation()).contains("exceeds the tolerance");

        ReconciliationService.Statement zero =
                reconciliation.reconcile(
                        "recon-3",
                        UUID.randomUUID(),
                        "recon-1.0.0",
                        new BigDecimal("1000.00"),
                        new BigDecimal("1000.00"),
                        new BigDecimal("1000.00"),
                        BigDecimal.ZERO);
        assertThat(zero.status()).isEqualTo("MATCHED_EXACT");
    }

    @Test
    void happyPathCertifiesOneRevisionWithEvidenceBundle() {
        String stableId = "cash-" + UUID.randomUUID();
        CertificationEntity certified =
                certification.certify(
                        "cash_position",
                        stableId,
                        1,
                        CertificationService.Preconditions.of(
                                true,
                                true,
                                "preparer@example.invalid",
                                "certifier@example.invalid",
                                UUID.randomUUID(),
                                "recon-ok",
                                false),
                        "certifier@example.invalid",
                        "bundle-" + stableId,
                        "corr-1");
        assertThat(certified.getCertifier()).isEqualTo("certifier@example.invalid");
        assertThat(certified.getRevision()).isEqualTo(1);
    }

    @Test
    void missingValidationEvidenceFailsClosed() {
        String stableId = "obj-" + UUID.randomUUID();
        assertThatThrownBy(
                        () ->
                                certification.certify(
                                        "cash_position",
                                        stableId,
                                        1,
                                        CertificationService.Preconditions.of(
                                                false, true, "p@e", "c@e", null, "recon-ok", false),
                                        "certifier@example.invalid",
                                        "bundle",
                                        "corr"))
                .hasMessageContaining("validation did not pass");
        // A later null-evidence attempt also fails even if the flag says passed.
        assertThatThrownBy(
                        () ->
                                certification.certify(
                                        "cash_position",
                                        stableId,
                                        1,
                                        new CertificationService.Preconditions(
                                                true, true, false, false, "p@e", null, "recon-ok"),
                                        "certifier@example.invalid",
                                        "bundle",
                                        "corr"))
                .hasMessageContaining("validation run reference is required");
        assertThat(CertificationRejectionEntity.countFor("cash_position", stableId)).isEqualTo(2);
    }

    @Test
    void unexplainedDifferenceBlocksCertification() {
        String stableId = "obj-" + UUID.randomUUID();
        assertThatThrownBy(
                        () ->
                                certification.certify(
                                        "cash_position",
                                        stableId,
                                        1,
                                        CertificationService.Preconditions.of(
                                                true,
                                                false,
                                                "p@e",
                                                "c@e",
                                                UUID.randomUUID(),
                                                "recon-bad",
                                                false),
                                        "certifier@example.invalid",
                                        "bundle",
                                        "corr"))
                .hasMessageContaining("reconciliation is not matched");
    }

    @Test
    void blockingCaseBlocksAndPreparerCannotCertify() {
        String stableId = "obj-" + UUID.randomUUID();
        assertThatThrownBy(
                        () ->
                                certification.certify(
                                        "cash_position",
                                        stableId,
                                        1,
                                        CertificationService.Preconditions.of(
                                                true,
                                                true,
                                                "p@e",
                                                "c@e",
                                                UUID.randomUUID(),
                                                "recon-ok",
                                                true),
                                        "certifier@example.invalid",
                                        "bundle",
                                        "corr"))
                .hasMessageContaining("blocking case");

        String preparer = "preparer@example.invalid";
        assertThatThrownBy(
                        () ->
                                certification.certify(
                                        "cash_position",
                                        stableId,
                                        1,
                                        CertificationService.Preconditions.of(
                                                true,
                                                true,
                                                preparer,
                                                preparer,
                                                UUID.randomUUID(),
                                                "recon-ok",
                                                false),
                                        preparer,
                                        "bundle",
                                        "corr"))
                .hasMessageContaining("preparer cannot certify");
    }

    @Test
    void exactlyOneCertifierDecisionPerRevision() {
        String stableId = "once-" + UUID.randomUUID();
        certification.certify(
                "cash_position",
                stableId,
                1,
                CertificationService.Preconditions.of(
                        true, true, "p@e", "c1@e", UUID.randomUUID(), "recon-ok", false),
                "certifier1@example.invalid",
                "bundle",
                "corr");
        // A second certification attempt for the same revision is rejected by the
        // one-decision-per-revision uniqueness.
        assertThatThrownBy(
                        () ->
                                certification.certify(
                                        "cash_position",
                                        stableId,
                                        1,
                                        CertificationService.Preconditions.of(
                                                true,
                                                true,
                                                "p@e",
                                                "c2@e",
                                                UUID.randomUUID(),
                                                "recon-ok",
                                                false),
                                        "certifier2@example.invalid",
                                        "bundle",
                                        "corr"))
                .hasRootCauseInstanceOf(org.postgresql.util.PSQLException.class)
                .hasMessageContaining("uq_certified_revision");
        CertificationEntity certified =
                CertificationEntity.findCertified("cash_position", stableId);
        assertThat(certified.getCertifier()).isEqualTo("certifier1@example.invalid");
    }
}
