// createdBy ELMANSOURI Mohamed
package fr.bank.payments.messages.infrastructure.adapter.in.mq;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import fr.bank.payments.messages.application.port.in.IngestMessageUseCase;
import fr.bank.payments.messages.domain.model.MessageStatus;
import fr.bank.payments.messages.infrastructure.config.MqProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.jms.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class MessageMqAdapterTest {
  private final MqProperties props =
      new MqProperties(
          "localhost",
          1414,
          "QM1",
          "DEV.APP.SVRCONN",
          "app",
          "",
          "DEV.PAYMENTS.IN",
          "DEV.PAYMENTS.QUARANTINE",
          4,
          1024,
          true,
          "");
  private final IngestMessageUseCase ingestion = mock(IngestMessageUseCase.class);
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final MessageMqAdapter listener =
      new MessageMqAdapter(new JmsMessageDecoder(props), ingestion, props, meters);

  private TextMessage text() throws JMSException {
    var message = mock(TextMessage.class);
    when(message.getJMSMessageID()).thenReturn("ID:123");
    when(message.getText()).thenReturn("paiement émis");
    return message;
  }

  @Test
  void persistsTextBeforeReturningWithoutManualAcknowledge() throws Exception {
    var message = text();
    var session = mock(Session.class);
    when(ingestion.archive(any())).thenReturn(true);
    listener.onMessage(message, session);
    verify(ingestion)
        .archive(
            argThat(
                m ->
                    new String(m.payload(), java.nio.charset.StandardCharsets.UTF_8)
                        .equals("paiement émis")));
    verify(message, never()).acknowledge();
    verifyNoInteractions(session);
    assertThat(meters.get("payments.messages").tag("result", "stored").counter().count())
        .isEqualTo(1);
  }

  @Test
  void databaseFailurePropagatesForMqRollback() throws Exception {
    var session = mock(Session.class);
    var message = text();
    when(ingestion.archive(any())).thenThrow(new DataAccessResourceFailureException("offline"));
    assertThatThrownBy(() -> listener.onMessage(message, session))
        .isInstanceOf(DataAccessResourceFailureException.class);
    verify(message, never()).acknowledge();
    verifyNoInteractions(session);
  }

  @Test
  void replayAfterDbCommitDoesNotReinsert() throws Exception {
    when(ingestion.archive(any())).thenReturn(false);
    listener.onMessage(text(), mock(Session.class));
    assertThat(meters.get("payments.messages").tag("result", "duplicate").counter().count())
        .isEqualTo(1);
  }

  @Test
  void unsupportedBodyIsSentToQuarantineUsingTheReceivingSession() throws Exception {
    var message = mock(MapMessage.class);
    when(message.getJMSMessageID()).thenReturn("ID:unsupported");
    var session = mock(Session.class);
    var destination = mock(Queue.class);
    var producer = mock(MessageProducer.class);
    when(session.createQueue(props.quarantineQueue())).thenReturn(destination);
    when(session.createProducer(destination)).thenReturn(producer);
    when(ingestion.archive(any())).thenReturn(true);
    listener.onMessage(message, session);
    var order = inOrder(ingestion, producer);
    order
        .verify(ingestion)
        .archive(argThat(m -> m.status() == MessageStatus.QUARANTINED && m.payload() == null));
    order.verify(producer).send(message, DeliveryMode.PERSISTENT, 0, 0);
    verify(message, never()).acknowledge();
  }

  @Test
  void quarantinePutFailurePropagatesEvenWhenDbAlreadyContainsRejection() throws Exception {
    var message = mock(MapMessage.class);
    when(message.getJMSMessageID()).thenReturn("ID:invalid");
    var session = mock(Session.class);
    when(session.createQueue(anyString())).thenThrow(new JMSException("queue full"));
    when(ingestion.archive(any())).thenReturn(false);
    assertThatThrownBy(() -> listener.onMessage(message, session)).isInstanceOf(JMSException.class);
  }

  @Test
  void oversizedBytesAreNotAllocated() throws Exception {
    var message = mock(BytesMessage.class);
    when(message.getJMSMessageID()).thenReturn("ID:large");
    when(message.getBodyLength()).thenReturn(5000000L);
    var result = new JmsMessageDecoder(props).decode(message);
    assertThat(result.status()).isEqualTo(MessageStatus.QUARANTINED);
    assertThat(result.payload()).isNull();
    verify(message, never()).readBytes(any(byte[].class));
  }

  @Test
  void bytesAndUtf8TextKeepTheirContent() throws Exception {
    var message = mock(BytesMessage.class);
    when(message.getJMSMessageID()).thenReturn("ID:bytes");
    when(message.getBodyLength()).thenReturn(3L);
    when(message.readBytes(any(byte[].class)))
        .thenAnswer(
            inv -> {
              byte[] buffer = inv.getArgument(0);
              buffer[0] = 0;
              buffer[1] = (byte) 255;
              buffer[2] = 42;
              return 3;
            });
    assertThat(new JmsMessageDecoder(props).decode(message).payload())
        .containsExactly((byte) 0, (byte) 255, (byte) 42);
    assertThat(new JmsMessageDecoder(props).decode(text()).payload()).hasSize(14);
  }

  @Test
  void aRejectedMessageIsNotSentToQuarantineWhenSqlFails() throws Exception {
    var message = mock(MapMessage.class);
    when(message.getJMSMessageID()).thenReturn("ID:sql-failure");
    when(ingestion.archive(any())).thenThrow(new DataAccessResourceFailureException("offline"));
    var session = mock(Session.class);
    assertThatThrownBy(() -> listener.onMessage(message, session))
        .isInstanceOf(DataAccessResourceFailureException.class);
    verifyNoInteractions(session);
    verify(message, never()).acknowledge();
    assertThat(meters.get("payments.ingestion.failures").counter().count()).isEqualTo(1);
    assertThat(meters.find("payments.messages").counter()).isNull();
  }

  @Test
  void quarantineSendFailureClosesTheProducerAndDoesNotCountSuccess() throws Exception {
    var message = mock(MapMessage.class);
    when(message.getJMSMessageID()).thenReturn("ID:send-failure");
    var session = mock(Session.class);
    var queue = mock(Queue.class);
    var producer = mock(MessageProducer.class);
    when(session.createQueue(props.quarantineQueue())).thenReturn(queue);
    when(session.createProducer(queue)).thenReturn(producer);
    doThrow(new JMSException("put failed"))
        .when(producer)
        .send(message, DeliveryMode.PERSISTENT, 0, 0);
    assertThatThrownBy(() -> listener.onMessage(message, session))
        .isInstanceOf(JMSException.class)
        .hasMessage("put failed");
    verify(producer).close();
    verify(message, never()).acknowledge();
    assertThat(meters.get("payments.ingestion.failures").counter().count()).isEqualTo(1);
    assertThat(meters.find("payments.messages").counter()).isNull();
  }
}
