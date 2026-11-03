package dev.epm.core.certification;

import dev.epm.core.outbox.OutboxService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.UUID;

/**
 * Independent certification of one controlled revision (Task 20). Every precondition fails closed:
 * a passed validation run, a matched reconciliation, a named certifier distinct from the preparer,
 * and a non-blocking case are all required; case closure alone never substitutes for evidence. The
 * decision and its outbox event commit in one transaction; rejections are immutable history.
 */
@ApplicationScoped
public class CertificationService {

    /** Preconditions for certifying one revision. */
    public record Preconditions(
            boolean validationPassed,
            boolean reconciliationMatched,
            boolean preparerIsCertifier,
            boolean blockingCaseOpen,
            String preparer,
            UUID validationRunId,
            String reconciliationId) {

        static Preconditions of(
                boolean validationPassed,
                boolean reconciliationMatched,
                String preparer,
                String certifier,
                UUID validationRunId,
                String reconciliationId,
                boolean blockingCaseOpen) {
            return new Preconditions(
                    validationPassed,
                    reconciliationMatched,
                    preparer != null && preparer.equals(certifier),
                    blockingCaseOpen,
                    preparer,
                    validationRunId,
                    reconciliationId);
        }
    }

    private final OutboxService outbox;
    private final CertificationRejectionWriter rejectionWriter;

    @Inject
    CertificationService(OutboxService outbox, CertificationRejectionWriter rejectionWriter) {
        this.outbox = outbox;
        this.rejectionWriter = rejectionWriter;
    }

    /**
     * Certifies exactly one validated, reconciled revision. Throws for every unmet precondition
     * (fail closed); records the decision and outbox event atomically.
     */
    @Transactional
    public CertificationEntity certify(
            String objectType,
            String stableId,
            int revision,
            Preconditions preconditions,
            String certifier,
            String evidenceBundle,
            String correlationId) {
        if (!preconditions.validationPassed()) {
            reject(
                    objectType,
                    stableId,
                    revision,
                    certifier,
                    "validation did not pass for this revision");
        }
        if (preconditions.validationRunId() == null) {
            reject(
                    objectType,
                    stableId,
                    revision,
                    certifier,
                    "a passed validation run reference is required evidence");
        }
        if (!preconditions.reconciliationMatched()) {
            reject(
                    objectType,
                    stableId,
                    revision,
                    certifier,
                    "reconciliation is not matched for this revision");
        }
        if (preconditions.preparerIsCertifier()) {
            reject(
                    objectType,
                    stableId,
                    revision,
                    certifier,
                    "the preparer cannot certify their own revision");
        }
        if (preconditions.blockingCaseOpen()) {
            reject(
                    objectType,
                    stableId,
                    revision,
                    certifier,
                    "a blocking case is open for this object");
        }

        CertificationEntity certified =
                CertificationEntity.record(
                        objectType,
                        stableId,
                        revision,
                        preconditions.validationRunId(),
                        preconditions.reconciliationId(),
                        evidenceBundle,
                        certifier,
                        correlationId);
        outbox.append(
                objectType,
                stableId,
                "CERTIFIED",
                "{\"revision\":" + revision + ",\"evidenceBundle\":\"" + evidenceBundle + "\"}",
                correlationId);
        return certified;
    }

    private void reject(
            String objectType, String stableId, int revision, String certifier, String reason) {
        // New transaction: rejection evidence survives the caller's rollback.
        rejectionWriter.record(objectType, stableId, revision, certifier, reason);
        throw new CertificationException(reason);
    }

    /** Certification gate failures (fail closed). */
    public static final class CertificationException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public CertificationException(String message) {
            super(message);
        }
    }
}
