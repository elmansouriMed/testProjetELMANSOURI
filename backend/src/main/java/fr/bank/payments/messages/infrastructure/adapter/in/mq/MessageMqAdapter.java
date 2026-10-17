// createdBy ELMANSOURI Mohamed
package fr.bank.payments.messages.infrastructure.adapter.in.mq;

import fr.bank.payments.messages.application.port.in.IngestMessageUseCase;
import fr.bank.payments.messages.domain.model.MessageStatus;
import fr.bank.payments.messages.infrastructure.config.MqProperties;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.jms.DeliveryMode;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;
import java.util.Locale;
import org.springframework.jms.listener.SessionAwareMessageListener;
import org.springframework.stereotype.Component;

@Component
public class MessageMqAdapter implements SessionAwareMessageListener<Message> {
  private final JmsMessageDecoder decoder;
  private final IngestMessageUseCase ingestion;
  private final MqProperties properties;
  private final MeterRegistry meters;

  public MessageMqAdapter(
      JmsMessageDecoder decoder,
      IngestMessageUseCase ingestion,
      MqProperties properties,
      MeterRegistry meters) {
    this.decoder = decoder;
    this.ingestion = ingestion;
    this.properties = properties;
    this.meters = meters;
  }

  @Override
  public void onMessage(Message message, Session session) throws JMSException {
    try {
      var received = decoder.decode(message);
      boolean inserted = ingestion.archive(received);
      if (received.status() == MessageStatus.QUARANTINED) {
        // Le PUT et le GET partagent la même transaction MQ ; la base a déjà validé sa transaction.
        try (MessageProducer producer =
            session.createProducer(session.createQueue(properties.quarantineQueue()))) {
          producer.send(message, DeliveryMode.PERSISTENT, message.getJMSPriority(), 0);
        }
      }
      meters
          .counter(
              "payments.messages",
              "result",
              inserted ? received.status().name().toLowerCase(Locale.ROOT) : "duplicate")
          .increment();
    } catch (JMSException | RuntimeException exception) {
      meters.counter("payments.ingestion.failures").increment();
      try {
        Thread.sleep(1000);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
      }
      throw exception; // Le conteneur annule la réception MQ et le message sera relivré.
    }
  }
}
