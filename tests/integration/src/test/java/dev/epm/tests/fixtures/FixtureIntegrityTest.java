package dev.epm.tests.fixtures;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Task 3 fixture integrity gate: every fixture file is digest-verified against {@code
 * db/fixtures/provenance.manifest}, stable IDs are unique per dataset, money is fixed-precision
 * decimal strings, effective dates never overlap, references resolve, golden files follow the exact
 * expected-result schema, all twelve adversarial categories are present, and no real-world payment
 * identifiers or emails leak into synthetic data.
 */
class FixtureIntegrityTest {

    // Surefire runs with the module directory as working directory; fixtures live at repo root.
    private static final Path FIXTURES = Paths.get("..", "..", "db", "fixtures");
    private static final Pattern MONEY = Pattern.compile("^-?\\d+\\.\\d{2}$");
    private static final Pattern RATE = Pattern.compile("^-?\\d+\\.\\d{4,6}$");
    private static final Pattern ISO_DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final Pattern UTC_TIMESTAMP =
            Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$");
    private static final Pattern IBAN_OR_PAN = Pattern.compile("[A-Z]{2}\\d{14,}|\\b\\d{16}\\b");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+");

    private static final List<String> ADVERSARIAL_CATEGORIES =
            List.of(
                    "NULL_VS_ZERO",
                    "EMPTY_EXTRACT",
                    "DUPLICATE_NATURAL_KEY",
                    "NEGATIVE_OVERDRAFT",
                    "ZERO_DENOMINATOR",
                    "EXACT_TOLERANCE",
                    "T0_BOUNDARY",
                    "ADJACENT_EFFECTIVE_DATES",
                    "STALE_ETAG",
                    "DUPLICATE_EVENT",
                    "RESTRICTED_ENTITY",
                    "ROUNDING_RESIDUAL");

    private static final Map<String, Path> dataSets = new HashMap<>();
    private static final Map<String, List<String[]>> tableByHeader = new HashMap<>();

    @BeforeAll
    static void loadFixtures() throws IOException {
        try (Stream<Path> files = Files.walk(FIXTURES)) {
            files.filter(p -> p.toString().endsWith(".csv"))
                    .sorted()
                    .forEach(
                            p -> {
                                String name = FIXTURES.relativize(p).toString();
                                dataSets.put(name, p);
                                try {
                                    tableByHeader.put(name, readCsv(p));
                                } catch (IOException e) {
                                    throw new IllegalStateException("cannot read " + p, e);
                                }
                            });
        }
        assertThat(dataSets).as("fixture data sets must exist").isNotEmpty();
    }

    @Test
    void provenanceManifestCoversEveryFileAndMatchesDigests() throws Exception {
        Path manifest = FIXTURES.resolve("provenance.manifest");
        assertThat(manifest).as("provenance manifest must exist").exists();

        Set<String> manifestPaths = new HashSet<>();
        Map<String, String> manifestDigests = new HashMap<>();
        boolean syntheticDeclared = false;
        for (String line : Files.readAllLines(manifest, StandardCharsets.UTF_8)) {
            if (line.startsWith("#")) {
                syntheticDeclared |= line.contains("synthetic_only=true");
                continue;
            }
            String[] parts = line.trim().split("\\s{2,}");
            assertThat(parts).as("manifest row must be '<sha256>  <path>'").hasSize(2);
            manifestDigests.put(parts[1], parts[0]);
            manifestPaths.add(parts[1]);
        }
        assertThat(syntheticDeclared).as("manifest must declare synthetic_only=true").isTrue();

        Set<String> actualPaths;
        Path repoRoot = FIXTURES.resolve("..").resolve("..").normalize();
        try (Stream<Path> files = Files.walk(FIXTURES)) {
            actualPaths =
                    files.filter(Files::isRegularFile)
                            .filter(p -> !p.endsWith("provenance.manifest"))
                            .map(
                                    p ->
                                            repoRoot.relativize(p.normalize())
                                                    .toString()
                                                    .replace('\\', '/'))
                            .collect(Collectors.toSet());
        }
        assertThat(manifestPaths)
                .as("manifest must cover exactly the fixture files on disk")
                .containsExactlyInAnyOrderElementsOf(actualPaths);

        for (Map.Entry<String, String> entry : manifestDigests.entrySet()) {
            assertThat(sha256(repoRoot.resolve(entry.getKey())))
                    .as(
                            "digest drift in %s; rerun scripts/quality/regen-fixture-manifest.sh",
                            entry.getKey())
                    .isEqualTo(entry.getValue());
        }
    }

