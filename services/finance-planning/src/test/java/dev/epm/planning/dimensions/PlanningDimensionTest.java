package dev.epm.planning.dimensions;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Task 40 shared-dimension acceptance: planning reuses governed effective-dated dimensions (retired
 * never resolves), and currency views preserve entitlements — entitled and group currencies
 * visible, others labelled unavailable rather than leaked or silently dropped.
 */
class PlanningDimensionTest {

    private final PlanningDimensionService service = new PlanningDimensionService();

    private static final PlanningDimensionService.Dimension ENTITY_DIMENSION =
            new PlanningDimensionService.Dimension(
                    "dim-entity", "Legal entity", "ENTITY", "ACTIVE", "mdm@example.invalid");

    @Test
    void membersResolveOnlyWhenEffectiveOnTheRequestedDate() {
        PlanningDimensionService.DimensionMember current =
                new PlanningDimensionService.DimensionMember(
                        "dim-entity",
                        "ENT-101",
                        "North sales",
                        null,
                        LocalDate.of(2024, 1, 1),
                        null,
                        "ACTIVE");
        PlanningDimensionService.DimensionMember superseded =
                new PlanningDimensionService.DimensionMember(
                        "dim-entity",
                        "ENT-101",
                        "North sales (old structure)",
                        null,
                        LocalDate.of(2020, 1, 1),
                        LocalDate.of(2023, 12, 31),
                        "ACTIVE");
        List<PlanningDimensionService.ResolvedMember> resolved =
                service.resolveMembers(
                        ENTITY_DIMENSION, List.of(current, superseded), LocalDate.of(2026, 2, 1));
        assertThat(resolved).hasSize(1);
        assertThat(resolved.get(0).memberId()).isEqualTo("ENT-101");
        assertThat(resolved.get(0).label()).isEqualTo("North sales");

        assertThat(
                        service.resolveMembers(
                                        ENTITY_DIMENSION,
                                        List.of(current, superseded),
                                        LocalDate.of(2022, 6, 1))
                                .get(0)
                                .label())
                .isEqualTo("North sales (old structure)");
    }

    @Test
    void retiredDimensionsAndMembersNeverResolve() {
        PlanningDimensionService.Dimension retiredDimension =
                new PlanningDimensionService.Dimension(
                        "dim-old", "Retired structure", "ENTITY", "RETIRED", "mdm@example.invalid");
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () ->
                        service.resolveMembers(
                                retiredDimension, List.of(), LocalDate.of(2026, 1, 1)));

        PlanningDimensionService.DimensionMember retiredMember =
                new PlanningDimensionService.DimensionMember(
                        "dim-entity",
                        "ENT-999",
                        "Closed unit",
                        null,
                        LocalDate.of(2024, 1, 1),
                        null,
                        "RETIRED");
        assertThat(
                        service.resolveMembers(
                                        ENTITY_DIMENSION,
                                        List.of(retiredMember),
                                        LocalDate.of(2026, 2, 1))
                                .isEmpty())
                .isTrue();
    }

    @Test
    void currencyViewsPreserveEntitlementsAndLabelTheRest() {
        List<PlanningDimensionService.CurrencyDefinition> currencies =
                List.of(
                        new PlanningDimensionService.CurrencyDefinition("USD", "GROUP", 2),
                        new PlanningDimensionService.CurrencyDefinition("EUR", "LOCAL", 2),
                        new PlanningDimensionService.CurrencyDefinition("JPY", "LOCAL", 0));
        List<PlanningDimensionService.CurrencyViewRow> view =
                service.currencyView(currencies, List.of("EUR"), "USD");

        PlanningDimensionService.CurrencyViewRow usd =
                view.stream().filter(r -> r.currencyCode().equals("USD")).findFirst().orElseThrow();
        assertThat(usd.visible()).isTrue(); // group currency always visible

        PlanningDimensionService.CurrencyViewRow eur =
                view.stream().filter(r -> r.currencyCode().equals("EUR")).findFirst().orElseThrow();
        assertThat(eur.visible()).isTrue(); // entitled

        PlanningDimensionService.CurrencyViewRow jpy =
                view.stream().filter(r -> r.currencyCode().equals("JPY")).findFirst().orElseThrow();
        assertThat(jpy.visible()).isFalse(); // not entitled → labelled, not leaked
        assertThat(jpy.availabilityNote()).contains("not entitled");
    }
}
