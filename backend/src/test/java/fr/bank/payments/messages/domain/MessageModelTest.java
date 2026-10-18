// createdBy ELMANSOURI Mohamed
package fr.bank.payments.messages.domain;

import static org.assertj.core.api.Assertions.*;

import fr.bank.payments.messages.domain.model.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class MessageModelTest {
  @Test
  void payloadCannotBeModifiedThroughAnExternalReference() {
    byte[] payload = {1, 2};
    var message =
        new ReceivedMessage(
            "ID:1", null, "BO-SEPA", "IN", BodyType.BYTES, payload, MessageStatus.STORED, null);
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
                    "ID:1", null, "BO", "IN", BodyType.TEXT, null, MessageStatus.STORED, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = "  ")
  void quarantineRequiresAnActionableReason(String reason) {
    assertThatThrownBy(
            () ->
                new ReceivedMessage(
                    "ID:rejected",
                    null,
                    "BO",
                    "IN",
                    BodyType.UNSUPPORTED,
                    null,
                    MessageStatus.QUARANTINED,
                    reason))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 100})
  void pageSizeBoundariesAndAnUnboundedTimeWindowAreValid(int limit) {
    var search = new MessageSearch(null, null, null, null, null, null, 1L, limit);
    assertThat(search.limit()).isEqualTo(limit);
    assertThatThrownBy(
            () ->
                new MessageSearch(
                    null, null, null, null, Instant.EPOCH, Instant.EPOCH, null, limit))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aPageCannotBeModifiedThroughItsInputOrReturnedList() {
    var source = new java.util.ArrayList<MessageSummary>();
    var page = new MessagePage(source, null, false);
    source.add(summary());
    assertThat(page.items()).isEmpty();
    assertThatThrownBy(() -> page.items().add(summary()))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void aDetailAlsoProtectsItsPayloadFromExternalMutation() {
    byte[] bytes = {0, (byte) 255};
    var detail = new MessageDetail(summary(), bytes);
    bytes[0] = 42;
    detail.payload()[1] = 42;
    assertThat(detail.payload()).containsExactly((byte) 0, (byte) 255);
  }

  private MessageSummary summary() {
    return new MessageSummary(
        1, "ID:1", null, "BO", "IN", Instant.EPOCH, BodyType.BYTES, MessageStatus.STORED, null);
  }
}