    @Test
    void stableIdsAreUniqueWithinEachDataSet() {
        Map<String, String> idColumnByPrefix =
                Map.ofEntries(
                        Map.entry("master-data/entities.csv", "entity_id"),
                        Map.entry("master-data/accounts.csv", "account_id"),
                        Map.entry("master-data/fx-rates.csv", "rate_id"),
                        Map.entry("treasury/opening-balances.csv", "balance_id"),
                        Map.entry("treasury/cash-movements.csv", "movement_id"),
                        Map.entry("treasury/facilities.csv", "facility_id"),
                        Map.entry("treasury/deposits.csv", "deposit_id"),
                        Map.entry("treasury/intercompany-loans.csv", "loan_id"),
                        Map.entry("treasury/covenant-definitions.csv", "covenant_id"),
                        Map.entry("planning/opening-balances.csv", "plan_id"),
                        Map.entry("planning/allocation-drivers.csv", "allocation_id"));

        idColumnByPrefix.forEach(
                (file, column) -> {
                    List<String[]> rows = headerAndRows(file);
                    int col = columnOf(file, column);
                    Set<String> composite = new HashSet<>();
                    for (String[] row : rows) {
                        // Hierarchy versions legitimately repeat entity_id; uniqueness is per
                        // (stable id, hierarchy version) there.
                        if (file.endsWith("entities.csv")) {
                            int version = columnOf(file, "hierarchy_version");
                            assertThat(composite.add(row[col] + "@" + row[version]))
                                    .as(
                                            "%s: duplicate stable id %s in version %s",
                                            file, row[col], row[version])
                                    .isTrue();
                        } else {
                            assertThat(composite.add(row[col]))
                                    .as("%s: duplicate stable id %s", file, row[col])
                                    .isTrue();
                        }
                    }
                });
    }

    @Test
    void moneyAndRateColumnsAreFixedPrecisionDecimalStrings() {
        moneyColumnMustMatch("treasury/opening-balances.csv", "amount");
        moneyColumnMustMatch("treasury/cash-movements.csv", "amount");
        moneyColumnMustMatch("treasury/facilities.csv", "committed_limit");
        moneyColumnMustMatch("treasury/facilities.csv", "utilization_amount");
        moneyColumnMustMatch("treasury/deposits.csv", "amount");
        moneyColumnMustMatch("treasury/intercompany-loans.csv", "principal");
        moneyColumnMustMatch("treasury/intercompany-loans.csv", "rate", RATE);
        moneyColumnMustMatch("treasury/covenant-inputs.csv", "numerator_amount");
        moneyColumnMustMatch("treasury/covenant-inputs.csv", "denominator_amount");
        moneyColumnMustMatch("planning/opening-balances.csv", "amount");
        moneyColumnMustMatch("expected/expected-cash-position.csv", "opening");
        moneyColumnMustMatch("expected/expected-cash-position.csv", "inflows");
        moneyColumnMustMatch("expected/expected-cash-position.csv", "outflows");
        moneyColumnMustMatch("expected/expected-cash-position.csv", "closing");
        moneyColumnMustMatch("expected/expected-cash-position-group.csv", "opening_total");
        moneyColumnMustMatch("expected/expected-cash-position-group.csv", "closing_total");
        moneyColumnMustMatch("expected/expected-headroom.csv", "committed_limit");
        moneyColumnMustMatch("expected/expected-headroom.csv", "utilization_amount");
        moneyColumnMustMatch("expected/expected-headroom.csv", "headroom");
        moneyColumnMustMatch("expected/expected-covenants.csv", "threshold", RATE);
        moneyColumnMustMatch("expected/expected-intercompany-matching.csv", "principal_difference");
        moneyColumnMustMatch("expected/expected-reconciliation.csv", "difference");
    }

    @Test
    void effectiveDateIntervalsDoNotOverlapPerStableKey() {
        List<String[]> rows = headerAndRows("master-data/entities.csv");
        int idCol = columnOf("master-data/entities.csv", "entity_id");
        int fromCol = columnOf("master-data/entities.csv", "effective_from");
        int toCol = columnOf("master-data/entities.csv", "effective_to");

        Map<String, List<String[]>> byEntity = new HashMap<>();
        for (String[] row : rows) {
            byEntity.computeIfAbsent(row[idCol], k -> new ArrayList<>()).add(row);
        }
        byEntity.forEach(
                (entity, versions) -> {
                    versions.sort((a, b) -> a[fromCol].compareTo(b[fromCol]));
                    for (int i = 1; i < versions.size(); i++) {
                        String previousEnd = versions.get(i - 1)[toCol];
                        String nextStart = versions.get(i)[fromCol];
                        if (!previousEnd.isEmpty()) {
                            assertThat(nextStart.compareTo(previousEnd) >= 0)
                                    .as(
                                            "entity %s: version starting %s overlaps previous end"
                                                    + " %s",
                                            entity, nextStart, previousEnd)
                                    .isTrue();
                        }
                    }
                    for (String[] v : versions) {
                        assertThat(ISO_DATE.matcher(v[fromCol]).matches()).isTrue();
                        assertThat(v[toCol].isEmpty() || ISO_DATE.matcher(v[toCol]).matches())
                                .as("effective_to must be ISO date or empty")
                                .isTrue();
                    }
                });
    }

