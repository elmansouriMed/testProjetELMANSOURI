// createdBy ELMANSOURI Mohamed
package fr.bank.payments.messages.infrastructure.adapter.out.persistence;

import fr.bank.payments.messages.domain.model.*;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "payment_message")
public class MessageEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // Métadonnée JPA interne : protège les écritures concurrentes, sans alourdir les DTO REST.
  @Version
  @Column(nullable = false)
  private long version;

  @Column(name = "mq_message_id", nullable = false)
  private String mqMessageId;

  @Column(name = "correlation_id")
  private String correlationId;

  @Column(name = "source_application", nullable = false, length = 128)
  private String sourceApplication;

  @Column(name = "source_queue", nullable = false)
  private String sourceQueue;

  @Column(name = "received_at", nullable = false)
  private Instant receivedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "body_type", nullable = false, length = 16)
  private BodyType bodyType;

  private byte[] payload;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private MessageStatus status;

  @Column(name = "rejection_reason")
  private String rejectionReason;

  protected MessageEntity() {}

  MessageDetail toDetail() {
    return new MessageDetail(
        new MessageSummary(
            id,
            mqMessageId,
            correlationId,
            sourceApplication,
            sourceQueue,
            receivedAt,
            bodyType,
            status,
            rejectionReason),
        payload);
  }
}
