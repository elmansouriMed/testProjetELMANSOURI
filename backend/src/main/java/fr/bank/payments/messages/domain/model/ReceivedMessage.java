// createdBy ELMANSOURI Mohamed
package fr.bank.payments.messages.domain.model;

import java.time.Instant;
import java.util.Objects;

public record ReceivedMessage(
    String mqMessageId,
    String correlationId,
    String sourceApplication,
    String sourceQueue,
    Instant sentAt,
    BodyType bodyType,
    byte[] payload,
    long payloadSize,
    boolean redelivered,
    MessageStatus status,
    String rejectionReason) {
  public ReceivedMessage {
    if (mqMessageId == null || mqMessageId.isBlank())
      throw new IllegalArgumentException("Identifiant MQ obligatoire");
    Objects.requireNonNull(sourceApplication);
    Objects.requireNonNull(sourceQueue);
    Objects.requireNonNull(bodyType);
    Objects.requireNonNull(status);
    if (payloadSize < 0) throw new IllegalArgumentException("Taille négative");
    if (status == MessageStatus.STORED && payload == null)
      throw new IllegalArgumentException("Contenu obligatoire pour un message archivé");
    if (status == MessageStatus.QUARANTINED
        && (rejectionReason == null || rejectionReason.isBlank()))
      throw new IllegalArgumentException("Motif de quarantaine obligatoire");
    payload = payload == null ? null : payload.clone();
  }

  @Override
  public byte[] payload() {
    return payload == null ? null : payload.clone();
  }
}
