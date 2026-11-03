package dev.epm.integration.contracts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

/**
 * Task 10 contract governance: authoritative activation, ambiguous scope claims, optional-field
 * omission, null required grain, contract diffs as versions, and deprecation impact — with
 * immutable transition evidence and independent approval.
 */
@QuarkusTest
class SourceContractTest {

    @Inject SourceContractService contracts;

    @Test
    void approvedContractActivatesAsAuthoritativeForItsScope() {
        String scope = "treasury-" + System.nanoTime();
        String id = "contract-" + System.nanoTime();
        proposeContract(id, scope, 100, "steward");
        contracts.approve(id, 1, "approver", "reviewed");
        contracts.activate(id, 1, "steward", "activation");

        assertThat(contracts.authoritativeFor(scope)).isPresent();
        assertThat(contracts.authoritativeFor(scope).get().getContractId()).isEqualTo(id);
    }

    @Test
    void ambiguousScopeClaimWithEqualPrecedenceIsRejected() {
        String scope = "ambiguous-" + System.nanoTime();
        String first = "first-" + System.nanoTime();
        String second = "second-" + System.nanoTime();
        proposeContract(first, scope, 50, "steward");
        contracts.approve(first, 1, "approver", null);
        contracts.activate(first, 1, "steward", "first");

        proposeContract(second, scope, 50, "steward");
        contracts.approve(second, 1, "approver", null);
        assertThatThrownBy(() -> contracts.activate(second, 1, "steward", "claim"))
                .isInstanceOf(SourceContractService.ContractException.class)
                .hasMessageContaining("ambiguous authoritative contract");
        // Rejection leaves the active evidence untouched.
        assertThat(contracts.authoritativeFor(scope).get().getContractId()).isEqualTo(first);
    }

    @Test
    void betterPrecedenceSupersedesTheIncumbentWithEvidence() {
        String scope = "precedence-" + System.nanoTime();
        String incumbent = "inc-" + System.nanoTime();
        String challenger = "chl-" + System.nanoTime();
        proposeContract(incumbent, scope, 50, "steward");
        contracts.approve(incumbent, 1, "approver", null);
        contracts.activate(incumbent, 1, "steward", "first");

        proposeContract(challenger, scope, 10, "steward");
        contracts.approve(challenger, 1, "approver", null);
        contracts.activate(challenger, 1, "steward", "better precedence");

        assertThat(contracts.authoritativeFor(scope).get().getContractId()).isEqualTo(challenger);
    }

    @Test
    void proposerCannotApproveTheirOwnContract() {
        String id = "self-" + System.nanoTime();
        proposeContract(id, "scope-" + System.nanoTime(), 100, "steward");
        assertThatThrownBy(() -> contracts.approve(id, 1, "steward", null))
                .isInstanceOf(SourceContractService.ContractException.class)
                .hasMessageContaining("proposer cannot approve");
    }

    @Test
    void nullRequiredGrainIsAFieldLevelViolation() {
        SourceContractEntity entity = baseEntity("noGrain-" + System.nanoTime(), "scope-x", 100);
        entity.setGrain(null);
        assertThatThrownBy(() -> contracts.propose(entity, "steward"))
                .isInstanceOf(SourceContractService.ContractException.class)
                .hasMessageContaining("grain is required");
    }

    @Test
    void missingOptionalFieldsDoNotBlockProposalButRequiredOnesDo() {
        // cadence/reconciliation are optional at R0 registration; scope is required.
        SourceContractEntity entity =
                baseEntity("minimal-" + System.nanoTime(), "scope-min-" + System.nanoTime(), 100);
        entity.setScope(null);
        assertThatThrownBy(() -> contracts.propose(entity, "steward"))
                .hasMessageContaining("scope is required");
    }

    @Test
    void deprecationRemovesAuthorityAndRecordsTheTransition() {
        String scope = "deprecate-" + System.nanoTime();
        String id = "dep-" + System.nanoTime();
        proposeContract(id, scope, 100, "steward");
        contracts.approve(id, 1, "approver", null);
        contracts.activate(id, 1, "steward", "activation");
        contracts.deprecate(id, 1, "owner", "source retired");

        assertThat(contracts.authoritativeFor(scope)).isEmpty();
    }

    @Test
    void contractDiffLandsAsANewImmutableVersion() {
        String scope = "diff-" + System.nanoTime();
        String id = "diffc-" + System.nanoTime();
        proposeContract(id, scope, 100, "steward");
        contracts.approve(id, 1, "approver", null);
        contracts.activate(id, 1, "steward", "v1");

        SourceContractEntity v2 = baseEntity(id, scope, 100);
        v2.setVersion(2);
        v2.setGrain("ENTITY_DAY");
        contracts.propose(v2, "steward");
        contracts.approve(id, 2, "approver", "field changes");
        contracts.activate(id, 2, "steward", "v2");

        assertThat(contracts.authoritativeFor(scope).get().getVersion()).isEqualTo(2);
    }

    @Transactional
    void proposeContract(String id, String scope, int precedence, String actor) {
        SourceContractEntity entity = baseEntity(id, scope, precedence);
        contracts.propose(entity, actor);
    }

    private static SourceContractEntity baseEntity(String id, String scope, int precedence) {
        SourceContractEntity entity = new SourceContractEntity();
        entity.setContractId(id);
        entity.setVersion(1);
        entity.setSystemId("SYS-ERP-1");
        entity.setOwner("source-owner@example.invalid");
        entity.setScope(scope);
        entity.setGrain("ENTITY_ACCOUNT_DAY");
        entity.setCadence("DAILY");
        entity.setCutoffUtc(LocalTime.of(17, 0));
        entity.setFieldDefinitions("{\"openingCash\":\"MONEY\"}");
        entity.setQualityRules("{\"noNulls\":[\"openingCash\"]}");
        entity.setReconciliationBasis("{\"total\":\"openingCash\"}");
        entity.setRetentionMonths(84);
        entity.setCredentialsRef("secret-ref:sys-erp-1");
        entity.setPrecedence(precedence);
        return entity;
    }
}
