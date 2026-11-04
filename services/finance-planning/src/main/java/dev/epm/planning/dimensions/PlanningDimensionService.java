package dev.epm.planning.dimensions;

import java.time.LocalDate;
import java.util.List;

/**
 * Task 40 shared planning dimensions: planning reuses the governed dimension sets (effective-dated
 * members, ownership, status) — no planning-local dimension is invented. Selection resolves the
 * members effective on a date; retired dimensions and members never resolve. Currency views
 * preserve entitlements: an actor sees only the currencies their scope entitles plus the group
 * currency, and unavailable currencies are labelled rather than silently dropped or leaked.
 */
public class PlanningDimensionService {

    /** One governed dimension (from {@code epm_planning_dimension}). */
    public record Dimension(
            String dimensionId, String name, String dimensionType, String status, String owner) {}

    /** One effective-dated dimension member (from {@code epm_dimension_member}). */
    public record DimensionMember(
            String dimensionId,
            String memberId,
            String label,
            String parentMemberId,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String status) {}

    /** A governed currency definition (from {@code epm_currency_definition}). */
    public record CurrencyDefinition(
            String currencyCode, String currencyRole, int minorUnitScale) {}

    /** A member resolved for a date, with its governed lineage. */
    public record ResolvedMember(
            String dimensionId, String memberId, String label, String parentMemberId) {}

    /** Entitlement-preserving currency view row. */
    public record CurrencyViewRow(
            String currencyCode, String currencyRole, boolean visible, String availabilityNote) {}

    /** Resolves members effective exactly on {@code on} for one dimension. */
    public List<ResolvedMember> resolveMembers(
            Dimension dimension, List<DimensionMember> members, LocalDate on) {
        if (!"ACTIVE".equals(dimension.status())) {
            throw new IllegalArgumentException(
                    "dimension " + dimension.dimensionId() + " is " + dimension.status());
        }
        return members.stream()
                .filter(m -> m.dimensionId().equals(dimension.dimensionId()))
                .filter(m -> "ACTIVE".equals(m.status()))
                .filter(m -> effective(m, on))
                .map(
                        m ->
                                new ResolvedMember(
                                        m.dimensionId(),
                                        m.memberId(),
                                        m.label(),
                                        m.parentMemberId()))
                .toList();
    }

    private static boolean effective(DimensionMember member, LocalDate on) {
        return !on.isBefore(member.effectiveFrom())
                && (member.effectiveTo() == null || !on.isAfter(member.effectiveTo()));
    }

    /**
     * Builds the entitlement-preserving currency view: entitled currencies and the group currency
     * are visible; everything else is present-but-labelled so the analyst sees governed
     * unavailability instead of fabricated coverage.
     */
    public List<CurrencyViewRow> currencyView(
            List<CurrencyDefinition> currencies,
            List<String> entitledCurrencies,
            String groupCurrency) {
        return currencies.stream()
                .map(
                        currency -> {
                            boolean entitled =
                                    entitledCurrencies.contains(currency.currencyCode())
                                            || currency.currencyCode().equals(groupCurrency);
                            return new CurrencyViewRow(
                                    currency.currencyCode(),
                                    currency.currencyRole(),
                                    entitled,
                                    entitled
                                            ? null
                                            : "not entitled: analysis in this currency requires"
                                                    + " entitlement grant");
                        })
                .toList();
    }
}
