package dev.epm.core.certification;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * Rejection history writer: persists in its own transaction so a refused certification leaves
 * immutable evidence even though the caller's transaction rolls back.
 */
@ApplicationScoped
public class CertificationRejectionWriter {

    /** Records one rejection in a new transaction (fail-closed evidence). */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void record(
            String objectType, String stableId, int revision, String certifier, String reason) {
        CertificationRejectionEntity.record(objectType, stableId, revision, certifier, reason);
    }
}
