package fr.bank.payments.ingestion;

import fr.bank.payments.messages.MessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessageIngestionService {
    private final MessageRepository repository;
    public MessageIngestionService(MessageRepository repository) { this.repository = repository; }

    // Separate Spring bean: the transaction interceptor commits before returning to the MQ listener.
    @Transactional(timeout = 15)
    public boolean store(IncomingMessage message) { return repository.insert(message); }
}
