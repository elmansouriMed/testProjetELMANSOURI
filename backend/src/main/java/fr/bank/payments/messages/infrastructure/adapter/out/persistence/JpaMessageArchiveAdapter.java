// createdBy ELMANSOURI Mohamed
package fr.bank.payments.messages.infrastructure.adapter.out.persistence;

import fr.bank.payments.messages.application.port.out.MessageArchivePort;
import fr.bank.payments.messages.domain.model.*;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaMessageArchiveAdapter implements MessageArchivePort {
  private final MessageJpaRepository repository;

  public JpaMessageArchiveAdapter(MessageJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  public boolean insert(ReceivedMessage message) {
    return repository.insertIfAbsent(message) == 1;
  }

  @Override
  @Transactional(readOnly = true)
  public MessagePage search(MessageSearch search) {
    var rows =
        repository.findBy(
            filters(search),
            query ->
                query
                    .as(MessageSummary.class)
                    .sortBy(Sort.by(Sort.Direction.DESC, "id"))
                    .limit(search.limit() + 1)
                    .all());
    boolean hasMore = rows.size() > search.limit();
    var items = List.copyOf(rows.subList(0, Math.min(rows.size(), search.limit())));
    return new MessagePage(items, hasMore ? items.getLast().id() : null, hasMore);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<MessageDetail> findById(long id) {
    return repository.findById(id).map(MessageEntity::toDetail);
  }

  private static Specification<MessageEntity> filters(MessageSearch search) {
    return (root, query, builder) -> {
      var predicates = new ArrayList<Predicate>();
      if (search.status() != null)
        predicates.add(builder.equal(root.get("status"), search.status()));
      if (search.source() != null && !search.source().isBlank())
        predicates.add(builder.equal(root.get("sourceApplication"), search.source()));
      if (search.correlationId() != null && !search.correlationId().isBlank())
        predicates.add(builder.equal(root.get("correlationId"), search.correlationId()));
      if (search.mqMessageId() != null && !search.mqMessageId().isBlank())
        predicates.add(builder.equal(root.get("mqMessageId"), search.mqMessageId()));
      if (search.beforeId() != null)
        predicates.add(builder.lessThan(root.get("id"), search.beforeId()));
      if (search.from() != null)
        predicates.add(builder.greaterThanOrEqualTo(root.get("receivedAt"), search.from()));
      if (search.to() != null)
        predicates.add(builder.lessThan(root.get("receivedAt"), search.to()));
      return builder.and(predicates.toArray(Predicate[]::new));
    };
  }
}