    @Test
    void referentialIntegrityOfEntityAndAccountReferences() {
        Set<String> entities = valueSet("master-data/entities.csv", "entity_id");
        Set<String> accounts = valueSet("master-data/accounts.csv", "account_id");

        assertThat(referencesResolve("treasury/cash-movements.csv", "entity_id", entities))
                .isTrue();
        assertThat(referencesResolve("treasury/cash-movements.csv", "account_id", accounts))
                .isTrue();
        assertThat(referencesResolve("treasury/opening-balances.csv", "account_id", accounts))
                .isTrue();
        assertThat(referencesResolve("treasury/facilities.csv", "entity_id", entities)).isTrue();
        assertThat(
                        referencesResolve(
                                "treasury/intercompany-loans.csv", "lender_entity_id", entities))
                .isTrue();
        assertThat(
                        referencesResolve(
                                "treasury/intercompany-loans.csv", "borrower_entity_id", entities))
                .isTrue();
        assertThat(accounts).as("accounts loaded for owner check").isNotEmpty();
    }

    @Test
    void goldenResultFilesFollowExactSchema() {
        assertThat(headerOf("expected/expected-cash-position.csv"))
                .containsExactly(
                        "account_id",
                        "business_date",
                        "entity_id",
                        "currency",
                        "opening",
                        "inflows",
                        "outflows",
                        "closing",
                        "source_revision",
                        "fx_version",
                        "calculation_basis");
        assertThat(headerOf("expected/expected-cash-position-group.csv"))
                .containsExactly(
                        "scope",
                        "business_date",
                        "currency",
                        "opening_total",
                        "inflow_total",
                        "outflow_total",
                        "closing_total",
                        "fx_version",
                        "rounding_mode",
                        "scale");
        assertThat(headerOf("expected/expected-headroom.csv"))
                .containsExactly(
                        "facility_id",
                        "currency",
                        "committed_limit",
                        "utilization_amount",
                        "headroom",
                        "status");
        assertThat(headerOf("expected/expected-covenants.csv"))
                .containsExactly(
                        "covenant_id",
                        "entity_id",
                        "actual_ratio",
                        "threshold",
                        "headroom_ratio",
                        "status",
                        "rounding_mode",
                        "scale");
        assertThat(headerOf("expected/expected-reconciliation.csv"))
                .containsExactly(
                        "reconciliation_id",
                        "source_total",
                        "loaded_total",
                        "difference",
                        "tolerance",
                        "status");
        assertThat(headerOf("expected/expected-rounding-residual.csv"))
                .containsExactly(
                        "case_id",
                        "total_to_allocate",
                        "first_share",
                        "second_share",
                        "third_share",
                        "residual_assigned_to",
                        "total_after_allocation");
        assertThat(headerOf("expected/expected-planning-integration.csv"))
                .containsExactly(
                        "entity_id",
                        "plan_version",
                        "assets_total",
                        "liabilities_total",
                        "equity_total",
                        "imbalance",
                        "status");

        // Unavailable ratio values must be empty, never coerced to zero.
        for (String[] row : headerAndRows("expected/expected-covenants.csv")) {
            if ("INVALID_NOT_COMPUTABLE"
                    .equals(row[columnOf("expected/expected-covenants.csv", "status")])) {
                assertThat(row[columnOf("expected/expected-covenants.csv", "actual_ratio")])
                        .as("invalid ratio must stay empty, never 0.000000")
                        .isEmpty();
            }
        }
    }

