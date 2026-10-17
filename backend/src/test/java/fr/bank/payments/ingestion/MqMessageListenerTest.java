package fr.bank.payments.ingestion;

import fr.bank.payments.config.MqProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.jms.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MqMessageListenerTest {
    private final MqProperties props = new MqProperties("localhost",1414,"QM1","DEV.APP.SVRCONN","app","", "DEV.PAYMENTS.IN","DEV.PAYMENTS.QUARANTINE",4,1024,true,"");
    private final MessageIngestionService ingestion = mock(MessageIngestionService.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final MqMessageListener listener = new MqMessageListener(new MessageDecoder(props),ingestion,props,meters);

    private TextMessage text() throws JMSException {
        var message = mock(TextMessage.class);
        when(message.getJMSMessageID()).thenReturn("ID:123"); when(message.getText()).thenReturn("paiement émis");
        return message;
    }
    @Test void persistsTextBeforeReturningWithoutManualAcknowledge() throws Exception {
        var message = text(); var session = mock(Session.class);
        when(ingestion.store(any())).thenReturn(true);
        listener.onMessage(message,session);
        verify(ingestion).store(argThat(m -> new String(m.payload(),java.nio.charset.StandardCharsets.UTF_8).equals("paiement émis")));
        verify(message,never()).acknowledge(); verifyNoInteractions(session);
        assertThat(meters.get("payments.messages").tag("result","stored").counter().count()).isEqualTo(1);
    }
    @Test void databaseFailurePropagatesForMqRollback() throws Exception {
        var session = mock(Session.class); var message = text();
        when(ingestion.store(any())).thenThrow(new DataAccessResourceFailureException("offline"));
        assertThatThrownBy(() -> listener.onMessage(message,session)).isInstanceOf(DataAccessResourceFailureException.class);
        verify(message,never()).acknowledge(); verifyNoInteractions(session);
    }
    @Test void replayAfterDbCommitDoesNotReinsert() throws Exception {
        when(ingestion.store(any())).thenReturn(false);
        listener.onMessage(text(),mock(Session.class));
        assertThat(meters.get("payments.messages").tag("result","duplicate").counter().count()).isEqualTo(1);
    }
    @Test void unsupportedBodyIsSentToQuarantineUsingTheReceivingSession() throws Exception {
        var message = mock(MapMessage.class); when(message.getJMSMessageID()).thenReturn("ID:unsupported");
        var session = mock(Session.class); var destination = mock(Queue.class); var producer = mock(MessageProducer.class);
        when(session.createQueue(props.quarantineQueue())).thenReturn(destination);
        when(session.createProducer(destination)).thenReturn(producer);
        when(ingestion.store(any())).thenReturn(true);
        listener.onMessage(message,session);
        var order = inOrder(ingestion,producer);
        order.verify(ingestion).store(argThat(m -> m.status().equals("QUARANTINED") && m.payload() == null));
        order.verify(producer).send(message,DeliveryMode.PERSISTENT,0,0);
        verify(message,never()).acknowledge();
    }
    @Test void quarantinePutFailurePropagatesEvenWhenDbAlreadyContainsRejection() throws Exception {
        var message = mock(MapMessage.class); when(message.getJMSMessageID()).thenReturn("ID:invalid");
        var session = mock(Session.class); when(session.createQueue(anyString())).thenThrow(new JMSException("queue full"));
        when(ingestion.store(any())).thenReturn(false);
        assertThatThrownBy(() -> listener.onMessage(message,session)).isInstanceOf(JMSException.class);
    }
    @Test void oversizedBytesAreNotAllocated() throws Exception {
        var message = mock(BytesMessage.class); when(message.getJMSMessageID()).thenReturn("ID:large");
        when(message.getBodyLength()).thenReturn(5000000L);
        var result = new MessageDecoder(props).decode(message);
        assertThat(result.status()).isEqualTo("QUARANTINED"); assertThat(result.payload()).isNull();
        verify(message,never()).readBytes(any(byte[].class));
    }
    @Test void bytesAndUtf8TextKeepTheirContent() throws Exception {
        var message = mock(BytesMessage.class); when(message.getJMSMessageID()).thenReturn("ID:bytes");
        when(message.getBodyLength()).thenReturn(3L);
        when(message.readBytes(any(byte[].class))).thenAnswer(inv -> {
            byte[] buffer = inv.getArgument(0); buffer[0]=0; buffer[1]=(byte)255; buffer[2]=42; return 3;
        });
        assertThat(new MessageDecoder(props).decode(message).payload()).containsExactly((byte)0,(byte)255,(byte)42);
        assertThat(new MessageDecoder(props).decode(text()).payloadSize()).isEqualTo(14);
    }
}
