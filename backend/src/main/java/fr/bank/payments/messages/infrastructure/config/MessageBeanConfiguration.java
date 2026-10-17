// createdBy ELMANSOURI Mohamed
package fr.bank.payments.messages.infrastructure.config;

import fr.bank.payments.messages.application.port.in.ConsultMessagesUseCase;
import fr.bank.payments.messages.application.port.in.IngestMessageUseCase;
import fr.bank.payments.messages.application.port.out.MessageArchivePort;
import fr.bank.payments.messages.application.service.ArchiveMessageService;
import fr.bank.payments.messages.application.service.ConsultMessagesService;
import fr.bank.payments.messages.infrastructure.transaction.TransactionalMessageIngestion;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MessageBeanConfiguration {
  @Bean
  IngestMessageUseCase ingestMessageUseCase(MessageArchivePort archive) {
    return new TransactionalMessageIngestion(new ArchiveMessageService(archive));
  }

  @Bean
  ConsultMessagesUseCase consultMessagesUseCase(MessageArchivePort archive) {
    return new ConsultMessagesService(archive);
  }
}
