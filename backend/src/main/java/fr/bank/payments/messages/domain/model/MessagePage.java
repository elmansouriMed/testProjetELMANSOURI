// createdBy ELMANSOURI Mohamed
package fr.bank.payments.messages.domain.model;

import java.util.List;

public record MessagePage(List<MessageSummary> items, Long nextCursor, boolean hasMore) {
  public MessagePage {
    items = List.copyOf(items);
  }
}
