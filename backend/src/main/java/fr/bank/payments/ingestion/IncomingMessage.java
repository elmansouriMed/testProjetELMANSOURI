package fr.bank.payments.ingestion;

import java.time.Instant;

public record IncomingMessage(String mqMessageId, String correlationId, String sourceApplication,
        String sourceQueue, Instant sentAt, String bodyType, byte[] payload, long payloadSize,
        boolean redelivered, String status, String rejectionReason) {}
