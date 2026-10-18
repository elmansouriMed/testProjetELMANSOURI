// createdBy ELMANSOURI Mohamed
package fr.bank.payments.messages.application.port.in;

import fr.bank.payments.messages.domain.model.MessageDetail;
import fr.bank.payments.messages.domain.model.MessagePage;
import fr.bank.payments.messages.domain.model.MessageSearch;

public interface ConsultMessagesUseCase {
  MessagePage search(MessageSearch search);

  MessageDetail findById(long id);
}
