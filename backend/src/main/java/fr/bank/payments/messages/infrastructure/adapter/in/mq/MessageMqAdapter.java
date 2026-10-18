// createdBy ELMANSOURI Mohamed
package fr.bank.payments.messages.infrastructure.adapter.in.mq;

import fr.bank.payments.messages.application.port.in.IngestMessageUseCase;
import fr.bank.payments.messages.domain.model.MessageStatus;
import fr.bank.payments.messages.infrastructure.config.MqProperties;
import jakarta.jms.DeliveryMode;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;
import org.springframework.jms.listener.SessionAwareMessageListener;
import org.springframework.stereotype.Component;

@Component
public class MessageMqAdapter implements SessionAwareMessageListener<Message> {
  private final JmsMessageDecoder decoder;
  private final IngestMessageUseCase ingestion;
  private final MqProperties properties;

  public MessageMqAdapter(
      JmsMessageDecoder decoder, IngestMessageUseCase ingestion, MqProperties properties) {
    this.decoder = decoder;
    this.ingestion = ingestion;
    this.properties = properties;
  }

  @Override
  public void onMessage(Message message, Session session) throws JMSException {
    try {
      var received = decoder.decode(message);
      ingestion.archive(received);
      if (received.status() == MessageStatus.QUARANTINED) {
        // Le PUT et le GET partagent la même transaction MQ ; la base a déjà validé sa transaction.
        try (MessageProducer producer =
            session.createProducer(session.createQueue(properties.quarantineQueue()))) {
          producer.send(message, DeliveryMode.PERSISTENT, message.getJMSPriority(), 0);
        }
      }
    } catch (JMSException | RuntimeException exception) {
      try {
        Thread.sleep(1000);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
      }
      throw exception; // Le conteneur annule la réception MQ et le message sera relivré.
    }
  }
}
