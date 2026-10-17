package fr.bank.payments.messages;

import fr.bank.payments.ingestion.IncomingMessage;
import fr.bank.payments.messages.MessageViews.*;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Repository
public class MessageRepository {
    private static final String COLUMNS = "id,mq_message_id,correlation_id,source_application,source_queue,sent_at,received_at,body_type,payload_size,redelivered,status,rejection_reason";
    private final NamedParameterJdbcTemplate jdbc;
    public MessageRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean insert(IncomingMessage m) {
        return jdbc.update("""
                INSERT INTO payment_message(mq_message_id,correlation_id,source_application,source_queue,
                  sent_at,body_type,payload,payload_size,redelivered,status,rejection_reason)
                VALUES (:mqId,:correlation,:source,:queue,:sent,:type,:payload,:size,:redelivered,:status,:reason)
                ON CONFLICT (source_queue,mq_message_id) DO NOTHING
                """, new MapSqlParameterSource()
                .addValue("mqId", m.mqMessageId()).addValue("correlation", m.correlationId())
                .addValue("source", m.sourceApplication()).addValue("queue", m.sourceQueue())
                .addValue("sent", m.sentAt() == null ? null : Timestamp.from(m.sentAt()))
                .addValue("type", m.bodyType()).addValue("payload", m.payload(), Types.BINARY)
                .addValue("size", m.payloadSize()).addValue("redelivered", m.redelivered())
                .addValue("status", m.status()).addValue("reason", m.rejectionReason())) == 1;
    }

    public Page find(Filters f) {
        StringBuilder sql = new StringBuilder("SELECT " + COLUMNS + " FROM payment_message WHERE 1=1");
        var args = new MapSqlParameterSource();
        if (f.beforeId() != null) { sql.append(" AND id < :before"); args.addValue("before", f.beforeId()); }
        exact(sql, args, "status", "status", f.status());
        exact(sql, args, "source_application", "source", f.source());
        exact(sql, args, "correlation_id", "correlation", f.correlationId());
        exact(sql, args, "mq_message_id", "mqId", f.mqMessageId());
        if (f.from() != null) { sql.append(" AND received_at >= :from"); args.addValue("from", Timestamp.from(f.from())); }
        if (f.to() != null) { sql.append(" AND received_at < :to"); args.addValue("to", Timestamp.from(f.to())); }
        sql.append(" ORDER BY id DESC LIMIT :limit"); args.addValue("limit", f.limit() + 1);
        var rows = jdbc.query(sql.toString(), args, (rs, n) -> summary(rs));
        boolean hasMore = rows.size() > f.limit();
        var items = java.util.List.copyOf(rows.subList(0, Math.min(f.limit(), rows.size())));
        return new Page(items, hasMore ? items.getLast().id() : null, hasMore);
    }

    public Optional<Detail> findById(long id) {
        return jdbc.query("SELECT " + COLUMNS + ",payload FROM payment_message WHERE id=:id",
                new MapSqlParameterSource("id", id), (rs, n) -> {
                    Summary metadata = summary(rs); byte[] body = rs.getBytes("payload");
                    return new Detail(metadata,
                            body != null && metadata.bodyType().equals("TEXT") ? new String(body, StandardCharsets.UTF_8) : null,
                            body != null && metadata.bodyType().equals("BYTES") ? Base64.getEncoder().encodeToString(body) : null);
                }).stream().findFirst();
    }

    public long[] statistics(Instant sampledAt) {
        return jdbc.queryForObject("""
                SELECT count(*) AS received,
                  count(*) FILTER (WHERE status='QUARANTINED') AS quarantined,
                  count(*) FILTER (WHERE received_at >= :recent) AS recent,
                  coalesce(sum(payload_size),0) AS bytes
                FROM payment_message WHERE received_at >= :since
                """, new MapSqlParameterSource("since", Timestamp.from(sampledAt.minusSeconds(86400)))
                .addValue("recent", Timestamp.from(sampledAt.minusSeconds(300))),
                (rs, n) -> new long[] {rs.getLong("received"), rs.getLong("quarantined"), rs.getLong("recent"), rs.getLong("bytes")});
    }

    private static void exact(StringBuilder sql, MapSqlParameterSource args, String column, String key, String value) {
        if (value != null && !value.isBlank()) { sql.append(" AND ").append(column).append(" = :").append(key); args.addValue(key, value); }
    }
    private static Instant instant(ResultSet rs, String key) throws SQLException {
        var value = rs.getTimestamp(key); return value == null ? null : value.toInstant();
    }
    private static Summary summary(ResultSet rs) throws SQLException {
        return new Summary(rs.getString("id"), rs.getString("mq_message_id"), rs.getString("correlation_id"),
                rs.getString("source_application"), rs.getString("source_queue"), instant(rs,"sent_at"),
                instant(rs,"received_at"), rs.getString("body_type"), rs.getLong("payload_size"),
                rs.getBoolean("redelivered"), rs.getString("status"), rs.getString("rejection_reason"));
    }
}
