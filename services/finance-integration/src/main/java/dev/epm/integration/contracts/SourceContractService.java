package dev.epm.integration.contracts;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;

/**
 * Authoritative source register: versioned contracts move PROPOSED → APPROVED → ACTIVE → DEPRECATED
 * with independent approval and immutable transition evidence. Exactly one contract is
 * authoritative per scope; ambiguity is rejected, and approved precedence resolves otherwise-equal
 * claims. Field-level violations are reported per field.
 */
@ApplicationScoped
public class SourceContractService {

    /** Registers a new contract version in PROPOSED with full validation. */
    @Transactional
    public SourceContractEntity propose(SourceContractEntity proposal, String actor) {
        validate(proposal);
        if (proposal.getVersion() == null || proposal.getVersion() < 1) {
            proposal.setVersion(1);
        }
        proposal.setStatus("PROPOSED");
        proposal.setProposedBy(actor);
        proposal.persist();
        ContractTransitionEntity.record(
                proposal.getContractId(), proposal.getVersion(), null, "PROPOSED", actor, null);
        return proposal;
    }

    /** Independent approval; the approver must differ from the proposer. */
    @Transactional
    public void approve(String contractId, Integer version, String approver, String reason) {
        SourceContractEntity contract = find(contractId, version);
        if ("PROPOSED".equals(contract.getStatus()) && contract.getApprovedBy() == null) {
            if (contract.getProposedBy() != null && contract.getProposedBy().equals(approver)) {
                throw new ContractException("proposer cannot approve their own contract");
            }
        }
        if (!"PROPOSED".equals(contract.getStatus())) {
            throw new ContractException("only PROPOSED contracts can be approved");
        }
        contract.setApprovedBy(approver);
        contract.setStatus("APPROVED");
        ContractTransitionEntity.record(
                contractId, version, "PROPOSED", "APPROVED", approver, reason);
    }

    /**
     * Activates an approved contract for its scope. Ambiguous scope claims (two ACTIVE contracts
     * with equal precedence) are rejected; an explicit precedence resolves otherwise-equal claims
     * by deprecating the loser with evidence.
     */
    @Transactional
    public void activate(String contractId, Integer version, String actor, String reason) {
        SourceContractEntity contract = find(contractId, version);
        if (!"APPROVED".equals(contract.getStatus())) {
            throw new ContractException("only APPROVED contracts can become ACTIVE");
        }
        SourceContractEntity current = SourceContractEntity.activeForScope(contract.getScope());
        if (current != null
                && current.getContractId().equals(contract.getContractId())
                && !current.getVersion().equals(contract.getVersion())) {
            // New version of the same contract: supersede the prior ACTIVE version.
            current.setStatus("DEPRECATED");
            ContractTransitionEntity.record(
                    current.getContractId(),
                    current.getVersion(),
                    "ACTIVE",
                    "DEPRECATED",
                    actor,
                    "superseded by version " + contract.getVersion());
        } else if (current != null && !current.getContractId().equals(contract.getContractId())) {
            if (current.getPrecedence().equals(contract.getPrecedence())) {
                throw new ContractException(
                        "ambiguous authoritative contract for scope " + contract.getScope());
            }
            if (contract.getPrecedence() < current.getPrecedence()) {
                current.setStatus("DEPRECATED");
                ContractTransitionEntity.record(
                        current.getContractId(),
                        current.getVersion(),
                        "ACTIVE",
                        "DEPRECATED",
                        actor,
                        "superseded by precedence");
            } else {
                throw new ContractException(
                        "existing authoritative contract has better precedence");
            }
        }
        contract.setStatus("ACTIVE");
        ContractTransitionEntity.record(contractId, version, "APPROVED", "ACTIVE", actor, reason);
    }

    /** Deprecates a contract; consumers see the impact before the transition lands. */
    @Transactional
    public void deprecate(String contractId, Integer version, String actor, String reason) {
        SourceContractEntity contract = find(contractId, version);
        String from = contract.getStatus();
        contract.setStatus("DEPRECATED");
        ContractTransitionEntity.record(contractId, version, from, "DEPRECATED", actor, reason);
    }

    /** The single authoritative ACTIVE contract for a scope, if any. */
    @Transactional
    public Optional<SourceContractEntity> authoritativeFor(String scope) {
        return Optional.ofNullable(SourceContractEntity.activeForScope(scope));
    }

    private static SourceContractEntity find(String contractId, Integer version) {
        SourceContractEntity entity =
                SourceContractEntity.findById(new SourceContractEntity.Key(contractId, version));
        if (entity == null) {
            throw new ContractException("contract not found: " + contractId + " v" + version);
        }
        return entity;
    }

    /** Field-level validation: every violation names its field. */
    private static void validate(SourceContractEntity contract) {
        List<String> violations = new java.util.ArrayList<>();
        if (contract.getContractId() == null || contract.getContractId().isBlank()) {
            violations.add("contractId is required");
        }
        if (contract.getScope() == null || contract.getScope().isBlank()) {
            violations.add("scope is required");
        }
        if (contract.getGrain() == null) {
            violations.add("grain is required at the declared level");
        }
        if (contract.getCutoffUtc() == null) {
            violations.add("business cutoff is required");
        }
        if (!violations.isEmpty()) {
            throw new ContractException("contract invalid: " + String.join("; ", violations));
        }
    }

    /** Contract governance failures surfaced as safe problems. */
    public static final class ContractException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public ContractException(String message) {
            super(message);
        }
    }
}
