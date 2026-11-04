package dev.epm.planning.eliminations;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Task 50 management-only intercompany eliminations: counterparty plan lines match on a shared key
 * within a declared tolerance; matched pairs eliminate exactly once under an approved run;
 * mismatches and one-sided lines open cases with entity owners — never an automatic elimination or
 * one-sided overwrite. Both sides and all differences are preserved. Every output carries the
 * MANAGEMENT_ONLY label; statutory or legal-consolidation semantics are denied by design (F-043
 * exclusion).
 */
public class EliminationService {

    /** One counterparty plan line (from {@code epm_counterparty_plan}). */
    public record PlanLine(
            String lineId,
            String entityId,
            String counterparty,
            String matchingKey,
            Direction direction,
            BigDecimal amount) {

        public enum Direction {
            PAY,
            RECEIVE
        }
    }

    /** Matching outcome for one key. */
    public record MatchResult(
            String matchingKey,
            String leftEntity,
            String rightEntity,
            BigDecimal leftAmount,
            BigDecimal rightAmount,
            BigDecimal difference,
            Status status) {

        public enum Status {
            MATCHED_WITHIN_TOLERANCE,
            MISMATCH_BEYOND_TOLERANCE,
            ONE_SIDED
        }
    }

    /** Matches counterparty lines by key; both sides preserved with differences. */
    public List<MatchResult> match(List<PlanLine> lines, BigDecimal tolerance) {
        Map<String, List<PlanLine>> byKey = new LinkedHashMap<>();
        for (PlanLine line : lines) {
            byKey.computeIfAbsent(line.matchingKey(), k -> new ArrayList<>()).add(line);
        }
        List<MatchResult> results = new ArrayList<>();
        for (Map.Entry<String, List<PlanLine>> entry : byKey.entrySet()) {
            List<PlanLine> group = entry.getValue();
            PlanLine pay =
                    group.stream()
                            .filter(l -> l.direction() == PlanLine.Direction.PAY)
                            .findFirst()
                            .orElse(null);
            PlanLine receive =
                    group.stream()
                            .filter(l -> l.direction() == PlanLine.Direction.RECEIVE)
                            .findFirst()
                            .orElse(null);
            if (pay == null || receive == null) {
                PlanLine present = pay != null ? pay : receive;
                results.add(
                        new MatchResult(
                                entry.getKey(),
                                present != null ? present.entityId() : null,
                                present != null ? present.counterparty() : null,
                                pay != null ? pay.amount() : null,
                                receive != null ? receive.amount() : null,
                                null,
                                MatchResult.Status.ONE_SIDED));
                continue;
            }
            BigDecimal difference = pay.amount().subtract(receive.amount()).abs();
            results.add(
                    new MatchResult(
                            entry.getKey(),
                            pay.entityId(),
                            receive.entityId(),
                            pay.amount(),
                            receive.amount(),
                            difference,
                            difference.compareTo(tolerance) <= 0
                                    ? MatchResult.Status.MATCHED_WITHIN_TOLERANCE
                                    : MatchResult.Status.MISMATCH_BEYOND_TOLERANCE));
        }
        return results;
    }

    /** One elimination ledger entry (both sides + eliminated amount + trace). */
    public record EliminationEntry(
            String matchingKey,
            String leftEntity,
            String rightEntity,
            BigDecimal leftAmount,
            BigDecimal rightAmount,
            BigDecimal eliminated,
            String trace) {}

    /** An elimination run: approved identity + immutable entries. */
    public record EliminationRun(
            String runId, String planVersion, String label, List<EliminationEntry> entries) {

        public static final String LABEL = "MANAGEMENT_ONLY";
    }

    /** Run outcome. */
    public record RunOutcome(
            EliminationRun run, List<CaseRecord> openedCases, boolean executed, String note) {

        static RunOutcome refused(String note) {
            return new RunOutcome(null, List.of(), false, note);
        }
    }

    /** A mismatch/one-sided case routed to entity owners (playbook 7). */
    public record CaseRecord(
            String caseId,
            String matchingKey,
            String difference,
            String ownerEntity,
            String status) {}

