package dev.epm.admin.authorization;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Versioned policy row in {@code epm_policy}; exactly one row is active. */
@Entity
@Table(name = "epm_policy")
public class PolicyEntity extends PanacheEntityBase {

    @Id
    @jakarta.persistence.Column(name = "policy_id")
    private Integer policyId;

    private String version;
    private boolean active;
    private String description;

    public Integer getPolicyId() {
        return policyId;
    }

    public String getVersion() {
        return version;
    }

    public static Integer activePolicyId() {
        PolicyEntity active = find("active", true).firstResult();
        return active == null ? null : active.policyId;
    }
}
