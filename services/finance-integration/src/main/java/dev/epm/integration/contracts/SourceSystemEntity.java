package dev.epm.integration.contracts;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Registered source system row in {@code epm_source_system}. */
@Entity
@Table(name = "epm_source_system")
public class SourceSystemEntity extends PanacheEntityBase {

    @Id
    @Column(name = "system_id")
    private String systemId;

    @Column(name = "system_name")
    private String systemName;

    private String owner;

    private String kind;

    public String getSystemId() {
        return systemId;
    }

    public String getKind() {
        return kind;
    }

    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }
}
