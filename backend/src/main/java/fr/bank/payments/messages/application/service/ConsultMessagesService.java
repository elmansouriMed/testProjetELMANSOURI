// createdBy ELMANSOURI Mohamed
package fr.bank.payments.messages.application.service;

import fr.bank.payments.messages.application.port.in.ConsultMessagesUseCase;
import fr.bank.payments.messages.application.port.out.MessageArchivePort;
import fr.bank.payments.messages.domain.exception.MessageNotFoundException;
import fr.bank.payments.messages.domain.model.MessageDetail;
import fr.bank.payments.messages.domain.model.MessagePage;
import fr.bank.payments.messages.domain.model.MessageSearch;

public final class ConsultMessagesService implements ConsultMessagesUseCase {
  private final MessageArchivePort archive;

  public ConsultMessagesService(MessageArchivePort archive) {
    this.archive = archive;
  }

  @Override
  public MessagePage search(MessageSearch search) {
    return archive.search(search);
  }

  @Override
  public MessageDetail findById(long id) {
    return archive.findById(id).orElseThrow(() -> new MessageNotFoundException(id));
  }
}
