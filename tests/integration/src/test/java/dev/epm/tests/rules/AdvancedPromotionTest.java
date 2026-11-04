package dev.epm.tests.rules;

import static org.assertj.core.api.Assertions.assertThat;

import dev.epm.admin.impact.ImpactService;
import dev.epm.admin.ruleadmin.RuleAdministrationService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Task 55 advanced promotion acceptance: isolated simulation with independent expected results;
 * fail-closed promotion on failed simulations, stale AGE impact, unauthorized/self approval,
 * missing provenance, and quarantine findings (suspected secrets, missing documentation delta);
 * rejected promotions leave active versions unchanged; rollback references preserved.
 */
class AdvancedPromotionTest {

    private final RuleAdministrationService admin = new RuleAdministrationService();
    private final ImpactService impact = new ImpactService();

    private static final Instant NOW = Instant.parse("2026-11-04T12:00:00Z");
    private static final Duration BOUND = Duration.ofMinutes(30);

    private static ImpactService.GovernedChange change(String newDefinition, String docRef) {
        return new ImpactService.GovernedChange(
                "chg-1",
                ImpactService.GovernedChange.Kind.DRIVER,
                "old",
                newDefinition,
                "hash",
                docRef);
    }

    private static ImpactService.ImpactList freshImpact() {
        return new ImpactService.ImpactList(
                List.of(new ImpactService.Impacted("KPI", "kpi-cash", "formula references driver")),
                NOW.minus(Duration.ofMinutes(5)));
    }

    private static RuleAdministrationService.PromotionRequest request(
            RuleAdministrationService.Simulation simulation,
            ImpactService.ImpactList impactList,
            String requestedBy,
            String approvedBy,
            String docRef) {
        return new RuleAdministrationService.PromotionRequest(
                change("revenue = base × volume", docRef),
                simulation,
                impactList,
                requestedBy,
                approvedBy,
                "TICKET-1",
                "tests/integration/rules/AdvancedPromotionTest.java",
                "bom/sbom-1.xml");
    }

    @Test
    void passingSimulationPromotesWithRollbackReference() {
        RuleAdministrationService.Simulation passing =
                admin.simulate(
                        "revenue = base × volume", List.of("1100.00"), d -> List.of("1100.00"));
        RuleAdministrationService.Promotion promotion =
                admin.promote(
                        request(
                                passing,
                                freshImpact(),
                                "author@epm",
                                "approver@epm",
                                "docs/architecture/driver-scenario-model.md"),
                        new RuleAdministrationService.ActiveVersion("DRIVER", "v1", "old"),
                        impact,
                        NOW,
                        BOUND);
        assertThat(promotion.promoted()).isTrue();
        assertThat(promotion.rollbackRef()).isEqualTo("rollback-to:v1");
        assertThat(promotion.previousActive().version()).isEqualTo("v1");
    }

    @Test
    void failedSimulationAndStaleImpactBlockPromotion() {
        RuleAdministrationService.Simulation failing =
                admin.simulate("broken", List.of("1100.00"), d -> List.of("9999.00"));
        assertThat(
                        admin.promote(
                                        request(failing, freshImpact(), "a@epm", "b@epm", "doc"),
                                        new RuleAdministrationService.ActiveVersion(
                                                "DRIVER", "v1", "old"),
                                        impact,
                                        NOW,
                                        BOUND)
                                .note())
                .contains("independent expected results");

        ImpactService.ImpactList stale =
                new ImpactService.ImpactList(List.of(), NOW.minus(Duration.ofHours(2)));
        RuleAdministrationService.Simulation passing =
                admin.simulate("ok", List.of("1"), d -> List.of("1"));
        assertThat(
                        admin.promote(
                                        request(passing, stale, "a@epm", "b@epm", "doc"),
                                        new RuleAdministrationService.ActiveVersion(
                                                "DRIVER", "v1", "old"),
                                        impact,
                                        NOW,
                                        BOUND)
                                .note())
                .contains("stale AGE impact");
    }

    @Test
    void selfApprovalAndMissingProvenanceAreRefused() {
        RuleAdministrationService.Simulation passing =
                admin.simulate("ok", List.of("1"), d -> List.of("1"));
        assertThat(
                        admin.promote(
                                        request(
                                                passing,
                                                freshImpact(),
                                                "same@epm",
                                                "same@epm",
                                                "doc"),
                                        new RuleAdministrationService.ActiveVersion(
                                                "DRIVER", "v1", "old"),
                                        impact,
                                        NOW,
                                        BOUND)
                                .note())
                .contains("self-approval");
        RuleAdministrationService.PromotionRequest incomplete =
                new RuleAdministrationService.PromotionRequest(
                        change("def", "doc"),
                        passing,
                        freshImpact(),
                        "a@epm",
                        "b@epm",
                        " ",
                        "evidence",
                        "sbom");
        assertThat(
                        admin.promote(
                                        incomplete,
                                        new RuleAdministrationService.ActiveVersion(
                                                "DRIVER", "v1", "old"),
                                        impact,
                                        NOW,
                                        BOUND)
                                .note())
                .contains("provenance incomplete");
    }

    @Test
    void suspectedSecretsAndMissingDocDeltaQuarantine() {
        assertThat(impact.quarantineScan(change("password=supersecret123", "doc")).reason())
                .contains("suspected secret");
        assertThat(impact.quarantineScan(change("revenue = base × volume", " ")).reason())
                .contains("living-documentation check failed");
        assertThat(impact.quarantineScan(change("revenue = base × volume", "doc.md")).quarantined())
                .isFalse();
    }

    @Test
    void rejectedPromotionsLeaveActiveVersionsUnchanged() {
        RuleAdministrationService.ActiveVersion active =
                new RuleAdministrationService.ActiveVersion("DRIVER", "v1", "old");
        RuleAdministrationService.Simulation failing =
                admin.simulate("broken", List.of("1"), d -> List.of("2"));
        RuleAdministrationService.Promotion rejected =
                admin.promote(
                        request(failing, freshImpact(), "a@epm", "b@epm", "doc"),
                        active,
                        impact,
                        NOW,
                        BOUND);
        assertThat(rejected.promoted()).isFalse();
        assertThat(rejected.newActive()).isNull();
        assertThat(active.version()).isEqualTo("v1");
        assertThat(active.definition()).isEqualTo("old");
    }
}
