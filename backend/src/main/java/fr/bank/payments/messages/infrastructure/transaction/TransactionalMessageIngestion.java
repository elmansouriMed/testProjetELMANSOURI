// createdBy ELMANSOURI Mohamed
package fr.bank.payments.messages.infrastructure.transaction;

import fr.bank.payments.messages.application.port.in.IngestMessageUseCase;
import fr.bank.payments.messages.domain.model.ReceivedMessage;
import org.springframework.transaction.annotation.Transactional;

/** Ajoute la transaction SQL au cas d'usage sans introduire Spring dans le cœur applicatif. */
public class TransactionalMessageIngestion implements IngestMessageUseCase {
  private final IngestMessageUseCase delegate;

  public TransactionalMessageIngestion(IngestMessageUseCase delegate) {
    this.delegate = delegate;
  }

  @Override
  @Transactional(timeout = 15)
  public boolean archive(ReceivedMessage message) {
    // Le proxy Spring termine le commit avant de rendre la main à l'adaptateur MQ.
    return delegate.archive(message);
  }
}
