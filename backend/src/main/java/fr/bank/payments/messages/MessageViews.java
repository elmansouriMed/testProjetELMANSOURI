package fr.bank.payments.messages;

import java.time.Instant;
import java.util.List;

public final class MessageViews {
    private MessageViews() {}
    public record Summary(String id, String mqMessageId, String correlationId, String sourceApplication,
            String sourceQueue, Instant sentAt, Instant receivedAt, String bodyType,
            long payloadSize, boolean redelivered, String status, String rejectionReason) {}
    public record Detail(Summary metadata, String textPayload, String base64Payload) {}
    public record Page(List<Summary> items, String nextCursor, boolean hasMore) {}
    public record Statistics(long received24h, long quarantined24h, long received5m,
            long bytes24h, Instant sampledAt, boolean mqEnabled, boolean listenerRunning, boolean recovering) {}
    public record Filters(String status, String source, String correlationId, String mqMessageId,
            Instant from, Instant to, Long beforeId, int limit) {}
}
