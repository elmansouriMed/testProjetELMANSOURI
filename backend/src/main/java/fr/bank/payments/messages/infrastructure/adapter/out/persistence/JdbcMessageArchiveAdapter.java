// createdBy ELMANSOURI Mohamed
package fr.bank.payments.messages.infrastructure.adapter.out.persistence;

import fr.bank.payments.messages.application.port.out.MessageArchivePort;
import fr.bank.payments.messages.domain.model.BodyType;
import fr.bank.payments.messages.domain.model.MessageDetail;
import fr.bank.payments.messages.domain.model.MessagePage;
import fr.bank.payments.messages.domain.model.MessageSearch;
import fr.bank.payments.messages.domain.model.MessageStatus;
import fr.bank.payments.messages.domain.model.MessageSummary;
import fr.bank.payments.messages.domain.model.ReceivedMessage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcMessageArchiveAdapter implements MessageArchivePort {
  private static final String COLUMNS =
      "id,mq_message_id,correlation_id,source_application,source_queue,sent_at,received_at,body_type,payload_size,redelivered,status,rejection_reason";
  private final NamedParameterJdbcTemplate jdbc;

  public JdbcMessageArchiveAdapter(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean insert(ReceivedMessage message) {
    // Une contrainte SQL protège aussi contre les doublons reçus par plusieurs instances.
    return jdbc.update(
            """
            INSERT INTO payment_message(mq_message_id,correlation_id,source_application,source_queue,
              sent_at,body_type,payload,payload_size,redelivered,status,rejection_reason)
            VALUES (:mqId,:correlation,:source,:queue,:sent,:type,:payload,:size,:redelivered,:status,:reason)
            ON CONFLICT (source_queue,mq_message_id) DO NOTHING
            """,
            new MapSqlParameterSource()
                .addValue("mqId", message.mqMessageId())
                .addValue("correlation", message.correlationId())
                .addValue("source", message.sourceApplication())
                .addValue("queue", message.sourceQueue())
                .addValue("sent", timestamp(message.sentAt()))
                .addValue("type", message.bodyType().name())
                .addValue("payload", message.payload(), Types.BINARY)
                .addValue("size", message.payloadSize())
                .addValue("redelivered", message.redelivered())
                .addValue("status", message.status().name())
                .addValue("reason", message.rejectionReason()))
        == 1;
  }

  @Override
  public MessagePage search(MessageSearch search) {
    StringBuilder sql = new StringBuilder("SELECT " + COLUMNS + " FROM payment_message WHERE 1=1");
    var parameters = new MapSqlParameterSource();
    if (search.beforeId() != null) {
      sql.append(" AND id < :before");
      parameters.addValue("before", search.beforeId());
    }
    exact(
        sql,
        parameters,
        "status",
        "status",
        search.status() == null ? null : search.status().name());
    exact(sql, parameters, "source_application", "source", search.source());
    exact(sql, parameters, "correlation_id", "correlation", search.correlationId());
    exact(sql, parameters, "mq_message_id", "mqId", search.mqMessageId());
    if (search.from() != null) {
      sql.append(" AND received_at >= :from");
      parameters.addValue("from", timestamp(search.from()));
    }
    if (search.to() != null) {
      sql.append(" AND received_at < :to");
      parameters.addValue("to", timestamp(search.to()));
    }
    sql.append(" ORDER BY id DESC LIMIT :limit");
    parameters.addValue("limit", search.limit() + 1);
    var rows = jdbc.query(sql.toString(), parameters, (rs, index) -> summary(rs));
    boolean hasMore = rows.size() > search.limit();
    var items = List.copyOf(rows.subList(0, Math.min(rows.size(), search.limit())));
    return new MessagePage(items, hasMore ? items.getLast().id() : null, hasMore);
  }

  @Override
  public Optional<MessageDetail> findById(long id) {
    return jdbc
        .query(
            "SELECT " + COLUMNS + ",payload FROM payment_message WHERE id=:id",
            new MapSqlParameterSource("id", id),
            (rs, index) -> new MessageDetail(summary(rs), rs.getBytes("payload")))
        .stream()
        .findFirst();
  }

  private static void exact(
      StringBuilder sql,
      MapSqlParameterSource parameters,
      String column,
      String key,
      String value) {
    if (value != null && !value.isBlank()) {
      sql.append(" AND ").append(column).append(" = :").append(key);
      parameters.addValue(key, value);
    }
  }

  private static Timestamp timestamp(Instant value) {
    return value == null ? null : Timestamp.from(value);
  }

  private static Instant instant(ResultSet rs, String column) throws SQLException {
    Timestamp value = rs.getTimestamp(column);
    return value == null ? null : value.toInstant();
  }

  private static MessageSummary summary(ResultSet rs) throws SQLException {
    return new MessageSummary(
        rs.getLong("id"),
        rs.getString("mq_message_id"),
        rs.getString("correlation_id"),
        rs.getString("source_application"),
        rs.getString("source_queue"),
        instant(rs, "sent_at"),
        instant(rs, "received_at"),
        BodyType.valueOf(rs.getString("body_type")),
        rs.getLong("payload_size"),
        rs.getBoolean("redelivered"),
        MessageStatus.valueOf(rs.getString("status")),
        rs.getString("rejection_reason"));
  }
}
