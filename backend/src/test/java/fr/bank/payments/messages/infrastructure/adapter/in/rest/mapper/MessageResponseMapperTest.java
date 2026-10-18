// createdBy ELMANSOURI Mohamed
package fr.bank.payments.messages.infrastructure.adapter.in.rest.mapper;

import static org.assertj.core.api.Assertions.*;

import fr.bank.payments.messages.domain.model.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class MessageResponseMapperTest {
  private final MessageResponseMapper mapper = Mappers.getMapper(MessageResponseMapper.class);

  private MessageSummary metadata(BodyType type, MessageStatus status) {
    return new MessageSummary(
        9007199254740993L,
        "ID:1",
        "corr",
        "BO",
        "IN",
        Instant.EPOCH,
        type,
        status,
        status == MessageStatus.QUARANTINED ? "Format refusé" : null);
  }

  @Test
  void generatedMapperKeeps64BitIdsAndCursorsAsExactStrings() {
    var metadata = metadata(BodyType.TEXT, MessageStatus.STORED);
    var result = mapper.page(new MessagePage(List.of(metadata), metadata.id(), true));
    assertThat(result.items().getFirst().id()).isEqualTo("9007199254740993");
    assertThat(result.nextCursor()).isEqualTo("9007199254740993");
    assertThat(result.items().getFirst().receivedAt()).isEqualTo(Instant.EPOCH);
    assertThat(result.hasMore()).isTrue();
  }

  @Test
  void utf8TextIsNeverMistakenForBinary() {
    var result =
        mapper.detail(
            new MessageDetail(
                metadata(BodyType.TEXT, MessageStatus.STORED),
                "Paiement réglé : 125 €".getBytes(StandardCharsets.UTF_8)));
    assertThat(result.textPayload()).isEqualTo("Paiement réglé : 125 €");
    assertThat(result.base64Payload()).isNull();
  }

  @Test
  void binaryBytesAreEncodedWithoutLossOrTextConversion() {
    var result =
        mapper.detail(
            new MessageDetail(
                metadata(BodyType.BYTES, MessageStatus.STORED),
                new byte[] {0, (byte) 128, (byte) 255, 13, 10}));
    assertThat(result.base64Payload()).isEqualTo("AID/DQo=");
    assertThat(result.textPayload()).isNull();
  }

  @Test
  void quarantinedContentStaysAbsentAndTheReasonIsPreserved() {
    var result =
        mapper.detail(
            new MessageDetail(metadata(BodyType.UNSUPPORTED, MessageStatus.QUARANTINED), null));
    assertThat(result.textPayload()).isNull();
    assertThat(result.base64Payload()).isNull();
    assertThat(result.metadata().rejectionReason()).isEqualTo("Format refusé");
  }

  @Test
  void anEmptyPageDoesNotInventANextCursor() {
    var result = mapper.page(new MessagePage(List.of(), null, false));
    assertThat(result.items()).isEmpty();
    assertThat(result.nextCursor()).isNull();
    assertThat(result.hasMore()).isFalse();
  }
}
