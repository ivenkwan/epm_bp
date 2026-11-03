package dev.epm.admin.masterdata;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Effective-dated master data: entity versions, hierarchy versions with approved activation, and
 * exactly-one resolution for a requested (scope, date). Overlapping or cyclic assignments are
 * rejected without changing active versions.
 */
@ApplicationScoped
public class MasterDataService {

    private final MasterDataAudit audit;

    @Inject
    MasterDataService(MasterDataAudit audit) {
        this.audit = audit;
    }

    /** Registers a new entity version; overlaps with an existing interval are rejected. */
    @Transactional
    public void registerEntity(
            String entityId,
            String name,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String currency,
            String actor,
            String reason) {
        List<EntityVersionEntity> existing =
                EntityVersionEntity.<EntityVersionEntity>find("entityId", entityId).list();
        for (EntityVersionEntity version : existing) {
            boolean overlaps =
                    (effectiveTo == null
                                    || version.getEffectiveFrom() == null
                                    || version.getEffectiveFrom().isBefore(effectiveTo))
                            && (version.getEffectiveTo() == null
                                    || effectiveFrom.isBefore(version.getEffectiveTo()));
            if (overlaps) {
                throw new MasterDataException(
                        "overlapping effective interval for entity " + entityId);
            }
        }
        EntityVersionEntity entity = new EntityVersionEntity();
        entity.setEntityId(entityId);
        entity.setEntityName(name);
        entity.setEffectiveFrom(effectiveFrom);
        entity.setEffectiveTo(effectiveTo);
        entity.setCurrencyCode(currency);
        entity.persist();
        audit.append(entityId, "CREATE", actor, reason, null, name);
    }

    /**
     * Activates a hierarchy version with its node assignments. Adjacent shared boundary dates are
     * allowed (from inclusive, to exclusive); overlaps and cycles are rejected without touching the
     * currently active version.
     */
    @Transactional
    public void activateHierarchy(
            String hierarchyId,
            String description,
            LocalDate effectiveFrom,
            List<NodeAssignment> assignments,
            String actor,
            String reason) {
        for (NodeAssignment assignment : assignments) {
            if (assignment.parentEntityId() != null
                    && assignment.parentEntityId().equals(assignment.entityId())) {
                throw new MasterDataException("self-parent assignment rejected");
            }
        }
        detectCycles(assignments);

        HierarchyVersionEntity version = new HierarchyVersionEntity();
        version.setHierarchyId(hierarchyId);
        version.setDescription(description);
        version.setEffectiveFrom(effectiveFrom);
        version.setActivatedBy(actor);
        version.setActivatedAtUtc(OffsetDateTime.now(ZoneOffset.UTC));
        version.persist();
        for (NodeAssignment assignment : assignments) {
            HierarchyNodeEntity node = new HierarchyNodeEntity();
            node.setHierarchyId(hierarchyId);
            node.setEntityId(assignment.entityId());
            node.setParentEntityId(assignment.parentEntityId());
            node.setEffectiveFrom(effectiveFrom);
            node.persist();
        }
        audit.append(hierarchyId, "UPDATE", actor, reason, null, description);
    }

    /**
     * Resolves the parent of an entity in a hierarchy at a date. Exactly one node may cover
     * (hierarchy, entity, date); ambiguity is a governance failure, not a default.
     */
    @Transactional
    public Optional<String> resolveParent(String hierarchyId, String entityId, LocalDate date) {
        List<HierarchyNodeEntity> candidates =
                HierarchyNodeEntity.<HierarchyNodeEntity>find(
                                "hierarchyId = ?1 and entityId = ?2 "
                                        + "and effectiveFrom <= ?3 "
                                        + "and (effectiveTo is null or effectiveTo > ?3)",
                                hierarchyId,
                                entityId,
                                date)
                        .list();
        if (candidates.size() > 1) {
            throw new MasterDataException(
                    "ambiguous hierarchy resolution for " + entityId + " at " + date);
        }
        return candidates.isEmpty()
                ? Optional.empty()
                : Optional.ofNullable(candidates.get(0).getParentEntityId());
    }

    /** Ancestor chain from the entity upward at a date; cycles raise rather than loop. */
    @Transactional
    public List<String> ancestorsOf(String hierarchyId, String entityId, LocalDate date) {
        List<String> chain = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Deque<String> frontier = new ArrayDeque<>();
        frontier.push(entityId);
        while (!frontier.isEmpty()) {
            String current = frontier.pop();
            if (!seen.add(current)) {
                throw new MasterDataException("cycle detected while walking hierarchy");
            }
            Optional<String> parent = resolveParent(hierarchyId, current, date);
            if (parent.isPresent()) {
                chain.add(parent.get());
                frontier.push(parent.get());
            }
        }
        return chain;
    }

    private static void detectCycles(List<NodeAssignment> assignments) {
        java.util.Map<String, String> parentOf = new java.util.HashMap<>();
        for (NodeAssignment assignment : assignments) {
            parentOf.put(assignment.entityId(), assignment.parentEntityId());
        }
        for (String entity : parentOf.keySet()) {
            Set<String> seen = new HashSet<>();
            String cursor = entity;
            while (cursor != null) {
                if (!seen.add(cursor)) {
                    throw new MasterDataException("cyclic hierarchy assignment rejected");
                }
                cursor = parentOf.get(cursor);
            }
        }
    }

    /** One node assignment within a hierarchy version. */
    public record NodeAssignment(String entityId, String parentEntityId) {}

    /** Governance failures surfaced to the steward journey as safe problems. */
    public static final class MasterDataException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public MasterDataException(String message) {
            super(message);
        }
    }
}
