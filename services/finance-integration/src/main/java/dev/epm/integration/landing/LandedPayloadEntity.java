package dev.epm.integration.landing;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Append-only original payload row in {@code epm_landed_payload}. */
@Entity
@Table(name = "epm_landed_payload")
public class LandedPayloadEntity extends PanacheEntityBase {

    @Id
    @Column(name = "payload_id")
    private UUID payloadId;

    @Column(name = "content_hash")
    private String contentHash;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "content")
    private byte[] content;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "byte_size")
    private Long byteSize;

    @Column(name = "landed_at_utc")
    private LocalDateTime landedAtUtc;

    public static LandedPayloadEntity of(byte[] content, String contentType, String hash) {
        LandedPayloadEntity entity = new LandedPayloadEntity();
        entity.payloadId = UUID.randomUUID();
        entity.content = content.clone();
        entity.contentType = contentType;
        entity.byteSize = (long) content.length;
        entity.contentHash = hash;
        entity.landedAtUtc = LocalDateTime.now(java.time.ZoneOffset.UTC);
        entity.persist();
        return entity;
    }

    public UUID getPayloadId() {
        return payloadId;
    }

    public String getContentHash() {
        return contentHash;
    }

    public byte[] getContent() {
        return content == null ? new byte[0] : content.clone();
    }

    public String getContentType() {
        return contentType;
    }

    public Long getByteSize() {
        return byteSize;
    }

    public LocalDateTime getLandedAtUtc() {
        return landedAtUtc;
    }

    /** Content-addressed duplicate lookup. */
    public static LandedPayloadEntity byHash(String hash) {
        return find("contentHash", hash).firstResult();
    }
}
