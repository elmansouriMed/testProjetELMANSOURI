package fr.bank.payments.messages;

import fr.bank.payments.messages.MessageViews.*;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.Instant;

@RestController
@RequestMapping("/api/messages")
public class MessageController {
    private final MessageRepository repository;
    private final StatisticsService statistics;
    public MessageController(MessageRepository repository, StatisticsService statistics) {
        this.repository = repository; this.statistics = statistics;
    }

    @GetMapping
    public Page list(
            @RequestParam(required=false) @Pattern(regexp="STORED|QUARANTINED") String status,
            @RequestParam(required=false) @Size(max=128) String source,
            @RequestParam(required=false) @Size(max=512) String correlationId,
            @RequestParam(required=false) @Size(max=512) String mqMessageId,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required=false) @Positive Long beforeId,
            @RequestParam(defaultValue="25") @Min(1) @Max(100) int limit) {
        if (from != null && to != null && !from.isBefore(to))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La date de début doit précéder la date de fin");
        return repository.find(new Filters(status, source, correlationId, mqMessageId, from, to, beforeId, limit));
    }
    @GetMapping("/{id}")
    public Detail detail(@PathVariable @Positive long id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message introuvable"));
    }
    @GetMapping("/statistics")
    public Statistics statistics() { return statistics.get(); }
}
