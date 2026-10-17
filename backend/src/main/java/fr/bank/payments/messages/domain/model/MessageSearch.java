// createdBy ELMANSOURI Mohamed
package fr.bank.payments.messages.domain.model;

import java.time.Instant;

public record MessageSearch(
    MessageStatus status,
    String source,
    String correlationId,
    String mqMessageId,
    Instant from,
    Instant to,
    Long beforeId,
    int limit) {
  public MessageSearch {
    if (limit < 1 || limit > 100)
      throw new IllegalArgumentException("La taille de page doit être comprise entre 1 et 100");
    if (beforeId != null && beforeId < 1)
      throw new IllegalArgumentException("Le curseur doit être positif");
    if (from != null && to != null && !from.isBefore(to))
      throw new IllegalArgumentException("La date de début doit précéder la date de fin");
  }
}
