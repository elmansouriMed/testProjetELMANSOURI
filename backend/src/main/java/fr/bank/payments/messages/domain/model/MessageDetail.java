// createdBy ELMANSOURI Mohamed
package fr.bank.payments.messages.domain.model;

public record MessageDetail(MessageSummary metadata, byte[] payload) {
  public MessageDetail {
    payload = payload == null ? null : payload.clone();
  }

  @Override
  public byte[] payload() {
    return payload == null ? null : payload.clone();
  }
}
