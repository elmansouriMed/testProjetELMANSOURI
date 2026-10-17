// createdBy ELMANSOURI Mohamed
package fr.bank.payments.messages.application.service;

import fr.bank.payments.messages.application.port.in.IngestMessageUseCase;
import fr.bank.payments.messages.application.port.out.MessageArchivePort;
import fr.bank.payments.messages.domain.model.ReceivedMessage;

public final class ArchiveMessageService implements IngestMessageUseCase {
  private final MessageArchivePort archive;

  public ArchiveMessageService(MessageArchivePort archive) {
    this.archive = archive;
  }

  @Override
  public boolean archive(ReceivedMessage message) {
    return archive.insert(message);
  }
}
