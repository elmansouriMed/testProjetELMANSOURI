// createdBy ELMANSOURI Mohamed
package fr.bank.payments.messages.application.port.in;

import fr.bank.payments.messages.domain.model.MessageDetail;
import fr.bank.payments.messages.domain.model.MessagePage;
import fr.bank.payments.messages.domain.model.MessageSearch;
import java.util.Optional;

public interface ConsultMessagesUseCase {
  MessagePage search(MessageSearch search);

  Optional<MessageDetail> findById(long id);
}
