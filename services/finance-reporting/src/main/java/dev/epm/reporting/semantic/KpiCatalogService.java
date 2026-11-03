package dev.epm.reporting.semantic;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * KPI lifecycle: propose → test → certify → deprecate. Certification requires an owner, a worked
 * example, and a green test evaluation; uncertified or invalid definitions cannot be certified.
 */
@ApplicationScoped
public class KpiCatalogService {

    /** Registers a KPI card in DRAFT; owner and worked example are mandatory. */
    @Transactional
    public KpiCardEntity propose(KpiCardEntity card) {
        if (card.getOwner() == null || card.getOwner().isBlank()) {
            throw new KpiCardEntity.SemanticException("KPI card requires a named owner");
        }
        if (card.workedExampleNode().isEmpty()) {
            throw new KpiCardEntity.SemanticException("KPI card requires a worked example");
        }
        if (card.getVersion() == null) {
            card.setVersion(1);
        }
        card.setStatus("DRAFT");
        card.persist();
        return card;
    }

    /** Marks a card TESTED once its worked example evaluates OK. */
    @Transactional
    public void markTested(KpiCardEntity card, String reason) {
        if (!"DRAFT".equals(card.getStatus()) && !"TESTED".equals(card.getStatus())) {
            throw new KpiCardEntity.SemanticException("only DRAFT cards can be marked TESTED");
        }
        card.setStatus("TESTED");
    }

    /**
     * Certifies a card. The certifier must differ from the owner (four-eyes), the card must be
     * TESTED, and the worked example must evaluate OK under the same version.
     */
    @Transactional
    public void certify(KpiCardEntity card, String certifier, KpiEvaluator evaluator) {
        if (!"TESTED".equals(card.getStatus())) {
            throw new KpiCardEntity.SemanticException("only TESTED cards can be certified");
        }
        if (card.getOwner().equals(certifier)) {
            throw new KpiCardEntity.SemanticException("owner cannot certify their own KPI");
        }
        KpiEvaluator.EvaluationResult example =
                evaluator.evaluate(card, card.workedExampleNode().toString());
        if (!"OK".equals(example.status())) {
            throw new KpiCardEntity.SemanticException(
                    "worked example must evaluate OK before certification");
        }
        card.setStatus("CERTIFIED");
        card.setCertifiedBy(certifier);
        card.setCertifiedAtUtc(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC));
    }

    /** Deprecates a certified card; certified results already published stay reproducible. */
    @Transactional
    public void deprecate(KpiCardEntity card) {
        card.setStatus("DEPRECATED");
    }
}
