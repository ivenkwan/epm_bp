package dev.epm.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.epm.admin.breakglass.BreakGlassService;
import dev.epm.admin.delegation.DelegationService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Task 30 privileged-access control: scoped time-bound delegation with non-transferability,
 * original-owner attribution, automatic expiry, and out-of-scope denial; dual-approved,
 * least-scope, 60-minute break-glass with early revocation, always-denied financial actions, and
 * independent 24-hour review.
 */
class BreakGlassTest {

    private final DelegationService delegation = new DelegationService();
    private final BreakGlassService breakGlass = new BreakGlassService();

    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 11, 3, 12, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void delegationIsScopedTimeBoundAndAttributedToTheOriginalOwner() {
        DelegationService.Delegation grant =
                delegation.grant(
                        "owner@example.invalid",
                        "delegate@example.invalid",
                        "ENT101",
                        "PREPARE",
                        NOW.minusHours(1),
                        NOW.plusHours(1));

        DelegationService.Decision inScope =
                delegation.decide(
                        List.of(grant), "delegate@example.invalid", "PREPARE", "ENT101", NOW);
        assertThat(inScope.allowed()).isTrue();
        assertThat(inScope.reason()).contains("owner@example.invalid");

        // Out of scope: wrong entity, wrong action, wrong actor.
        assertThat(
                        delegation
                                .decide(
                                        List.of(grant),
                                        "delegate@example.invalid",
                                        "PREPARE",
                                        "ENT102",
                                        NOW)
                                .allowed())
                .isFalse();
        assertThat(
                        delegation
                                .decide(
                                        List.of(grant),
                                        "delegate@example.invalid",
                                        "APPROVE",
                                        "ENT101",
                                        NOW)
                                .allowed())
                .isFalse();
        assertThat(
                        delegation
                                .decide(
                                        List.of(grant),
                                        "other@example.invalid",
                                        "PREPARE",
                                        "ENT101",
                                        NOW)
                                .allowed())
                .isFalse();
    }

    @Test
    void delegationExpiresAutomaticallyWithoutCompletingAnything() {
        DelegationService.Delegation grant =
                delegation.grant(
                        "o@e", "d@e", "ENT101", "PREPARE", NOW.minusHours(2), NOW.plusMinutes(1));
        // One second past the window: authority is gone, no state change implied.
        assertThat(
                        delegation
                                .decide(
                                        List.of(grant),
                                        "d@e",
                                        "PREPARE",
                                        "ENT101",
                                        NOW.plusMinutes(1).plusSeconds(1))
                                .allowed())
                .isFalse();
        assertThat(delegation.decide(List.of(grant), "d@e", "PREPARE", "ENT101", NOW).allowed())
                .isTrue();
    }

    @Test
    void delegationIsNonTransferableAndEscalationNeverGrantsAuthority() {
        DelegationService.Delegation grant =
                delegation.grant(
                        "o@e",
                        "d@e",
                        "ENT101",
                        "PREPARE",
                        NOW.minusMinutes(1),
                        NOW.plusMinutes(30));
        // The delegate cannot re-delegate to a third party.
        assertThat(delegation.isTransferable(grant, "d@e")).isFalse();
        // Escalation only raises visibility: a denied action stays denied.
        DelegationService.Decision denied =
                delegation.decide(List.of(grant), "d@e", "APPROVE", "ENT101", NOW);
        assertThat(denied.allowed()).isFalse();
        assertThat(delegation.escalate(denied).allowed()).isFalse();
        assertThat(delegation.escalate(denied).reason()).contains("visibility only");
    }

    @Test
    void breakGlassRequiresDualDistinctApprovalAndJustification() {
        assertThatThrownBy(() -> breakGlass.open("r@e", "r@e", "ENT101", "why", NOW))
                .hasMessageContaining("two distinct individuals");
        assertThatThrownBy(() -> breakGlass.open("r@e", "a@e", "ENT101", " ", NOW))
                .hasMessageContaining("justification");
        BreakGlassService.Session session =
                breakGlass.open("r@e", "a@e", "ENT101", "production incident", NOW);
        assertThat(session.expiresAt()).isEqualTo(NOW.plusMinutes(60));
    }

    @Test
    void breakGlassDeniesFinancialAuthorityAndSelfApproval() {
        BreakGlassService.Session session =
                breakGlass.open("r@e", "a@e", "ENT101", "incident", NOW);
        for (String denied :
                new String[] {
                    "PAYMENT",
                    "GL_POSTING",
                    "CERTIFY",
                    "PUBLISH",
                    "AUTONOMOUS_APPROVAL",
                    "DIRECT_FINANCIAL_EDIT"
                }) {
            assertThat(breakGlass.decide(session, "r@e", denied, NOW).allowed())
                    .as(denied)
                    .isFalse();
        }
        assertThat(breakGlass.decide(session, "r@e", "READ", NOW).allowed()).isTrue();
        assertThat(breakGlass.decide(session, "r@e", "APPROVE:SELF", NOW).allowed()).isFalse();
    }

    @Test
    void expiryAndRevocationEndSessionsWithoutStandingPrivilege() {
        BreakGlassService.Session session =
                breakGlass.open("r@e", "a@e", "ENT101", "incident", NOW.minusMinutes(59));
        assertThat(session.activeAt(NOW)).isTrue();
        // Past 60 minutes: dead.
        assertThat(session.activeAt(NOW.plusMinutes(2))).isFalse();

        BreakGlassService.Session revoked = breakGlass.revoke(session, NOW.minusMinutes(30));
        assertThat(revoked.activeAt(NOW.minusMinutes(29))).isFalse();
        // No standing privilege: every action decision after revocation denies.
        assertThat(breakGlass.decide(revoked, "r@e", "READ", NOW.minusMinutes(29)).allowed())
                .isFalse();
    }

    @Test
    void independentReviewWithin24HoursByADistinctReviewer() {
        BreakGlassService.Session session =
                breakGlass.open("r@e", "a@e", "ENT101", "incident", NOW);
        assertThat(breakGlass.reviewRecorded(session, "auditor@e", NOW.plusHours(23))).isTrue();
        assertThat(breakGlass.reviewRecorded(session, "r@e", NOW.plusHours(1))).isFalse();
        assertThat(breakGlass.reviewRecorded(session, "auditor@e", NOW.plusHours(25))).isFalse();
    }
}
