package dev.epm.reporting.ai.qa;

import dev.epm.reporting.ai.AiControlPlane;
import dev.epm.reporting.semantic.tools.SemanticTools;
import java.util.List;
import java.util.Map;

/**
 * Task 53 bounded evidence-linked KPI Q&amp;A (Tier 2): answers come only from the allowlisted
 * semantic tools with access filtering applied before grounding and again on evidence-link
 * retrieval. Every answer carries the KPI/version/period basis and citations — or a safe refusal.
 * Values are never invented; unsupported questions, uncertified data, cross-entity scope, and
 * injection refuse.
 */
public class KpiQaService {

    /** A bounded question. */
    public record Question(String kpiId, String text, List<String> entitledEntities) {}

    /** A bounded answer (or refusal). */
    public record Answer(
            boolean answered,
            String text,
            String basis,
            List<String> citations,
            String provenance) {

        static Answer refused(String reason, String provenance) {
            return new Answer(false, reason, null, List.of(), provenance);
        }
    }

    /**
     * Answers a question through the gate and tools. The route being disabled — the shipped state —
     * refuses with the deterministic dashboard/drill alternative named; an admitted route answers
     * only from tool results.
     */
    public Answer ask(Question question, AiControlPlane.Gate gate, SemanticTools tools) {
        if (gate == null || !gate.allowed()) {
            return Answer.refused(
                    "AI Q&A is disabled — use the certified dashboard and drill views"
                            + (gate != null && gate.reason() != null
                                    ? " (" + gate.reason() + ")"
                                    : ""),
                    "deterministic-alternative");
        }
        // Access filter pass 1 (before grounding): the tool layer re-checks scope.
        SemanticTools.ToolResult value =
                tools.execute(
                        SemanticTools.Tool.KPI_LOOKUP,
                        Map.of("kpiId", question.kpiId()),
                        question.entitledEntities());
        if (!value.ok()) {
            return Answer.refused(value.note(), "tools:refused");
        }
        // Access filter pass 2 (evidence retrieval): citations re-check scope.
        SemanticTools.ToolResult evidence =
                tools.execute(
                        SemanticTools.Tool.EVIDENCE_READ,
                        Map.of("kpiId", question.kpiId()),
                        question.entitledEntities());
        List<String> citations = evidence.ok() ? List.of(evidence.value()) : List.of();
        if (citations.isEmpty()) {
            return Answer.refused("no citable evidence within scope", "tools:no-evidence");
        }
        return new Answer(
                true,
                question.kpiId() + " is " + value.value() + ".",
                value.note(),
                citations,
                "route:"
                        + gate.provenance().routeId()
                        + "/snapshot:"
                        + gate.provenance().snapshotId());
    }
}
