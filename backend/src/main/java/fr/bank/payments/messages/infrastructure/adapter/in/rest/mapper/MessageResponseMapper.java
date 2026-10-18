// createdBy ELMANSOURI Mohamed
package fr.bank.payments.messages.infrastructure.adapter.in.rest.mapper;

import fr.bank.payments.messages.domain.model.*;
import fr.bank.payments.messages.infrastructure.adapter.in.rest.dto.MessageResponses;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.mapstruct.*;

/** MapStruct génère les DTO ; seuls les encodages du contenu demandent une conversion explicite. */
@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface MessageResponseMapper {
  MessageResponses.Summary summary(MessageSummary message);

  MessageResponses.Page page(MessagePage page);

  @Mapping(target = "textPayload", source = "message", qualifiedByName = "textPayload")
  @Mapping(target = "base64Payload", source = "message", qualifiedByName = "base64Payload")
  MessageResponses.Detail detail(MessageDetail message);

  @Named("textPayload")
  default String textPayload(MessageDetail message) {
    byte[] payload = message.payload();
    return payload != null && message.metadata().bodyType() == BodyType.TEXT
        ? new String(payload, StandardCharsets.UTF_8)
        : null;
  }

  @Named("base64Payload")
  default String base64Payload(MessageDetail message) {
    byte[] payload = message.payload();
    return payload != null && message.metadata().bodyType() == BodyType.BYTES
        ? Base64.getEncoder().encodeToString(payload)
        : null;
  }
}