    @Test
    void allAdversarialCategoriesDeclaredWithPayloads() {
        List<String[]> cases = headerAndRows("adversarial/adversarial-cases.csv");
        Set<String> declared =
                cases.stream()
                        .map(r -> r[columnOf("adversarial/adversarial-cases.csv", "category")])
                        .collect(Collectors.toSet());
        assertThat(declared)
                .as("all twelve adversarial categories must be declared")
                .containsExactlyInAnyOrderElementsOf(ADVERSARIAL_CATEGORIES);

        for (String[] row : cases) {
            String payload = row[columnOf("adversarial/adversarial-cases.csv", "payload_file")];
            assertThat(FIXTURES.resolve("adversarial").resolve(payload))
                    .as("adversarial payload %s must exist", payload)
                    .exists();
        }

        // Duplicate event case must actually contain the duplicate id.
        List<String[]> events = headerAndRows("adversarial/adv-duplicate-event.csv");
        assertThat(events).as("duplicate event payload rows").hasSize(2);
        assertThat(events.get(0)[0]).isEqualTo(events.get(1)[0]);

        // Null vs zero case must keep the literal zero row numeric and the null row empty.
        List<String[]> nullZero = headerAndRows("adversarial/adv-null-vs-zero.csv");
        assertThat(nullZero.get(0)[columnOf("adversarial/adv-null-vs-zero.csv", "amount")])
                .isEmpty();
        assertThat(nullZero.get(1)[columnOf("adversarial/adv-null-vs-zero.csv", "amount")])
                .isEqualTo("0.00");
    }

    @Test
    void timestampsAreUtcAndBoundaryCaseIsExactToTheSecond() {
        List<String[]> t0 = headerAndRows("adversarial/adv-t0-boundary.csv");
        int received = columnOf("adversarial/adv-t0-boundary.csv", "received_at_utc");
        int cutoff = columnOf("adversarial/adv-t0-boundary.csv", "cutoff_utc");
        for (String[] row : t0) {
            assertThat(UTC_TIMESTAMP.matcher(row[received]).matches())
                    .as("received_at must be RFC-3339 UTC")
                    .isTrue();
            assertThat(UTC_TIMESTAMP.matcher(row[cutoff]).matches())
                    .as("cutoff must be RFC-3339 UTC")
                    .isTrue();
        }
        assertThat(t0.get(0)[received]).isEqualTo("2026-11-02T16:59:59Z");
        assertThat(t0.get(1)[received]).isEqualTo("2026-11-02T17:00:01Z");
        assertThat(t0.get(0)[cutoff]).isEqualTo(t0.get(1)[cutoff]);
    }

    @Test
    void syntheticDataContainsNoRealWorldIdentifiersOrContacts() throws IOException {
        try (Stream<Path> files = Files.walk(FIXTURES)) {
            for (Path p : files.filter(Files::isRegularFile).toList()) {
                String content = Files.readString(p, StandardCharsets.UTF_8);
                assertThat(IBAN_OR_PAN.matcher(content).find())
                        .as("%s must not contain IBAN/PAN-like identifiers", p)
                        .isFalse();
                assertThat(EMAIL.matcher(content).find())
                        .as("%s must not contain email addresses", p)
                        .isFalse();
            }
        }
    }

    // ---------- helpers ----------

    private static List<String[]> readCsv(Path path) throws IOException {
        List<String[]> rows = new ArrayList<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            rows.add(line.split(",", -1));
        }
        return rows;
    }

    private static String[] headerRow(String file) {
        return tableByHeader.get(file).get(0);
    }

    private static List<String[]> headerAndRows(String file) {
        return tableByHeader.get(file).subList(1, tableByHeader.get(file).size());
    }

    private static List<String> headerOf(String file) {
        return Arrays.asList(headerRow(file));
    }

    private static int columnOf(String file, String column) {
        String[] header = headerRow(file);
        for (int i = 0; i < header.length; i++) {
            if (header[i].equals(column)) {
                return i;
            }
        }
        throw new IllegalStateException(
                "column " + column + " missing in " + file + " header " + List.of(header));
    }

    private static Set<String> valueSet(String file, String column) {
        int col = columnOf(file, column);
        return headerAndRows(file).stream().map(r -> r[col]).collect(Collectors.toSet());
    }

    private void moneyColumnMustMatch(String file, String column) {
        moneyColumnMustMatch(file, column, MONEY);
    }

    private void moneyColumnMustMatch(String file, String column, Pattern pattern) {
        int col = columnOf(file, column);
        for (String[] row : headerAndRows(file)) {
            String value = row[col].trim();
            assertThat(value.isEmpty() || pattern.matcher(value).matches())
                    .as(
                            "%s.%s value '%s' must be a fixed-scale decimal string (or empty for"
                                    + " declared null)",
                            file, column, value)
                    .isTrue();
        }
    }

    private boolean referencesResolve(String file, String column, Set<String> targets) {
        int col = columnOf(file, column);
        return headerAndRows(file).stream().allMatch(r -> targets.contains(r[col]));
    }

    private static String sha256(Path path) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(Files.readAllBytes(path));
        return HexFormat.of().formatHex(hash).toLowerCase(Locale.ROOT);
    }
}
