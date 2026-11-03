package dev.epm.admin.authorization;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Authority-matrix permission row in {@code epm_permission}. */
@Entity
@Table(name = "epm_permission")
public class PermissionEntity extends PanacheEntityBase {

    @jakarta.persistence.Id
    @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    @jakarta.persistence.Column(name = "permission_id")
    private Long permissionId;

    @jakarta.persistence.Column(name = "policy_id")
    private Integer policyId;

    @jakarta.persistence.Column(name = "role_id")
    private String roleId;

    @jakarta.persistence.Column(name = "object_type")
    private String objectType;

    private String action;

    public Long getPermissionId() {
        return permissionId;
    }

    public Integer getPolicyId() {
        return policyId;
    }

    public String getRoleId() {
        return roleId;
    }

    public String getObjectType() {
        return objectType;
    }

    public String getAction() {
        return action;
    }

    public static long countMatching(
            Integer policyId, String roleId, String objectType, String action) {
        return find(
                        "policyId = ?1 and roleId = ?2 and objectType = ?3 and action = ?4",
                        policyId,
                        roleId,
                        objectType,
                        action)
                .count();
    }
}
