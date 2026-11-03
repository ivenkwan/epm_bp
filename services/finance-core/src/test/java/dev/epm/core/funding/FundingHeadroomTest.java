package dev.epm.core.funding;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Task 24 golden tests (§2, hand-calculated): headroom = limit − utilization for the three
 * facilities; exact zero is a boundary; over-limit is an anomaly with the value kept explicit;
 * deposits reconcile to certified cash; no float anywhere.
 */
@QuarkusTest
class FundingHeadroomTest {

    @Inject FundingHeadroomCalculator calculator;

    @Test
    void reproducesGoldenHeadroomForAllFacilities() {
        // Golden §2: FAC001 374999.50 WITHIN_LIMIT; FAC002 0.00 AT_LIMIT_EXACT_ZERO;
        // FAC003 -1.00 OVER_LIMIT_ANOMALY.
        FundingHeadroomCalculator.Headroom fac001 =
                calculator.headroom(
                        new FundingHeadroomCalculator.Facility(
                                "FAC001", "USD", "1000000.00", "625000.50"));
        assertThat(fac001.headroom()).isEqualTo("374999.50");
        assertThat(fac001.status()).isEqualTo("WITHIN_LIMIT");

        FundingHeadroomCalculator.Headroom fac002 =
                calculator.headroom(
                        new FundingHeadroomCalculator.Facility(
                                "FAC002", "EUR", "500000.00", "500000.00"));
        assertThat(fac002.headroom()).isEqualTo("0.00");
        assertThat(fac002.status()).isEqualTo("AT_LIMIT_EXACT_ZERO");

        FundingHeadroomCalculator.Headroom fac003 =
                calculator.headroom(
                        new FundingHeadroomCalculator.Facility(
                                "FAC003", "USD", "250000.00", "250001.00"));
        assertThat(fac003.headroom()).isEqualTo("-1.00");
        assertThat(fac003.status()).isEqualTo("OVER_LIMIT_ANOMALY");
    }

    @Test
    void overLimitSurfacesAsAnomalyWithoutChangingTerms() {
        List<FundingHeadroomCalculator.Headroom> all =
                List.of(
                        calculator.headroom(
                                new FundingHeadroomCalculator.Facility(
                                        "FAC001", "USD", "1000000.00", "625000.50")),
                        calculator.headroom(
                                new FundingHeadroomCalculator.Facility(
                                        "FAC002", "EUR", "500000.00", "500000.00")),
                        calculator.headroom(
                                new FundingHeadroomCalculator.Facility(
                                        "FAC003", "USD", "250000.00", "250001.00")));
        List<FundingHeadroomCalculator.Headroom> anomalies = calculator.anomalies(all);
        assertThat(anomalies).hasSize(1);
        assertThat(anomalies.get(0).facilityId()).isEqualTo("FAC003");
        // The negative availability is explicit, never clamped to zero or hidden.
        assertThat(anomalies.get(0).headroom()).isEqualTo("-1.00");
    }

    @Test
    void depositsReconcileToCertifiedCash() {
        // Golden fixture deposits: DEP001-003 against the certified deposit-carrying
        // cash position (synthetic reconciliation basis).
        assertThat(
                        calculator.depositsReconcile(
                                List.of("150000.00", "80000.00", "60000.00"), "290000.00"))
                .isTrue();
        assertThat(calculator.depositsReconcile(List.of("150000.00", "80000.00"), "290000.00"))
                .isFalse();
    }

    @Test
    void negativeAvailabilityIsPreventedFromBecomingPositiveAnywhere() {
        FundingHeadroomCalculator.Headroom over =
                calculator.headroom(
                        new FundingHeadroomCalculator.Facility("X", "USD", "10.00", "1000.00"));
        assertThat(over.headroom()).isEqualTo("-990.00");
        assertThat(over.utilization()).isEqualTo("1000.00");
        assertThat(over.committedLimit()).isEqualTo("10.00");
    }
}
