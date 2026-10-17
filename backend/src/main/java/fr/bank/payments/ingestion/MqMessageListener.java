package fr.bank.payments.ingestion;

import fr.bank.payments.config.MqProperties;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.jms.*;
import org.springframework.jms.listener.SessionAwareMessageListener;
import org.springframework.stereotype.Component;

@Component
public class MqMessageListener implements SessionAwareMessageListener<Message> {
    private final MessageDecoder decoder;
    private final MessageIngestionService ingestion;
    private final MqProperties properties;
    private final MeterRegistry meters;

    public MqMessageListener(MessageDecoder decoder, MessageIngestionService ingestion,
                             MqProperties properties, MeterRegistry meters) {
        this.decoder = decoder; this.ingestion = ingestion; this.properties = properties; this.meters = meters;
    }

    @Override
    public void onMessage(Message message, Session session) throws JMSException {
        try {
            var incoming = decoder.decode(message);
            boolean inserted = ingestion.store(incoming);
            if (incoming.status().equals("QUARANTINED")) {
                // PUT and GET participate in the same MQ transaction. A failed PUT rolls back the GET.
                try (MessageProducer producer = session.createProducer(session.createQueue(properties.quarantineQueue()))) {
                    producer.send(message, DeliveryMode.PERSISTENT, message.getJMSPriority(), 0);
                }
            }
            meters.counter("payments.messages", "result", inserted ? incoming.status().toLowerCase(java.util.Locale.ROOT) : "duplicate").increment();
        } catch (JMSException | RuntimeException ex) {
            meters.counter("payments.ingestion.failures").increment();
            // Bound retries during a storage outage; no message leaves MQ on this path.
            try { Thread.sleep(1000); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            throw ex; // Never acknowledge on a DB failure.
        }
    }
}
