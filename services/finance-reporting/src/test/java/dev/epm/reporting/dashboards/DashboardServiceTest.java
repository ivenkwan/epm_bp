package dev.epm.reporting.dashboards;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Task 45 dashboard acceptance (AC-F-013/F-041): certified tiles only, pivot slicing on governed
 * dimensions, saved views re-check entitlement at load (restricted entities denied explicitly),
 * drill stops at the lowest available governed grain and labels unavailable grain rather than
 * fabricating detail.
 */
class DashboardServiceTest {

    private final DashboardService service = new DashboardService();

    private static DashboardService.Tile certifiedTile() {
        return new DashboardService.Tile(
                "tile-cash",
                "kpi-cash",
                true,
                "2026-11-03T18:00:00Z",
                List.of("GROUP", "DIVISION", "ENTITY"));
    }

    @Test
    void dashboardsAssembleFromCertifiedTilesOnly() {
        assertThat(service.assemble(List.of(certifiedTile())).assembled()).isTrue();
        DashboardService.Tile uncertified =
                new DashboardService.Tile(
                        "tile-x", "kpi-x", false, "2026-11-03T18:00:00Z", List.of("GROUP"));
        assertThat(service.assemble(List.of(uncertified)).assembled()).isFalse();
        assertThat(service.assemble(List.of(uncertified)).note())
                .contains("uncertified tiles cannot join");
    }

    @Test
    void slicingAppliesOnlyOnGovernedDimensions() {
        DashboardService.SliceResult full =
                service.slice(
                        List.of(
                                new DashboardService.Slice("dim-entity", List.of("ENT-101")),
                                new DashboardService.Slice("dim-scenario", List.of("BUDGET"))),
                        List.of("dim-entity", "dim-scenario", "dim-period"));
        assertThat(full.appliedFully()).isTrue();

        DashboardService.SliceResult partial =
                service.slice(
                        List.of(
                                new DashboardService.Slice("dim-entity", List.of("ENT-101")),
                                new DashboardService.Slice("dim-spreadsheet-magic", List.of("X"))),
                        List.of("dim-entity"));
        assertThat(partial.appliedFully()).isFalse();
        assertThat(partial.applied()).hasSize(1);
        assertThat(partial.note()).contains("applied partially");
    }

    @Test
    void savedViewsReCheckEntitlementAtLoadAndDenyRestrictedEntities() {
        DashboardService.SavedView view =
                new DashboardService.SavedView(
                        "view-1",
                        "Group cash view",
                        "cfo@example.invalid",
                        List.of("ENT-101", "ENT-102"));
        DashboardService.ViewLoadResult loaded = service.loadView(view, List.of("ENT-101"));
        assertThat(loaded.visibleEntities()).containsExactly("ENT-101");
        assertThat(loaded.deniedEntities()).containsExactly("ENT-102");
    }

    @Test
    void drillStopsAtTheLowestAvailableGovernedGrain() {
        DashboardService.DrillResult deep = service.drill(certifiedTile(), "ENTITY");
        assertThat(deep.available()).isTrue();
        assertThat(deep.note()).contains("governed grain ENTITY");

        DashboardService.DrillResult beyond = service.drill(certifiedTile(), "ACCOUNT");
        assertThat(beyond.available()).isFalse();
        assertThat(beyond.note())
                .contains("lowest available governed grain is ENTITY")
                .contains("never fabricated");
    }
}
