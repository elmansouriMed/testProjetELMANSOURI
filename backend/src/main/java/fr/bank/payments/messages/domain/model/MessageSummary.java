// createdBy ELMANSOURI Mohamed
package fr.bank.payments.messages.domain.model;

import java.time.Instant;

public record MessageSummary(
    long id,
    String mqMessageId,
    String correlationId,
    String sourceApplication,
    String sourceQueue,
    Instant receivedAt,
    BodyType bodyType,
    MessageStatus status,
    String rejectionReason) {}
