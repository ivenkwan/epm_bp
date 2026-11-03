package dev.epm.admin.authorization;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** SoD constraint row in {@code epm_sod_constraint}. */
@Entity
@Table(name = "epm_sod_constraint")
public class SodConstraintEntity extends PanacheEntityBase {

    @jakarta.persistence.Id
    @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    @jakarta.persistence.Column(name = "sod_id")
    private Long sodId;

    @jakarta.persistence.Column(name = "policy_id")
    private Integer policyId;

    @jakarta.persistence.Column(name = "role_a")
    private String roleA;

    @jakarta.persistence.Column(name = "role_b")
    private String roleB;

    private String reason;

    public Integer getPolicyId() {
        return policyId;
    }

    public String getRoleA() {
        return roleA;
    }

    public String getRoleB() {
        return roleB;
    }

    public String getReason() {
        return reason;
    }

    public static long countConflicts(Integer policyId, String roleA, String roleB) {
        return find(
                        "policyId = ?1 and ((roleA = ?2 and roleB = ?3) or (roleA = ?3 and roleB ="
                                + " ?2))",
                        policyId,
                        roleA,
                        roleB)
                .count();
    }
}
