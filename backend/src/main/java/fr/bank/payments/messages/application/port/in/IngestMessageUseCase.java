// createdBy ELMANSOURI Mohamed
package fr.bank.payments.messages.application.port.in;

import fr.bank.payments.messages.domain.model.ReceivedMessage;

public interface IngestMessageUseCase {
  boolean archive(ReceivedMessage message);
}
