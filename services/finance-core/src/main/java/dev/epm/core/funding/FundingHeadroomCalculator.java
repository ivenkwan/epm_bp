package dev.epm.core.funding;

import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Funding headroom (Task 24): headroom = eligible limit − utilization in fixed-precision decimals
 * (scale 2). Exact zero headroom is a boundary value, not an anomaly; an over-limit position is a
 * surfaced anomaly (owned exception, terms never changed).
 */
@ApplicationScoped
public class FundingHeadroomCalculator {

    /** Calculator version (golden expectations pinned against this). */
    public static final String VERSION = "treasury-1.0.0";

    /** One facility's governed terms. */
    public record Facility(
            String facilityId, String currency, String committedLimit, String utilization) {}

    /** Calculated headroom with anomaly classification. */
    public record Headroom(
            String facilityId,
            String currency,
            String committedLimit,
            String utilization,
            String headroom,
            String status) {}

    /** Calculates headroom for one facility. */
    public Headroom headroom(Facility facility) {
        BigDecimal limit = money(facility.committedLimit());
        BigDecimal utilization = money(facility.utilization());
        BigDecimal headroom = limit.subtract(utilization);
        String status;
        if (headroom.signum() > 0) {
            status = "WITHIN_LIMIT";
        } else if (headroom.signum() == 0) {
            status = "AT_LIMIT_EXACT_ZERO";
        } else {
            status = "OVER_LIMIT_ANOMALY";
        }
        return new Headroom(
                facility.facilityId(),
                facility.currency(),
                limit.toPlainString(),
                utilization.toPlainString(),
                headroom.toPlainString(),
                status);
    }

    /** Anomalies among calculated headrooms (surfaces the owned exceptions). */
    public List<Headroom> anomalies(List<Headroom> headrooms) {
        List<Headroom> anomalies = new ArrayList<>();
        for (Headroom headroom : headrooms) {
            if ("OVER_LIMIT_ANOMALY".equals(headroom.status())) {
                anomalies.add(headroom);
            }
        }
        return anomalies;
    }

    /** Deposit reconciliation: deposits must tie to the certified cash position. */
    public boolean depositsReconcile(List<String> depositAmounts, String certifiedAmount) {
        BigDecimal total = BigDecimal.ZERO;
        for (String deposit : depositAmounts) {
            total = total.add(money(deposit));
        }
        return total.compareTo(money(certifiedAmount)) == 0;
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
    }
}
