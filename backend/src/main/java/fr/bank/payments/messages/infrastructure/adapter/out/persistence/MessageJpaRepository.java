// createdBy ELMANSOURI Mohamed
package fr.bank.payments.messages.infrastructure.adapter.out.persistence;

import fr.bank.payments.messages.domain.model.ReceivedMessage;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

interface MessageJpaRepository
    extends JpaRepository<MessageEntity, Long>, JpaSpecificationExecutor<MessageEntity> {
  // save() seul ne protège pas deux consommateurs concurrents d'une même livraison MQ.
  @Transactional
  @Modifying
  @Query(
      value =
          """
          INSERT INTO payment_message(mq_message_id,correlation_id,source_application,source_queue,
            body_type,payload,status,rejection_reason)
          VALUES (:#{#message.mqMessageId()},:#{#message.correlationId()},
            :#{#message.sourceApplication()},:#{#message.sourceQueue()},
            :#{#message.bodyType().name()},:#{#message.payload()},
            :#{#message.status().name()},:#{#message.rejectionReason()})
          ON CONFLICT (source_queue,mq_message_id) DO NOTHING
          """,
      nativeQuery = true)
  int insertIfAbsent(@Param("message") ReceivedMessage message);
}
