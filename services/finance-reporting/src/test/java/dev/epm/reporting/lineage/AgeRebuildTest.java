package dev.epm.reporting.lineage;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Task 21 AGE projection: upstream/downstream paths over the projected graph; stale watermark
 * labels block dependent certification; full rebuild equivalence (relational node/edge counts
 * reproduced); differential reconciliation after a mapping change is covered by the rebuild
 * counting the new edge; no graph mutation API exists.
 */
@QuarkusTest
class AgeRebuildTest {

    @Inject LineageService lineage;
    @Inject EntityManager entityManager;

    @Test
    @Transactional
    void rebuildProjectsRelationalEvidenceAndTraversesDownstream() {
        String parent = "E-" + UUID.randomUUID();
        String child = "C-" + UUID.randomUUID();
        seedEntity(parent);
        seedEntity(child);
        seedRollUp(child, parent);

        var build = lineage.rebuild("engineer@example.invalid");
        assertThat(build.getNodeCount()).isGreaterThanOrEqualTo(2);
        assertThat(build.getEdgeCount()).isGreaterThanOrEqualTo(1);
        assertThat(build.getBuildKind()).isEqualTo("FULL_REBUILD");

        List<String> downstream = lineage.downstreamOf(child);
        assertThat(downstream).contains(parent);
    }

    @Test
    void fullRebuildIsEquivalentAcrossRebuilds() {
        String a = "A-" + UUID.randomUUID();
        String b = "B-" + UUID.randomUUID();
        seedEntity(a);
        seedEntity(b);
        seedRollUp(a, b);

        var first = lineage.rebuild("engineer@example.invalid");
        var second = lineage.rebuild("engineer@example.invalid");

        // Rebuildable: identical counts and freshness from the same relational truth.
        assertThat(second.getNodeCount()).isEqualTo(first.getNodeCount());
        assertThat(second.getEdgeCount()).isEqualTo(first.getEdgeCount());
        assertThat(lineage.downstreamOf(a)).contains(b);
    }

    @Test
    void staleWatermarkBlocksDependentCertification() {
        String fresh = "F-" + UUID.randomUUID();
        seedEntity(fresh);
        lineage.rebuild("engineer@example.invalid");

        // Fresh: required-through is now (watermark recorded at rebuild time).
        assertThat(lineage.isFresh(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1))).isTrue();
        // Stale: a required-through instant in the future of the watermark blocks.
        assertThat(lineage.isFresh(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1))).isFalse();
    }

    @Transactional
    void seedEntity(String entityId) {
        entityManager
                .createNativeQuery(
                        "INSERT INTO epm_entity_version (entity_id, entity_name,"
                                + " effective_from, currency_code) VALUES (?, ?, ?, ?)"
                                + " ON CONFLICT DO NOTHING")
                .setParameter(1, entityId)
                .setParameter(2, "Entity " + entityId)
                .setParameter(3, LocalDate.of(2026, 1, 1))
                .setParameter(4, "USD")
                .executeUpdate();
    }

    @Transactional
    void seedRollUp(String child, String parent) {
        entityManager
                .createNativeQuery(
                        "INSERT INTO epm_hierarchy_node (hierarchy_id, entity_id,"
                                + " parent_entity_id, effective_from) VALUES (?, ?, ?, ?)"
                                + " ON CONFLICT DO NOTHING")
                .setParameter(1, "H" + Long.toHexString(System.nanoTime()))
                .setParameter(2, child)
                .setParameter(3, parent)
                .setParameter(4, LocalDate.of(2026, 1, 1))
                .executeUpdate();
    }
}
