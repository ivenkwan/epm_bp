package dev.epm.reporting.semantic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * Task 12 KPI catalog: independently expected results (from db/fixtures/expected): two evaluations
 * of the same card return the identical certified value and version; undefined measure, missing
 * owner, zero denominator (never zero), null numerator (never coerced), and permitted drill grain.
 */
@QuarkusTest
class KpiEvaluationTest {

    @Inject KpiEvaluator evaluator;
    @Inject KpiCatalogService catalog;

    @Test
    void sameCardReturnsIdenticalValueAndVersionAcrossEvaluations() {
        // Golden: 850000.00 / 1700000.00 = 0.500000 (expected-covenants.csv, scale 6).
        KpiCardEntity card = gearingCard("kpi-" + System.nanoTime());
        KpiEvaluator.EvaluationResult first =
                evaluator.evaluate(
                        card, "{\"numerator\":\"850000.00\",\"denominator\":\"1700000.00\"}");
        KpiEvaluator.EvaluationResult second =
                evaluator.evaluate(
                        card, "{\"numerator\":\"850000.00\",\"denominator\":\"1700000.00\"}");
        assertThat(first.value()).isEqualTo("0.500000");
        assertThat(second.value()).isEqualTo(first.value());
        assertThat(second.version()).isEqualTo(first.version());
    }

    @Test
    void zeroDenominatorIsGovernedUnavailableNeverZero() {
        KpiCardEntity card = gearingCard("kpi-" + System.nanoTime());
        KpiEvaluator.EvaluationResult result =
                evaluator.evaluate(card, "{\"numerator\":\"300000.00\",\"denominator\":\"0.00\"}");
        assertThat(result.value()).isNull();
        assertThat(result.status()).isEqualTo("UNAVAILABLE_ZERO_DENOMINATOR");
    }

    @Test
    void nullNumeratorNeverCoercesToZero() {
        KpiCardEntity card = gearingCard("kpi-" + System.nanoTime());
        KpiEvaluator.EvaluationResult result =
                evaluator.evaluate(card, "{\"numerator\":null,\"denominator\":\"100.00\"}");
        assertThat(result.value()).isNull();
        assertThat(result.status()).isEqualTo("UNAVAILABLE_NULL_NUMERATOR");
    }

    @Test
    void undefinedMeasureIsRejectedNotGuessed() {
        KpiCardEntity card = gearingCard("kpi-" + System.nanoTime());
        KpiEvaluator.EvaluationResult result = evaluator.evaluate(card, "{\"something\":\"else\"}");
        assertThat(result.value()).isNull();
        assertThat(result.status()).isEqualTo("UNDEFINED_MEASURE");
    }

    @Test
    void missingOwnerOrWorkedExampleCannotBeProposed() {
        KpiCardEntity noOwner = baseGearingCard("kpi-" + System.nanoTime());
        noOwner.setOwner(null);
        assertThatThrownBy(() -> catalog.propose(noOwner))
                .isInstanceOf(KpiCardEntity.SemanticException.class)
                .hasMessageContaining("owner");

        KpiCardEntity noExample = baseGearingCard("kpi-" + System.nanoTime());
        noExample.setWorkedExample("{}");
        assertThatThrownBy(() -> catalog.propose(noExample)).hasMessageContaining("worked example");
    }

    @Test
    void certificationRequiresTestingIndependentCertifierAndGreenExample() {
        KpiCardEntity card = baseGearingCard("kpi-" + System.nanoTime());
        catalog.propose(card);
        assertThatThrownBy(() -> catalog.certify(card, "certifier", evaluator))
                .hasMessageContaining("TESTED");

        catalog.markTested(card, "worked example green");
        assertThatThrownBy(() -> catalog.certify(card, card.getOwner(), evaluator))
                .hasMessageContaining("owner cannot certify");

        catalog.certify(card, "independent.certifier@example.invalid", evaluator);
        assertThat(card.getStatus()).isEqualTo("CERTIFIED");
        assertThat(card.getCertifiedBy()).isEqualTo("independent.certifier@example.invalid");
    }

    @Test
    void uncertifiedInvalidDefinitionCannotBeCertified() {
        KpiCardEntity broken = baseGearingCard("kpi-" + System.nanoTime());
        broken.setWorkedExample("{\"numerator\":\"1.00\",\"denominator\":\"0.00\"}");
        catalog.propose(broken);
        catalog.markTested(broken, "example intentionally unavailable");
        assertThatThrownBy(() -> catalog.certify(broken, "certifier", evaluator))
                .hasMessageContaining("worked example must evaluate OK");
        assertThat(broken.getStatus()).isEqualTo("TESTED");
    }

    private static KpiCardEntity gearingCard(String id) {
        KpiCardEntity card = baseGearingCard(id);
        card.setVersion(1);
        card.setStatus("CERTIFIED");
        return card;
    }

    private static KpiCardEntity baseGearingCard(String id) {
        KpiCardEntity card = new KpiCardEntity();
        card.setKpiId(id);
        card.setName("Gearing");
        card.setFormula("gearing");
        card.setUnit("RATIO");
        card.setGrain("ENTITY_DAY");
        card.setDimensions("{\"entity\":[\"ENT101\"]}");
        card.setOwner("treasury.owner@example.invalid");
        card.setCadence("DAILY");
        card.setSensitivity("INTERNAL");
        card.setSourceMappings("{\"numerator\":\"NET_DEBT\",\"denominator\":\"EQUITY\"}");
        card.setThresholds("{\"max\":\"0.600000\"}");
        card.setWorkedExample("{\"numerator\":\"850000.00\",\"denominator\":\"1700000.00\"}");
        card.setDrillPolicy("ENTITY");
        return card;
    }
}
