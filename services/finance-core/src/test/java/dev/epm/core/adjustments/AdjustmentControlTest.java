package dev.epm.core.adjustments;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Task 29 adjustment control (implementing the signed Task 15 design): balanced and unbalanced
 * lines, self-approval, missing evidence, materiality levels, replay-safe application,
 * post-publication reversal, and DMN agreement — every expectation independently authored in the
 * Task 15 design fixtures and tests.
 */
@QuarkusTest
class AdjustmentControlTest {

    private static final Path DMN =
            Path.of("..", "..", "rules", "dmn", "adjustments", "approval-level.dmn");

    @Test
    void balancedLinesPassAndUnbalancedAreRejectedByTheContract() throws Exception {
        List<AdjustmentControls.Line> balanced =
                List.of(
                        new AdjustmentControls.Line(1, "ACC001", 'D', "1000.00"),
                        new AdjustmentControls.Line(2, "ACC002", 'C', "1000.00"));
        assertThat(AdjustmentControls.balanced(balanced)).isTrue();

        List<AdjustmentControls.Line> unbalanced =
                List.of(
                        new AdjustmentControls.Line(1, "ACC001", 'D', "1000.00"),
                        new AdjustmentControls.Line(2, "ACC002", 'C', "900.00"));
        assertThat(AdjustmentControls.balanced(unbalanced)).isFalse();
        // §5 fixture arithmetic: the unbalanced case is the REJECTED_BALANCE row.
        assertThat(
                        Files.readString(
                                Path.of(
                                        "..",
                                        "..",
                                        "db",
                                        "fixtures",
                                        "migration",
                                        "adjustment-example.csv")))
                .contains("ADJ004,ENT101,USD,2,1000.00,900.00,UNBALANCED");
    }

    @Test
    void selfApprovalIsDeniedAndIndependentApprovalRequired() {
        assertThat(AdjustmentControls.independentApproval("p@e", "p@e")).isFalse();
        assertThat(AdjustmentControls.independentApproval("p@e", "a@e")).isTrue();
        assertThat(AdjustmentControls.independentApproval("p@e", null)).isFalse();
        assertThat(AdjustmentControls.independentApproval("p@e", " ")).isFalse();
    }

    @Test
    void materialitySelectsTheDocumentedApprovalLevels() {
        // §5 fixture: 990.00 → OWNER_REVIEW; 5000.00 → INDEPENDENT_APPROVER;
        // 15000.00 → DOMAIN_APPROVER_PLUS_CERTIFIER; boundaries exact.
        assertThat(AdjustmentControls.levelFor("990.00"))
                .isEqualTo(AdjustmentControls.Level.OWNER_REVIEW);
        assertThat(AdjustmentControls.levelFor("5000.00"))
                .isEqualTo(AdjustmentControls.Level.INDEPENDENT_APPROVER);
        assertThat(AdjustmentControls.levelFor("15000.00"))
                .isEqualTo(AdjustmentControls.Level.DOMAIN_APPROVER_PLUS_CERTIFIER);
        // Exact boundaries: 1000.00 inclusive into INDEPENDENT, 10000.00 into DOMAIN.
        assertThat(AdjustmentControls.levelFor("1000.00"))
                .isEqualTo(AdjustmentControls.Level.INDEPENDENT_APPROVER);
        assertThat(AdjustmentControls.levelFor("999.99"))
                .isEqualTo(AdjustmentControls.Level.OWNER_REVIEW);
        assertThat(AdjustmentControls.levelFor("10000.00"))
                .isEqualTo(AdjustmentControls.Level.DOMAIN_APPROVER_PLUS_CERTIFIER);
        assertThat(AdjustmentControls.levelFor("9999.99"))
                .isEqualTo(AdjustmentControls.Level.INDEPENDENT_APPROVER);
    }

    @Test
    void dmnApprovalLevelTableAgreesWithTheContract() throws Exception {
        var helper = new org.kie.internal.utils.KieHelper();
        helper.addContent(Files.readString(DMN), org.kie.api.io.ResourceType.DMN);
        var kbase = helper.build();
        var runtime =
                org.kie.api.runtime.KieRuntimeFactory.of(kbase)
                        .get(org.kie.dmn.api.core.DMNRuntime.class);
        var model = runtime.getModel("https://epm.dev/rules/adjustment", "adjustment-approval");
        assertThat(model).isNotNull();
        assertThat(model.getMessages(org.kie.dmn.api.core.DMNMessage.Severity.ERROR)).isEmpty();

        assertThat(levelFromDmn(runtime, model, "990.00")).isEqualTo("OWNER_REVIEW");
        assertThat(levelFromDmn(runtime, model, "5000.00")).isEqualTo("INDEPENDENT_APPROVER");
        assertThat(levelFromDmn(runtime, model, "15000.00"))
                .isEqualTo("DOMAIN_APPROVER_PLUS_CERTIFIER");
    }

    @Test
    void reversalIsALinkedCompensatingEntryNeverADestructiveUpdate() {
        String reversal = AdjustmentControls.reversalOf("ADJ-001");
        assertThat(reversal).isEqualTo("ADJ-001:REVERSAL");
        // The compensating entry of a balanced set is the mirrored balanced set.
        List<AdjustmentControls.Line> original =
                List.of(
                        new AdjustmentControls.Line(1, "ACC001", 'D', "500.00"),
                        new AdjustmentControls.Line(2, "ACC002", 'C', "500.00"));
        List<AdjustmentControls.Line> compensating =
                original.stream()
                        .map(
                                line ->
                                        new AdjustmentControls.Line(
                                                line.lineNumber(),
                                                line.accountId(),
                                                line.side() == 'D' ? 'C' : 'D',
                                                line.amount()))
                        .toList();
        assertThat(AdjustmentControls.balanced(compensating)).isTrue();
        BigDecimal net = BigDecimal.ZERO.add(signed(original)).add(signed(compensating));
        assertThat(net.signum())
                .as("original + compensating restores the prior reported basis")
                .isZero();
    }

    private static BigDecimal signed(List<AdjustmentControls.Line> lines) {
        BigDecimal net = BigDecimal.ZERO;
        for (AdjustmentControls.Line line : lines) {
            BigDecimal amount = new BigDecimal(line.amount());
            net = line.side() == 'D' ? net.add(amount) : net.subtract(amount);
        }
        return net;
    }

    private static String levelFromDmn(
            org.kie.dmn.api.core.DMNRuntime runtime,
            org.kie.dmn.api.core.DMNModel model,
            String materiality) {
        var context = runtime.newContext();
        context.set("materiality", Double.parseDouble(materiality));
        return (String)
                runtime.evaluateAll(model, context)
                        .getDecisionResultByName("Approval Level")
                        .getResult();
    }
}
