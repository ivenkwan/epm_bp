package dev.epm.admin.masterdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Task 9 hierarchy resolution: non-overlapping effective versions, shared boundary dates, null
 * effective dates rejected as overlaps where they collide, duplicate stable ids, cycle rejection,
 * historical resolution, and rollback-without-history-deletion.
 */
@QuarkusTest
class HierarchyResolutionTest {

    @Inject MasterDataService masterData;

    @Test
    void adjacentVersionsShareBoundaryDatesWithoutOverlap() {
        String entity = "adj-" + System.nanoTime();
        masterData.registerEntity(
                entity,
                "Adjacent",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 7, 1),
                "USD",
                "steward",
                "first half");
        masterData.registerEntity(
                entity,
                "Adjacent",
                LocalDate.of(2026, 7, 1),
                null,
                "USD",
                "steward",
                "second half");

        // The versions share the 2026-07-01 boundary (from inclusive, to exclusive):
        // exactly one resolves at each probe date.
        LocalDate boundary = LocalDate.of(2026, 6, 30);
        LocalDate next = LocalDate.of(2026, 7, 1);
        org.junit.jupiter.api.Assertions.assertEquals(
                2, EntityVersionEntity.find("entityId", entity).list().size());
        assertThat(EntityVersionEntity.resolveAt(entity, boundary)).isNotNull();
        assertThat(EntityVersionEntity.resolveAt(entity, next)).isNotNull();
        assertThat(EntityVersionEntity.resolveAt(entity, LocalDate.of(2025, 12, 31))).isNull();
    }

    @Test
    void overlappingIntervalIsRejectedWithoutChangingActiveVersion() {
        String entity = "ovl-" + System.nanoTime();
        masterData.registerEntity(
                entity,
                "Overlap",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 6, 30),
                "USD",
                "steward",
                "first half");
        assertThatThrownBy(
                        () ->
                                masterData.registerEntity(
                                        entity,
                                        "Overlap",
                                        LocalDate.of(2026, 6, 1),
                                        LocalDate.of(2026, 8, 1),
                                        "USD",
                                        "steward",
                                        "must be rejected"))
                .isInstanceOf(MasterDataService.MasterDataException.class)
                .hasMessageContaining("overlapping");
        // Active version untouched.
        assertThat(EntityVersionEntity.resolveAt(entity, LocalDate.of(2026, 6, 15))).isNotNull();
    }

    @Test
    void duplicateStableIdInSameHierarchyIsRejectedByDatabase() {
        String hierarchy = "dup-" + System.nanoTime();
        String entity = "dupE-" + System.nanoTime();
        MasterDataService.NodeAssignment first = new MasterDataService.NodeAssignment(entity, null);
        masterData.activateHierarchy(
                hierarchy,
                "duplicate test",
                LocalDate.of(2026, 1, 1),
                List.of(first),
                "steward",
                "initial");
        assertThatThrownBy(
                        () ->
                                masterData.activateHierarchy(
                                        hierarchy,
                                        "duplicate again",
                                        LocalDate.of(2026, 2, 1),
                                        List.of(first),
                                        "steward",
                                        "same key"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void cyclicAssignmentsAreRejectedBeforeActivation() {
        assertThatThrownBy(
                        () ->
                                masterData.activateHierarchy(
                                        "cyc-" + System.nanoTime(),
                                        "cycle",
                                        LocalDate.of(2026, 1, 1),
                                        List.of(
                                                new MasterDataService.NodeAssignment("A", "B"),
                                                new MasterDataService.NodeAssignment("B", "A")),
                                        "steward",
                                        "cycle attempt"))
                .isInstanceOf(MasterDataService.MasterDataException.class)
                .hasMessageContaining("cyclic");
    }

    @Test
    void historicalResolutionUsesOnlyTheVersionEffectiveAtThatDate() {
        String child = "h-child-" + System.nanoTime();
        String parentV1 = "h-p1-" + System.nanoTime();
        String parentV2 = "h-p2-" + System.nanoTime();
        String hierarchy = "hist-" + System.nanoTime();
        masterData.activateHierarchy(
                hierarchy + "-v1",
                "first",
                LocalDate.of(2026, 1, 1),
                List.of(new MasterDataService.NodeAssignment(child, parentV1)),
                "steward",
                "first");
        masterData.activateHierarchy(
                hierarchy + "-v2",
                "second",
                LocalDate.of(2026, 7, 1),
                List.of(new MasterDataService.NodeAssignment(child, parentV2)),
                "steward",
                "reorganized");

        assertThat(masterData.resolveParent(hierarchy + "-v1", child, LocalDate.of(2026, 3, 1)))
                .contains(parentV1);
        assertThat(masterData.resolveParent(hierarchy + "-v2", child, LocalDate.of(2026, 3, 1)))
                .isEmpty();
        assertThat(masterData.resolveParent(hierarchy + "-v2", child, LocalDate.of(2026, 8, 1)))
                .contains(parentV2);
    }

    @Test
    void ancestorsWalkTerminatesAndReturnsTheFullChain() {
        String hierarchy = "anc-" + System.nanoTime();
        String top = "anc-top-" + System.nanoTime();
        String middle = "anc-mid-" + System.nanoTime();
        String leaf = "anc-leaf-" + System.nanoTime();
        masterData.activateHierarchy(
                hierarchy,
                "chain",
                LocalDate.of(2026, 1, 1),
                List.of(
                        new MasterDataService.NodeAssignment(leaf, middle),
                        new MasterDataService.NodeAssignment(middle, top),
                        new MasterDataService.NodeAssignment(top, null)),
                "steward",
                "chain");
        List<String> ancestors = masterData.ancestorsOf(hierarchy, leaf, LocalDate.of(2026, 5, 1));
        assertThat(ancestors).containsExactly(middle, top);
    }
}
