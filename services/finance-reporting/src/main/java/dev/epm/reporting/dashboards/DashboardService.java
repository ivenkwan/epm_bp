package dev.epm.reporting.dashboards;

import java.util.List;

/**
 * Task 45 certified dashboards: pivot-style slicing over governed dimensions, saved authorized
 * views that re-check entitlement at load time, and drill to the lowest available governed grain.
 * Restricted entities are denied explicitly (never silently filtered); grain that the governed data
 * does not carry is labelled unavailable — detail is never fabricated. Every tile carries
 * certification and freshness labels.
 */
public class DashboardService {

    /** One dashboard tile bound to a certified KPI. */
    public record Tile(
            String tileId,
            String kpiId,
            boolean certified,
            String asOfUtc,
            List<String> availableGrains) {}

    /** A pivot slice over governed dimensions. */
    public record Slice(String dimensionId, List<String> memberIds) {}

    /** Slicing outcome. */
    public record SliceResult(List<Slice> applied, boolean appliedFully, String note) {}

    /** A saved view with the entitlement scope it was saved under. */
    public record SavedView(String viewId, String name, String owner, List<String> entityScope) {}

    /** Saved-view load outcome (entitlement re-checked, restricted entities denied). */
    public record ViewLoadResult(
            SavedView view, List<String> visibleEntities, List<String> deniedEntities) {}

    /** Drill outcome with governed grain availability. */
    public record DrillResult(
            String kpiId, String drilledToGrain, boolean available, String note) {}

    /**
     * Applies pivot slicing: only governed dimensions participate; a slice naming members outside
     * the governed set applies partially with an explicit note.
     */
    public SliceResult slice(List<Slice> requested, List<String> governedDimensions) {
        List<Slice> applicable =
                requested.stream()
                        .filter(s -> governedDimensions.contains(s.dimensionId()))
                        .toList();
        boolean fully = applicable.size() == requested.size();
        return new SliceResult(
                applicable,
                fully,
                fully
                        ? "slicing applied on governed dimensions"
                        : "dimensions outside the governed set ignored: slicing applied partially");
    }

    /**
     * Loads a saved view for an actor: every entity in the saved scope is re-checked against the
     * actor's current entitlements — restricted entities are denied and listed, never silently
     * filtered.
     */
    public ViewLoadResult loadView(SavedView view, List<String> actorEntitledEntities) {
        List<String> visible =
                view.entityScope().stream().filter(actorEntitledEntities::contains).toList();
        List<String> denied =
                view.entityScope().stream()
                        .filter(entity -> !actorEntitledEntities.contains(entity))
                        .toList();
        return new ViewLoadResult(view, visible, denied);
    }

    /**
     * Drills a tile one grain deeper: drilling stops at the lowest grain the governed data actually
     * carries; unavailable grain is labelled, never fabricated.
     */
    public DrillResult drill(Tile tile, String requestedGrain) {
        boolean available = tile.availableGrains().contains(requestedGrain);
        String lowest = tile.availableGrains().get(tile.availableGrains().size() - 1);
        return new DrillResult(
                tile.kpiId(),
                requestedGrain,
                available,
                available
                        ? "drilled to governed grain " + requestedGrain
                        : "grain "
                                + requestedGrain
                                + " is unavailable — lowest available governed grain is "
                                + lowest
                                + " (detail never fabricated)");
    }

    /**
     * Dashboard assembly refuses uncertified tiles: an uncertified KPI cannot appear on a certified
     * dashboard.
     */
    public record AssemblyResult(List<Tile> tiles, boolean assembled, String note) {}

    public AssemblyResult assemble(List<Tile> tiles) {
        boolean allCertified = tiles.stream().allMatch(Tile::certified);
        return new AssemblyResult(
                tiles,
                allCertified,
                allCertified
                        ? "dashboard assembled from certified tiles with freshness labels"
                        : "uncertified tiles cannot join a certified dashboard");
    }
}
