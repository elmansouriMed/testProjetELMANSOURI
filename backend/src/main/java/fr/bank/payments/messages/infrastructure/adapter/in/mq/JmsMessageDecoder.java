// createdBy ELMANSOURI Mohamed
package fr.bank.payments.messages.infrastructure.adapter.in.mq;

import fr.bank.payments.messages.domain.model.BodyType;
import fr.bank.payments.messages.domain.model.MessageStatus;
import fr.bank.payments.messages.domain.model.ReceivedMessage;
import fr.bank.payments.messages.infrastructure.config.MqProperties;
import jakarta.jms.BytesMessage;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class JmsMessageDecoder {
  private final MqProperties properties;

  public JmsMessageDecoder(MqProperties properties) {
    this.properties = properties;
  }

  public ReceivedMessage decode(Message message) throws JMSException {
    String id = message.getJMSMessageID();
    if (id == null || id.isBlank()) throw new JMSException("Un identifiant MQ est obligatoire");
    DecodedBody body =
        switch (message) {
          case TextMessage text -> textBody(text);
          case BytesMessage bytes -> binaryBody(bytes);
          default -> new DecodedBody(BodyType.UNSUPPORTED, null, "Type JMS non pris en charge");
        };
    String source = message.getStringProperty("sourceApplication");
    source =
        source == null || source.isBlank()
            ? "UNKNOWN"
            : source.substring(0, Math.min(source.length(), 128));
    return new ReceivedMessage(
        id,
        correlation(message),
        source,
        properties.inputQueue(),
        body.type(),
        body.payload(),
        body.reason() == null ? MessageStatus.STORED : MessageStatus.QUARANTINED,
        body.reason());
  }

  private DecodedBody textBody(TextMessage message) throws JMSException {
    String text = message.getText();
    byte[] payload = (text == null ? "" : text).getBytes(StandardCharsets.UTF_8);
    return payload.length > properties.maxPayloadBytes()
        ? new DecodedBody(BodyType.TEXT, null, "Taille maximale dépassée")
        : new DecodedBody(BodyType.TEXT, payload, null);
  }

  private DecodedBody binaryBody(BytesMessage message) throws JMSException {
    long size = message.getBodyLength();
    if (size > properties.maxPayloadBytes())
      return new DecodedBody(BodyType.BYTES, null, "Taille maximale dépassée");
    byte[] payload = new byte[(int) size];
    message.reset();
    for (int offset = 0; offset < payload.length; ) {
      byte[] buffer = new byte[Math.min(8192, payload.length - offset)];
      int count = message.readBytes(buffer);
      if (count <= 0) throw new JMSException("Corps binaire incomplet");
      System.arraycopy(buffer, 0, payload, offset, count);
      offset += count;
    }
    message.reset();
    return new DecodedBody(BodyType.BYTES, payload, null);
  }

  private static String correlation(Message message) throws JMSException {
    try {
      return message.getJMSCorrelationID();
    } catch (JMSException exception) {
      byte[] value = message.getJMSCorrelationIDAsBytes();
      return value == null ? null : HexFormat.of().formatHex(value);
    }
  }

  private record DecodedBody(BodyType type, byte[] payload, String reason) {}
}
