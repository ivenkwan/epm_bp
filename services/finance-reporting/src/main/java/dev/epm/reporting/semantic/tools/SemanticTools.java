package dev.epm.reporting.semantic.tools;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Task 53 allowlisted semantic tools: the only data surface the Q&amp;A route may touch — bounded
 * lookups over the certified catalog with fixed arguments. There is no arbitrary SQL, Cypher, DRL,
 * code execution, or unrestricted filtering anywhere.
 */
public class SemanticTools {

    /** Allowlisted tool names (mirrors ai-routes.yaml). */
    public enum Tool {
        KPI_LOOKUP,
        VARIANCE_LOOKUP,
        EVIDENCE_READ
    }

    /** One certified KPI fact the tools may serve. */
    public record KpiFact(
            String kpiId,
            String period,
            String version,
            String value,
            boolean certified,
            List<String> entitledEntities,
            String evidenceRef) {}

    /** Tool execution outcome. */
    public record ToolResult(boolean ok, String value, String note) {}

    private final List<KpiFact> catalog;

    public SemanticTools(List<KpiFact> catalog) {
        this.catalog = catalog;
    }

    /**
     * Executes one allowlisted tool with fixed arguments. Unknown tools, uncertified data, and
     * out-of-scope entities are refused — the tool layer cannot leak.
     */
    public ToolResult execute(Tool tool, Map<String, String> args, List<String> entitledEntities) {
        String kpiId = args.getOrDefault("kpiId", "");
        return switch (tool) {
            case KPI_LOOKUP -> lookup(kpiId, entitledEntities);
            case VARIANCE_LOOKUP -> {
                ToolResult base = lookup(kpiId, entitledEntities);
                yield base.ok()
                        ? new ToolResult(true, base.value(), "variance basis " + base.note())
                        : base;
            }
            case EVIDENCE_READ -> {
                Optional<KpiFact> fact = find(kpiId);
                yield fact.map(f -> new ToolResult(true, f.evidenceRef(), "evidence for " + kpiId))
                        .orElse(new ToolResult(false, null, "no evidence for " + kpiId));
            }
        };
    }

    private ToolResult lookup(String kpiId, List<String> entitledEntities) {
        Optional<KpiFact> fact = find(kpiId);
        if (fact.isEmpty()) {
            return new ToolResult(false, null, "unknown KPI " + kpiId);
        }
        KpiFact f = fact.get();
        if (!f.certified()) {
            return new ToolResult(false, null, "KPI " + kpiId + " is not certified");
        }
        boolean inScope = f.entitledEntities().stream().anyMatch(entitledEntities::contains);
        if (!inScope) {
            return new ToolResult(false, null, "KPI " + kpiId + " is outside the entitled scope");
        }
        return new ToolResult(
                true, f.value(), "basis " + f.kpiId() + "/" + f.period() + "/" + f.version());
    }

    private Optional<KpiFact> find(String kpiId) {
        return catalog.stream().filter(f -> f.kpiId().equals(kpiId)).findFirst();
    }
}
