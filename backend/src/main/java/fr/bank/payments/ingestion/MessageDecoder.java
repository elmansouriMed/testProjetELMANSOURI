package fr.bank.payments.ingestion;

import fr.bank.payments.config.MqProperties;
import jakarta.jms.*;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Component
public class MessageDecoder {
    private final MqProperties properties;
    public MessageDecoder(MqProperties properties) { this.properties = properties; }

    public IncomingMessage decode(Message message) throws JMSException {
        String id = message.getJMSMessageID();
        if (id == null || id.isBlank()) throw new JMSException("Un identifiant MQ est obligatoire");
        byte[] payload = null;
        String type;
        String rejection = null;
        long size = 0;
        if (message instanceof TextMessage text) {
            type = "TEXT";
            String body = text.getText();
            payload = (body == null ? "" : body).getBytes(StandardCharsets.UTF_8);
            size = payload.length;
        } else if (message instanceof BytesMessage bytes) {
            type = "BYTES";
            size = bytes.getBodyLength();
            if (size <= properties.maxPayloadBytes()) {
                payload = new byte[(int) size];
                bytes.reset();
                int offset = 0;
                while (offset < payload.length) {
                    byte[] part = new byte[Math.min(8192, payload.length - offset)];
                    int n = bytes.readBytes(part);
                    if (n <= 0) throw new JMSException("Corps binaire incomplet");
                    System.arraycopy(part, 0, payload, offset, n); offset += n;
                }
                bytes.reset();
            }
        } else {
            type = "UNSUPPORTED";
            rejection = "Type JMS non pris en charge";
        }
        if (size > properties.maxPayloadBytes()) { payload = null; rejection = "Taille maximale dépassée"; }
        String correlation;
        try { correlation = message.getJMSCorrelationID(); }
        catch (JMSException ex) {
            byte[] bytes = message.getJMSCorrelationIDAsBytes();
            correlation = bytes == null ? null : java.util.HexFormat.of().formatHex(bytes);
        }
        return new IncomingMessage(id, correlation, source(message), properties.inputQueue(),
                message.getJMSTimestamp() > 0 ? Instant.ofEpochMilli(message.getJMSTimestamp()) : null,
                type, payload, size, message.getJMSRedelivered(),
                rejection == null ? "STORED" : "QUARANTINED", rejection);
    }

    private String source(Message message) throws JMSException {
        String source = message.getStringProperty("sourceApplication");
        return source == null || source.isBlank() ? "UNKNOWN" : source.substring(0, Math.min(source.length(), 128));
    }
}