    /**
     * Executes an approved elimination run: matched pairs eliminate the lower of the two sides
     * exactly once (replay of the same runId is refused as a second elimination); mismatches and
     * one-sided lines open cases instead.
     */
    public RunOutcome eliminate(
            List<MatchResult> matches,
            String runId,
            String planVersion,
            String approvedBy,
            List<EliminationRun> existingRuns,
            java.util.function.UnaryOperator<String> caseIdFor) {
        if (approvedBy == null || approvedBy.isBlank()) {
            return RunOutcome.refused("elimination requires independent approval");
        }
        if (existingRuns.stream().anyMatch(r -> r.runId().equals(runId))) {
            return RunOutcome.refused(
                    "run " + runId + " already exists — replaying would eliminate twice");
        }
        List<EliminationEntry> entries = new ArrayList<>();
        List<CaseRecord> cases = new ArrayList<>();
        int caseSeq = 0;
        for (MatchResult match : matches) {
            switch (match.status()) {
                case MATCHED_WITHIN_TOLERANCE -> {
                    BigDecimal eliminated =
                            match.leftAmount()
                                    .min(match.rightAmount())
                                    .setScale(2, java.math.RoundingMode.HALF_UP);
                    entries.add(
                            new EliminationEntry(
                                    match.matchingKey(),
                                    match.leftEntity(),
                                    match.rightEntity(),
                                    match.leftAmount(),
                                    match.rightAmount(),
                                    eliminated,
                                    "MANAGEMENT_ONLY "
                                            + match.leftEntity()
                                            + " ↔ "
                                            + match.rightEntity()
                                            + " key "
                                            + match.matchingKey()
                                            + " eliminated "
                                            + eliminated.toPlainString()));
                }
                case MISMATCH_BEYOND_TOLERANCE ->
                        cases.add(
                                new CaseRecord(
                                        caseIdFor.apply(runId + "-k" + match.matchingKey()),
                                        match.matchingKey(),
                                        match.difference().toPlainString(),
                                        match.leftEntity(),
                                        "OPEN"));
                case ONE_SIDED ->
                        cases.add(
                                new CaseRecord(
                                        caseIdFor.apply(runId + "-one-" + (++caseSeq)),
                                        match.matchingKey(),
                                        "one-sided",
                                        match.leftEntity() == null
                                                ? match.rightEntity()
                                                : match.leftEntity(),
                                        "OPEN"));
            }
        }
        return new RunOutcome(
                new EliminationRun(runId, planVersion, EliminationRun.LABEL, entries),
                cases,
                true,
                "eliminated "
                        + entries.size()
                        + " pairs; "
                        + cases.size()
                        + " mismatch/one-sided cases opened (never auto-eliminated)");
    }

    /** Reconciled bridge: eliminated total and residual per key for the management view. */
    public record Bridge(
            BigDecimal totalEliminated,
            BigDecimal totalResidual,
            List<String> perKeyResiduals,
            String label) {}

    public Bridge bridge(EliminationRun run, List<MatchResult> matches) {
        BigDecimal eliminated =
                run.entries().stream()
                        .map(EliminationEntry::eliminated)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<String> residuals = new ArrayList<>();
        BigDecimal residualTotal = BigDecimal.ZERO;
        for (MatchResult match : matches) {
            if (match.status() == MatchResult.Status.MATCHED_WITHIN_TOLERANCE) {
                BigDecimal diff =
                        match.leftAmount()
                                .subtract(match.rightAmount())
                                .abs()
                                .setScale(2, java.math.RoundingMode.HALF_UP);
                if (diff.signum() != 0) {
                    residuals.add(match.matchingKey() + ": " + diff.toPlainString());
                    residualTotal = residualTotal.add(diff);
                }
            }
        }
        return new Bridge(
                eliminated.setScale(2), residualTotal.setScale(2), residuals, EliminationRun.LABEL);
    }

    /** F-043 exclusion: statutory/legal-consolidation requests are denied by design. */
    public String statutoryRequest(String request) {
        return "DENIED: "
                + request
                + " — management-only eliminations must never be represented as statutory"
                + " consolidation; no statutory or legal-consolidation capability exists";
    }
}
