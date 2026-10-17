// createdBy ELMANSOURI Mohamed
package fr.bank.payments.messages.application.port.out;

import fr.bank.payments.messages.domain.model.MessageDetail;
import fr.bank.payments.messages.domain.model.MessagePage;
import fr.bank.payments.messages.domain.model.MessageSearch;
import fr.bank.payments.messages.domain.model.ReceivedMessage;
import java.util.Optional;

public interface MessageArchivePort {
  boolean insert(ReceivedMessage message);

  MessagePage search(MessageSearch search);

  Optional<MessageDetail> findById(long id);
}
