package dev.epm.tests.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Task 4 platform smoke: proves the local Kubernetes platform answers a token-authenticated gateway
 * request with a correlation id and emits correlated telemetry that the collector received, without
 * leaking credentials into telemetry.
 *
 * <p>Runs only when {@code EPM_PLATFORM_SMOKE=true} (set by scripts/setup after
 * install-local-dependencies.sh); plain CI runs skip it because no cluster exists there. Endpoints
 * and credentials come from {@code .secrets/local.env}, generated at install time — no credentials
 * are committed.
 */
@EnabledIfEnvironmentVariable(named = "EPM_PLATFORM_SMOKE", matches = "true")
class PlatformSmokeTest {

    private static final HttpClient CLIENT =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private static String gatewayUrl;
    private static String keycloakUrl;
    private static String collectorUrl;
    private static String nodeAddress;
    private static String smokePassword;
    private static String gatewayClientSecret;

    @BeforeAll
    static void loadEnvironment() throws IOException {
        Map<String, String> env = new HashMap<>(System.getenv());
        Path local = Path.of("..", "..", ".secrets", "local.env");
        if (Files.exists(local)) {
            for (String line : Files.readAllLines(local, StandardCharsets.UTF_8)) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    int eq = line.indexOf('=');
                    env.putIfAbsent(line.substring(0, eq), line.substring(eq + 1));
                }
            }
        }
        gatewayUrl = env.getOrDefault("EPM_GATEWAY_URL", "http://localhost:18080");
        keycloakUrl = env.getOrDefault("EPM_KEYCLOAK_URL", "http://localhost:18081");
        collectorUrl =
                env.getOrDefault("EPM_COLLECTOR_METRICS_URL", "http://localhost:18888/metrics");
        nodeAddress = env.getOrDefault("EPM_NODE_ADDRESS", "localhost:30089");
        smokePassword = env.get("EPM_SMOKE_PASSWORD");
        gatewayClientSecret = env.get("EPM_GATEWAY_CLIENT_SECRET");
        assertThat(smokePassword)
                .as("EPM_SMOKE_PASSWORD must be available (see .secrets/local.env)")
                .isNotNull();
        assertThat(gatewayClientSecret)
                .as("EPM_GATEWAY_CLIENT_SECRET must be available (see .secrets/local.env)")
                .isNotNull();
    }

    @Test
    void gatewayRejectsUnauthenticatedRequests() throws Exception {
        HttpResponse<String> response =
                CLIENT.send(
                        HttpRequest.newBuilder(URI.create(gatewayUrl + "/api/v1/core/health"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode())
                .as("OIDC bearer-only route must reject anonymous calls")
                .isEqualTo(401);
    }

    @Test
    void tokenAuthenticatedGatewayRequestSucceedsWithCorrelationId() throws Exception {
        String token = fetchAccessToken();
        HttpResponse<String> response =
                CLIENT.send(
                        HttpRequest.newBuilder(URI.create(gatewayUrl + "/api/v1/core/health"))
                                .header("Authorization", "Bearer " + token)
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode())
                .as("authenticated gateway call must reach finance-core")
                .isEqualTo(200);
        assertThat(response.body()).contains("finance-core");
        assertThat(response.headers().firstValue("X-Correlation-Id"))
                .as("gateway must return a correlation id")
                .isPresent();
    }

    @Test
    void telemetryPipelineReceivedSignalsForTheSmokeRequest() throws Exception {
        // Warm-up: generate one authenticated request, then wait for the batched export.
        fetchAccessToken();
        String token = fetchAccessToken();
        CLIENT.send(
                HttpRequest.newBuilder(URI.create(gatewayUrl + "/api/v1/core/health"))
                        .header("Authorization", "Bearer " + token)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        long deadline = System.currentTimeMillis() + 60_000;
        long acceptedSpans = -1;
        long acceptedMetricPoints = -1;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(3_000);
            String metrics = fetch(collectorUrl);
            acceptedSpans = sumCounter(metrics, "otelcol_receiver_accepted_spans");
            acceptedMetricPoints = sumCounter(metrics, "otelcol_receiver_accepted_metric_points");
            if (acceptedSpans > 0 && acceptedMetricPoints > 0) {
                break;
            }
        }
        assertThat(acceptedSpans).as("collector must have accepted OTLP spans").isPositive();
        assertThat(acceptedMetricPoints)
                .as("collector must have accepted OTLP metric points")
                .isPositive();
    }

    @Test
    void telemetryCarriesGovernanceLabels() throws Exception {
        // Received application metrics are re-exposed with resource attributes converted to
        // labels on the collector's prometheus exporter; governance labels must appear there.
        String receivedUrl = receivedMetricsUrl();
        generateOneAuthenticatedRequest();
        long deadline = System.currentTimeMillis() + 60_000;
        String exposition = "";
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(3_000);
            exposition = fetch(receivedUrl);
            if (exposition.contains("epm_environment=\"dev\"")) {
                break;
            }
        }
        assertThat(exposition)
                .as("resource labels must include epm.environment=\"dev\" on received metrics")
                .contains("epm_environment=\"dev\"");
        assertThat(exposition)
                .as("resource labels must include epm.product on received metrics")
                .contains("epm_product=\"epm-finance\"");
    }

    @Test
    void rawBearerTokensDoNotAppearInCollectorTelemetry() throws Exception {
        // Re-issue an authenticated call, let it export, then scan the collector's own
        // telemetry exposition: the token value must never appear.
        String token = fetchAccessToken();
        CLIENT.send(
                HttpRequest.newBuilder(URI.create(gatewayUrl + "/api/v1/core/health"))
                        .header("Authorization", "Bearer " + token)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        Thread.sleep(6_000);
        String metrics = fetch(collectorUrl);
        assertThat(metrics)
                .as("no token material may appear in telemetry exposition")
                .doesNotContain(token);
    }

    // ---------- helpers ----------

    private void generateOneAuthenticatedRequest() throws Exception {
        String token = fetchAccessToken();
        CLIENT.send(
                HttpRequest.newBuilder(URI.create(gatewayUrl + "/api/v1/core/health"))
                        .header("Authorization", "Bearer " + token)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String receivedMetricsUrl() {
        // The received-metrics exposition (collector prometheus exporter) is reachable
        // through the cluster node's NodePort; the node address is recorded at install time.
        return "http://" + nodeAddress + "/metrics";
    }

    private String fetchAccessToken() throws Exception {
        String form =
                "grant_type=password"
                        + "&client_id="
                        + URLEncoder.encode("apisix-gateway", StandardCharsets.UTF_8)
                        + "&client_secret="
                        + URLEncoder.encode(gatewayClientSecret, StandardCharsets.UTF_8)
                        + "&username="
                        + URLEncoder.encode("smoke.tester", StandardCharsets.UTF_8)
                        + "&password="
                        + URLEncoder.encode(smokePassword, StandardCharsets.UTF_8);
        HttpRequest request =
                HttpRequest.newBuilder(
                                URI.create(
                                        keycloakUrl + "/realms/epm/protocol/openid-connect/token"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form))
                        .build();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode())
                .as("keycloak token endpoint must accept smoke credentials")
                .isEqualTo(200);
        var matcher =
                Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"").matcher(response.body());
        assertThat(matcher.find()).as("token response must carry access_token").isTrue();
        return matcher.group(1);
    }

    private String fetch(String url) throws Exception {
        HttpResponse<String> response =
                CLIENT.send(
                        HttpRequest.newBuilder(URI.create(url)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("GET %s", url).isEqualTo(200);
        return response.body();
    }

    /** Sum an OTel counter across its label permutations in Prometheus exposition text. */
    private long sumCounter(String exposition, String counterName) {
        long total = 0;
        boolean any = false;
        for (String line : exposition.split("\n")) {
            if (!line.startsWith(counterName + "{") && !line.startsWith(counterName + " ")) {
                continue;
            }
            String value = line.substring(line.lastIndexOf(' ') + 1).trim();
            if (!value.isEmpty() && !value.equals("NaN")) {
                try {
                    total += (long) Double.parseDouble(value);
                    any = true;
                } catch (NumberFormatException ignored) {
                    // header or non-sample line
                }
            }
        }
        return any ? total : -1;
    }
}
