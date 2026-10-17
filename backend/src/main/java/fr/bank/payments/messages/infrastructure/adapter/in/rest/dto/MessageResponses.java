// createdBy ELMANSOURI Mohamed
package fr.bank.payments.messages.infrastructure.adapter.in.rest.dto;

import fr.bank.payments.messages.domain.model.BodyType;
import fr.bank.payments.messages.domain.model.MessageStatus;
import java.time.Instant;
import java.util.List;

public final class MessageResponses {
  private MessageResponses() {}

  public record Summary(
      String id,
      String mqMessageId,
      String correlationId,
      String sourceApplication,
      String sourceQueue,
      Instant sentAt,
      Instant receivedAt,
      BodyType bodyType,
      long payloadSize,
      boolean redelivered,
      MessageStatus status,
      String rejectionReason) {}

  public record Detail(Summary metadata, String textPayload, String base64Payload) {}

  public record Page(List<Summary> items, String nextCursor, boolean hasMore) {}
}
