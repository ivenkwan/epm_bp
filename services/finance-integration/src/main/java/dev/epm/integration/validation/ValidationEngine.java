package dev.epm.integration.validation;

import java.util.ArrayList;
import java.util.List;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;

/**
 * Deterministic validation over landed rows (Task 19): the Task 13 rule set ({@code
 * rules/drools/validation/validation-rules.drl}, version val-1.0.0) evaluates typed facts with no
 * side effects; outcomes are pass/quarantine with rule coordinates, expected vs actual, and a
 * human-readable explanation.
 */
public final class ValidationEngine {

    private final KieBase rules;

    private ValidationEngine(KieBase rules) {
        this.rules = rules;
    }

    /** Exposes the compiled rule base (service-side caching). */
    KieBase rules() {
        return rules;
    }

    /** Builds the engine over the versioned rule set from the repository rules dir. */
    public static ValidationEngine fromRepository() {
        java.io.File drl = resolveRulesFile();
        org.kie.internal.utils.KieHelper helper = new org.kie.internal.utils.KieHelper();
        helper.addResource(
                org.kie.internal.io.ResourceFactory.newFileResource(drl),
                org.kie.api.io.ResourceType.DRL);
        return new ValidationEngine(helper.build());
    }

    /** Resolves the rules file from the working dir (repo root or module dir). */
    private static java.io.File resolveRulesFile() {
        java.util.List<java.nio.file.Path> candidates =
                java.util.List.of(
                        java.nio.file.Path.of(
                                "rules", "drools", "validation", "validation-rules.drl"),
                        java.nio.file.Path.of(
                                "..",
                                "..",
                                "rules",
                                "drools",
                                "validation",
                                "validation-rules.drl"));
        for (java.nio.file.Path candidate : candidates) {
            if (java.nio.file.Files.exists(candidate)) {
                return candidate.toFile();
            }
        }
        throw new IllegalStateException("validation rules not found on disk");
    }

    /** Result of evaluating one landed batch. */
    public static final class Result {
        public final List<LandedRow> rows = new ArrayList<>();
        public final List<Quarantine> quarantined = new ArrayList<>();
        public int passedRows;

        public boolean allPassed() {
            return quarantined.isEmpty();
        }
    }

    /** One quarantined row with full explanation coordinates. */
    public record Quarantine(
            int rowNumber, String ruleName, String expected, String actual, String explanation) {}

    /** Evaluates the rows; row numbers are landed positions (1-based). */
    public Result evaluate(List<LandedRow> facts) {
        Result result = new Result();
        KieSession session = rules.newKieSession();
        try {
            for (int index = 0; index < facts.size(); index++) {
                LandedRow row = facts.get(index);
                session.insert(row);
                session.fireAllRules(1000);
                if (row.isQuarantined()) {
                    result.quarantined.add(
                            new Quarantine(
                                    index + 1,
                                    row.getViolationCode(),
                                    expectedFor(row.getViolationCode()),
                                    actualFor(row),
                                    row.getViolationCode() + " on row " + (index + 1)));
                } else {
                    result.passedRows++;
                }
                result.rows.add(row);
            }
        } finally {
            session.dispose();
        }
        return result;
    }

    private static String expectedFor(String code) {
        return switch (code) {
            case "NULL_AMOUNT" -> "non-null decimal string";
            case "MISSING_ACCOUNT" -> "known account id";
            case "CURRENCY_INVALID" -> "three uppercase letters";
            case "INVALID_SIGN" -> "non-negative for RECEIPT category";
            default -> "valid value";
        };
    }

    private static String actualFor(LandedRow row) {
        return switch (row.getViolationCode()) {
            case "NULL_AMOUNT" -> "null";
            case "MISSING_ACCOUNT" -> row.getAccountId() == null ? "null" : row.getAccountId();
            case "CURRENCY_INVALID" -> row.getCurrency();
            case "INVALID_SIGN" ->
                    row.getAmount() == null ? "null" : row.getAmount().toPlainString();
            default -> "unknown";
        };
    }
}
