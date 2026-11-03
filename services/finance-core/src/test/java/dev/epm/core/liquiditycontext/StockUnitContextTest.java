package dev.epm.core.liquiditycontext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Task 36 (F-022, AC-F-022-01): zero stock renders as zero; blank optional remarks stay blank;
 * quantities/ageing carry source and freshness labels; the context reconciles to submitted detail;
 * denied scope returns empty without leakage; and the feature stays disabled (fail-closed) until an
 * owner signs activation.
 */
class StockUnitContextTest {

    private final StockUnitContextService context = new StockUnitContextService();

    private static final OffsetDateTime FRESH =
            OffsetDateTime.of(2026, 11, 3, 9, 0, 0, 0, ZoneOffset.UTC);

    private static StockUnitContextService.StockLine line(
            String itemId, String quantity, String remark) {
        return new StockUnitContextService.StockLine(
                itemId, quantity, "0_30", "FINISHED_GOODS", "MV-1", remark, "REV-SU-1", FRESH);
    }

    @Test
    void featureIsDisabledUntilAnOwnerSignsActivation() {
        assertThat(context.isEnabled()).isFalse();
        assertThatThrownBy(() -> context.contextFor(List.of(line("I-1", "10", null))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void zeroStockRendersAsZeroAndBlankRemarksStayBlank() {
        context.enable();
        List<StockUnitContextService.ContextRow> rows =
                context.contextFor(List.of(line("ZERO", "0", ""), line("R", "5", "remark")));
        StockUnitContextService.ContextRow zero = rows.get(0);
        assertThat(zero.quantity()).isEqualTo("0");
        assertThat(zero.optionalRemark()).isEmpty();
        assertThat(rows.get(1).optionalRemark()).contains("remark");
    }

    @Test
    void sourceAndFreshnessLabelsTravelWithEveryRow() {
        context.enable();
        StockUnitContextService.ContextRow row =
                context.contextFor(List.of(line("I-9", "3", null))).get(0);
        assertThat(row.sourceRevision()).isEqualTo("REV-SU-1");
        assertThat(row.sourceFreshness()).isEqualTo(FRESH);
        assertThat(row.ageingBucket()).contains("0_30");
        assertThat(row.classification()).contains("FINISHED_GOODS");
    }

    @Test
    void contextReconcilesToSubmittedDetailExactly() {
        context.enable();
        var submitted = List.of(line("A", "1", null), line("B", "2", "x"));
        var rows = context.contextFor(submitted);
        assertThat(context.reconcilesToSubmitted(rows, submitted)).isTrue();
        assertThat(context.reconcilesToSubmitted(rows, List.of(line("A", "1", null)))).isFalse();
    }

    @Test
    void deniedScopeReturnsEmptyWithoutDetailLeakage() {
        context.enable();
        var rows = context.contextFor(List.of(line("SECRET", "7", null)));
        Optional<StockUnitContextService.ContextRow> denied = context.detail(rows, "SECRET", false);
        assertThat(denied).isEmpty();
        assertThat(context.detail(rows, "SECRET", true)).isPresent();
    }
}
