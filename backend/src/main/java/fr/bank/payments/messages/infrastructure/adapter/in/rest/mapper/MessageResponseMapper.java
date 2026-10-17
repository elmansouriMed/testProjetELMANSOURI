// createdBy ELMANSOURI Mohamed
package fr.bank.payments.messages.infrastructure.adapter.in.rest.mapper;

import fr.bank.payments.messages.domain.model.BodyType;
import fr.bank.payments.messages.domain.model.MessageDetail;
import fr.bank.payments.messages.domain.model.MessagePage;
import fr.bank.payments.messages.domain.model.MessageSummary;
import fr.bank.payments.messages.infrastructure.adapter.in.rest.dto.MessageResponses;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class MessageResponseMapper {
  private MessageResponseMapper() {}

  public static MessageResponses.Summary summary(MessageSummary message) {
    return new MessageResponses.Summary(
        Long.toString(message.id()),
        message.mqMessageId(),
        message.correlationId(),
        message.sourceApplication(),
        message.sourceQueue(),
        message.sentAt(),
        message.receivedAt(),
        message.bodyType(),
        message.payloadSize(),
        message.redelivered(),
        message.status(),
        message.rejectionReason());
  }

  public static MessageResponses.Page page(MessagePage page) {
    return new MessageResponses.Page(
        page.items().stream().map(MessageResponseMapper::summary).toList(),
        page.nextCursor() == null ? null : page.nextCursor().toString(),
        page.hasMore());
  }

  public static MessageResponses.Detail detail(MessageDetail message) {
    byte[] payload = message.payload();
    BodyType type = message.metadata().bodyType();
    return new MessageResponses.Detail(
        summary(message.metadata()),
        payload != null && type == BodyType.TEXT
            ? new String(payload, StandardCharsets.UTF_8)
            : null,
        payload != null && type == BodyType.BYTES
            ? Base64.getEncoder().encodeToString(payload)
            : null);
  }
}
