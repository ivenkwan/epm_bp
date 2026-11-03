package dev.epm.core.outbox;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import javax.sql.DataSource;

/**
 * Transactional outbox. Producers append events in the same transaction as the business state (no
 * event is ever visible before its commit); the dispatcher claims pending rows with SKIP LOCKED,
 * delivers to registered consumers, and records dispatch state.
 *
 * <p>Redelivery is safe: the stable event id lets consumers deduplicate, and consumers must fetch
 * authoritative state through APIs rather than trusting the payload.
 */
@ApplicationScoped
public class OutboxService {

    private final DataSource dataSource;
    private final Set<UUID> deliveredEventIds = ConcurrentHashMap.newKeySet();
    private final List<Consumer<OutboxEvent>> consumers = new ArrayList<>();

    @Inject
    OutboxService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Registers a delivery target; delivery is exactly-once per registered consumer set. */
    public synchronized void addConsumer(Consumer<OutboxEvent> consumer) {
        consumers.add(consumer);
    }

    /**
     * Appends an outbox row in the caller's transaction. Callers must invoke this on the same
     * transaction as the business mutation; a rollback removes the row, which is exactly the
     * crash-before-dispatch guarantee.
     */
    public UUID append(
            String aggregateType,
            String aggregateId,
            String eventType,
            String payloadJson,
            String correlationId) {
        UUID eventId = UUID.randomUUID();
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps =
                        c.prepareStatement(
                                "INSERT INTO epm_outbox (event_id, aggregate_type, aggregate_id, "
                                        + "event_type, payload, correlation_id, occurred_at_utc) "
                                        + "VALUES (?, ?, ?, ?, ?::jsonb, ?, now())")) {
            ps.setObject(1, eventId);
            ps.setString(2, aggregateType);
            ps.setString(3, aggregateId);
            ps.setString(4, eventType);
            ps.setString(5, payloadJson);
            ps.setString(6, correlationId);
            ps.executeUpdate();
            return eventId;
        } catch (SQLException e) {
            throw new OutboxException("append failed", e);
        }
    }

    /**
     * Claims and delivers up to {@code limit} pending events. Events already delivered in this
     * process (redelivery) are acknowledged but not delivered twice.
     *
     * @return the number of events newly delivered
     */
    public int dispatchPending(int limit) {
        List<OutboxEvent> batch = new ArrayList<>();
        String claim =
                "SELECT event_id, aggregate_type, aggregate_id, event_type, payload::text, "
                        + "correlation_id, occurred_at_utc FROM epm_outbox "
                        + "WHERE dispatched_at_utc IS NULL "
                        + "ORDER BY occurred_at_utc LIMIT ? FOR UPDATE SKIP LOCKED";
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(claim)) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        batch.add(
                                new OutboxEvent(
                                        UUID.fromString(rs.getString(1)),
                                        rs.getString(2),
                                        rs.getString(3),
                                        rs.getString(4),
                                        rs.getString(5),
                                        rs.getString(6),
                                        rs.getObject(7, java.time.OffsetDateTime.class)));
                    }
                }
            }
            int delivered = 0;
            for (OutboxEvent event : batch) {
                if (deliveredEventIds.contains(event.eventId())) {
                    markDispatched(c, event.eventId(), null);
                    continue;
                }
                try {
                    List<Consumer<OutboxEvent>> snapshot;
                    synchronized (this) {
                        snapshot = List.copyOf(consumers);
                    }
                    for (Consumer<OutboxEvent> consumer : snapshot) {
                        consumer.accept(event);
                    }
                    deliveredEventIds.add(event.eventId());
                    markDispatched(c, event.eventId(), null);
                    delivered++;
                } catch (RuntimeException deliveryFailure) {
                    markFailed(c, event.eventId(), deliveryFailure.getMessage());
                }
            }
            c.commit();
            return delivered;
        } catch (SQLException e) {
            throw new OutboxException("dispatchPending failed", e);
        }
    }

    /** Forces a redelivery of an already-dispatched event to test consumer deduplication. */
    public void resetDispatched(UUID eventId) {
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps =
                        c.prepareStatement(
                                "UPDATE epm_outbox SET dispatched_at_utc = NULL, "
                                        + "dispatch_attempts = 0 WHERE event_id = ?")) {
            ps.setObject(1, eventId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new OutboxException("resetDispatched failed", e);
        }
    }

    /** Number of rows that committed but have not been dispatched yet. */
    public long pendingCount() {
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps =
                        c.prepareStatement(
                                "SELECT count(*) FROM epm_outbox WHERE dispatched_at_utc IS NULL");
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new OutboxException("pendingCount failed", e);
        }
    }

    public Optional<OutboxEvent> byId(UUID eventId) {
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps =
                        c.prepareStatement(
                                "SELECT event_id, aggregate_type, aggregate_id, event_type, "
                                        + "payload::text, correlation_id, occurred_at_utc, "
                                        + "dispatched_at_utc FROM epm_outbox WHERE event_id = ?")) {
            ps.setObject(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(
                        new OutboxEvent(
                                UUID.fromString(rs.getString(1)),
                                rs.getString(2),
                                rs.getString(3),
                                rs.getString(4),
                                rs.getString(5),
                                rs.getString(6),
                                rs.getObject(7, java.time.OffsetDateTime.class)));
            }
        } catch (SQLException e) {
            throw new OutboxException("byId failed", e);
        }
    }

    private static void markDispatched(Connection c, UUID eventId, String unusedError)
            throws SQLException {
        try (PreparedStatement ps =
                c.prepareStatement(
                        "UPDATE epm_outbox SET dispatched_at_utc = now(), "
                                + "dispatch_attempts = dispatch_attempts + 1, last_error = NULL "
                                + "WHERE event_id = ?")) {
            ps.setObject(1, eventId);
            ps.executeUpdate();
        }
    }

    private static void markFailed(Connection c, UUID eventId, String error) throws SQLException {
        try (PreparedStatement ps =
                c.prepareStatement(
                        "UPDATE epm_outbox SET dispatch_attempts = dispatch_attempts + 1, "
                                + "last_error = ? WHERE event_id = ?")) {
            ps.setString(
                    1, error == null ? null : error.substring(0, Math.min(500, error.length())));
            ps.setObject(2, eventId);
            ps.executeUpdate();
        }
    }

    /** Outbox event envelope; payload is advisory - consumers refetch authoritative state. */
    public record OutboxEvent(
            UUID eventId,
            String aggregateType,
            String aggregateId,
            String eventType,
            String payloadJson,
            String correlationId,
            java.time.OffsetDateTime occurredAtUtc) {}

    /** Wraps SQL failures from the outbox helpers. */
    public static final class OutboxException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        OutboxException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
