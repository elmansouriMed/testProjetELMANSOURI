// createdBy ELMANSOURI Mohamed
package fr.bank.payments.messages.infrastructure.adapter.in.mq;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import fr.bank.payments.messages.domain.model.MessageStatus;
import fr.bank.payments.messages.infrastructure.config.MqProperties;
import jakarta.jms.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class JmsMessageDecoderTest {
  private final JmsMessageDecoder decoder =
      new JmsMessageDecoder(
          new MqProperties(
              "localhost", 1414, "QM1", "CHANNEL", "app", "", "IN", "REJECTED", 1, 8, true, ""));

  private TextMessage text(String body) throws JMSException {
    var message = mock(TextMessage.class);
    when(message.getJMSMessageID()).thenReturn("ID:123");
    when(message.getText()).thenReturn(body);
    return message;
  }

  @ParameterizedTest
  @NullAndEmptySource
  void anEmptyTextIsAnArchivedEmptyPayload(String body) throws Exception {
    var result = decoder.decode(text(body));
    assertThat(result.status()).isEqualTo(MessageStatus.STORED);
    assertThat(result.payload()).isEmpty();
    assertThat(result.payloadSize()).isZero();
  }

  @Test
  void sizeLimitUsesUtf8BytesAndAcceptsTheExactBoundary() throws Exception {
    var accepted = decoder.decode(text("éééé"));
    var rejected = decoder.decode(text("ééééa"));
    assertThat(accepted.payload()).isEqualTo("éééé".getBytes(StandardCharsets.UTF_8));
    assertThat(accepted.payloadSize()).isEqualTo(8);
    assertThat(accepted.status()).isEqualTo(MessageStatus.STORED);
    assertThat(rejected.status()).isEqualTo(MessageStatus.QUARANTINED);
    assertThat(rejected.payloadSize()).isEqualTo(9);
    assertThat(rejected.payload()).isNull();
    assertThat(rejected.rejectionReason()).isNotBlank();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = "   ")
  void missingSourceIsUnknown(String source) throws Exception {
    var message = text("ok");
    when(message.getStringProperty("sourceApplication")).thenReturn(source);
    assertThat(decoder.decode(message).sourceApplication()).isEqualTo("UNKNOWN");
  }

  @Test
  void metadataIsPreservedAndOnlyTheSourceIsTruncated() throws Exception {
    var message = text("ok");
    when(message.getStringProperty("sourceApplication")).thenReturn("a".repeat(129));
    when(message.getJMSCorrelationID()).thenReturn("référence+€&42");
    when(message.getJMSTimestamp()).thenReturn(1234L);
    when(message.getJMSRedelivered()).thenReturn(true);
    var result = decoder.decode(message);
    assertThat(result.sourceApplication()).isEqualTo("a".repeat(128));
    assertThat(result.sourceQueue()).isEqualTo("IN");
    assertThat(result.correlationId()).isEqualTo("référence+€&42");
    assertThat(result.sentAt()).isEqualTo(Instant.ofEpochMilli(1234));
    assertThat(result.redelivered()).isTrue();
  }

  @Test
  void binaryCorrelationIsConvertedToHexWhenTheStringAccessorRefusesIt() throws Exception {
    var message = text("ok");
    when(message.getJMSCorrelationID()).thenThrow(new JMSException("binary correlation"));
    when(message.getJMSCorrelationIDAsBytes()).thenReturn(new byte[] {0, (byte) 255, 16});
    assertThat(decoder.decode(message).correlationId()).isEqualTo("00ff10");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = " ")
  void anAbsentMqIdIsRetriedRatherThanArchivedWithAnInventedId(String id) throws Exception {
    var message = text("ok");
    when(message.getJMSMessageID()).thenReturn(id);
    assertThatThrownBy(() -> decoder.decode(message)).isInstanceOf(JMSException.class);
    verify(message, never()).getText();
  }

  @Test
  void binaryChunksAreReassembledWithoutTruncationAndResetForQuarantineReuse() throws Exception {
    var message = mock(BytesMessage.class);
    when(message.getJMSMessageID()).thenReturn("ID:chunks");
    when(message.getBodyLength()).thenReturn(8L);
    byte[] expected = {0, 1, (byte) 255, 3, 4, 5, 6, 7};
    int[] offset = {0};
    when(message.readBytes(any(byte[].class)))
        .thenAnswer(
            invocation -> {
              byte[] buffer = invocation.getArgument(0);
              int count = Math.min(3, expected.length - offset[0]);
              System.arraycopy(expected, offset[0], buffer, 0, count);
              offset[0] += count;
              return count;
            });
    assertThat(decoder.decode(message).payload()).containsExactly(expected);
    verify(message, times(3)).readBytes(any(byte[].class));
    verify(message, times(2)).reset();
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 0})
  void anIncompleteBinaryReadFailsInsteadOfArchivingZeroFilledBytes(int endOfRead)
      throws Exception {
    var message = mock(BytesMessage.class);
    when(message.getJMSMessageID()).thenReturn("ID:truncated");
    when(message.getBodyLength()).thenReturn(2L);
    when(message.readBytes(any(byte[].class))).thenReturn(endOfRead);
    assertThatThrownBy(() -> decoder.decode(message))
        .isInstanceOf(JMSException.class)
        .hasMessageContaining("incomplet");
  }

  @Test
  void metadataReadFailureIsPropagatedForMqRollback() throws Exception {
    var message = text("ok");
    when(message.getStringProperty("sourceApplication")).thenThrow(new JMSException("metadata"));
    assertThatThrownBy(() -> decoder.decode(message))
        .isInstanceOf(JMSException.class)
        .hasMessage("metadata");
  }
}
