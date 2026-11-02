package dev.epm.admin.authorization;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;

/** Role assignment row in {@code epm_user_role}. */
@Entity
@Table(name = "epm_user_role")
@IdClass(UserRoleEntity.UserRoleId.class)
public class UserRoleEntity extends PanacheEntityBase {

    @Id
    @jakarta.persistence.Column(name = "user_id")
    private String userId;

    @Id
    @jakarta.persistence.Column(name = "role_id")
    private String roleId;

    @jakarta.persistence.Column(name = "granted_by")
    private String grantedBy;

    @jakarta.persistence.Column(name = "granted_at_utc")
    private OffsetDateTime grantedAtUtc;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }

    public String getGrantedBy() {
        return grantedBy;
    }

    public void setGrantedBy(String grantedBy) {
        this.grantedBy = grantedBy;
    }

    public OffsetDateTime getGrantedAtUtc() {
        return grantedAtUtc;
    }

    public void setGrantedAtUtc(OffsetDateTime grantedAtUtc) {
        this.grantedAtUtc = grantedAtUtc;
    }

    public static UserRoleEntity findById(UserRoleId id) {
        return find("userId = ?1 and roleId = ?2", id.userId, id.roleId).firstResult();
    }

    /** Composite key for role grants. */
    public static class UserRoleId implements Serializable {
        private static final long serialVersionUID = 1L;

        private String userId;
        private String roleId;

        public UserRoleId() {
            // JPA
        }

        public UserRoleId(String userId, String roleId) {
            this.userId = userId;
            this.roleId = roleId;
        }

        @Override
        public boolean equals(Object other) {
            if (other == null || getClass() != other.getClass()) {
                return false;
            }
            UserRoleId that = (UserRoleId) other;
            return Objects.equals(userId, that.userId) && Objects.equals(roleId, that.roleId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, roleId);
        }
    }
}
