// createdBy ELMANSOURI Mohamed
package fr.bank.payments.messages.infrastructure.adapter.in.rest;

import fr.bank.payments.messages.application.port.in.ConsultMessagesUseCase;
import fr.bank.payments.messages.domain.model.MessageSearch;
import fr.bank.payments.messages.domain.model.MessageStatus;
import fr.bank.payments.messages.infrastructure.adapter.in.rest.dto.MessageResponses;
import fr.bank.payments.messages.infrastructure.adapter.in.rest.mapper.MessageResponseMapper;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/messages")
public class MessageRestAdapter {
  private final ConsultMessagesUseCase messages;
  private final MessageResponseMapper mapper;

  public MessageRestAdapter(ConsultMessagesUseCase messages, MessageResponseMapper mapper) {
    this.messages = messages;
    this.mapper = mapper;
  }

  @GetMapping
  public MessageResponses.Page list(
      @RequestParam(required = false) MessageStatus status,
      @RequestParam(required = false) @Size(max = 128) String source,
      @RequestParam(required = false) @Size(max = 512) String correlationId,
      @RequestParam(required = false) @Size(max = 512) String mqMessageId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to,
      @RequestParam(required = false) @Positive Long beforeId,
      @RequestParam(defaultValue = "25") @Min(1) @Max(100) int limit) {
    return mapper.page(
        messages.search(
            new MessageSearch(
                status, source, correlationId, mqMessageId, from, to, beforeId, limit)));
  }

  @GetMapping("/{id}")
  public MessageResponses.Detail detail(@PathVariable @Positive long id) {
    return mapper.detail(messages.findById(id));
  }
}
