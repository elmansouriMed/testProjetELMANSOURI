// createdBy ELMANSOURI Mohamed
package fr.bank.payments.messages.domain;

import static org.assertj.core.api.Assertions.*;

import fr.bank.payments.messages.domain.model.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MessageModelTest {
  @Test
  void payloadCannotBeModifiedThroughAnExternalReference() {
    byte[] payload = {1, 2};
    var message =
        new ReceivedMessage(
            "ID:1",
            null,
            "BO-SEPA",
            "IN",
            null,
            BodyType.BYTES,
            payload,
            2,
            false,
            MessageStatus.STORED,
            null);
    payload[0] = 9;
    message.payload()[1] = 9;
    assertThat(message.payload()).containsExactly((byte) 1, (byte) 2);
  }

  @Test
  void invalidQueriesAreRejectedBeforePersistence() {
    assertThatThrownBy(() -> new MessageSearch(null, null, null, null, null, null, null, 101))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new MessageSearch(null, null, null, null, Instant.now(), Instant.EPOCH, null, 25))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new MessageSearch(null, null, null, null, null, null, -1L, 25))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void anArchivedMessageMustContainItsPayload() {
    assertThatThrownBy(
            () ->
                new ReceivedMessage(
                    "ID:1",
                    null,
                    "BO",
                    "IN",
                    null,
                    BodyType.TEXT,
                    null,
                    0,
                    false,
                    MessageStatus.STORED,
                    null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
