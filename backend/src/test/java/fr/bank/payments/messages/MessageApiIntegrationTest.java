// createdBy ELMANSOURI Mohamed
package fr.bank.payments.messages;

import static org.assertj.core.api.Assertions.*;

import fr.bank.payments.messages.application.port.in.IngestMessageUseCase;
import fr.bank.payments.messages.application.port.out.MessageArchivePort;
import fr.bank.payments.messages.domain.model.*;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"app.mq.enabled=false"})
class MessageApiIntegrationTest {
  static final EmbeddedPostgres PG;

  static {
    try {
      PG = EmbeddedPostgres.builder().setPort(0).start();
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", () -> PG.getJdbcUrl("postgres", "postgres"));
    r.add("spring.datasource.username", () -> "postgres");
    r.add("spring.datasource.password", () -> "");
  }

  @Autowired IngestMessageUseCase ingestion;
  @Autowired MessageArchivePort repository;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager txManager;
  @LocalServerPort int port;
  final HttpClient client = HttpClient.newHttpClient();

  @BeforeEach
  void clear() {
    jdbc.execute("TRUNCATE payment_message RESTART IDENTITY");
  }

  ReceivedMessage message(String id, String source, String correlation) {
    byte[] body = "<payment>€1250</payment>".getBytes(StandardCharsets.UTF_8);
    return new ReceivedMessage(
        id,
        correlation,
        source,
        "DEV.PAYMENTS.IN",
        Instant.now(),
        BodyType.TEXT,
        body,
        body.length,
        false,
        MessageStatus.STORED,
        null);
  }

  MessageSearch filters(String source, Long cursor, int limit) {
    return new MessageSearch(null, source, null, null, null, null, cursor, limit);
  }

  HttpResponse<String> get(String path) throws Exception {
    return client.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
        HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void concurrentDuplicateDeliveryStoresExactlyOneRow() throws Exception {
    try (var executor = Executors.newFixedThreadPool(8)) {
      var tasks = new java.util.ArrayList<Callable<Boolean>>();
      for (int i = 0; i < 24; i++)
        tasks.add(() -> ingestion.archive(message("ID:same", "BO-SEPA", "corr-1")));
      var results = executor.invokeAll(tasks);
      int inserted = 0;
      for (var result : results) if (result.get()) inserted++;
      assertThat(inserted).isEqualTo(1);
    }
    assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_message", Long.class))
        .isEqualTo(1);
  }

  @Test
  void identicalBodiesWithDifferentMqIdsRemainDistinct() {
    ingestion.archive(message("ID:1", "BO-SEPA", "c"));
    ingestion.archive(message("ID:2", "BO-SEPA", "c"));
    assertThat(repository.search(filters(null, null, 25)).items()).hasSize(2);
  }

  @Test
  void cursorPagesDoNotRepeatRowsWhenNewMessagesArrive() {
    for (int i = 1; i <= 5; i++) ingestion.archive(message("ID:" + i, "BO-SEPA", "c"));
    MessagePage first = repository.search(filters(null, null, 2));
    ingestion.archive(message("ID:new", "BO-SEPA", "c"));
    MessagePage second = repository.search(filters(null, first.nextCursor(), 2));
    assertThat(first.items())
        .extracting(MessageSummary::mqMessageId)
        .containsExactly("ID:5", "ID:4");
    assertThat(second.items())
        .extracting(MessageSummary::mqMessageId)
        .containsExactly("ID:3", "ID:2");
    assertThat(first.items().getFirst().toString()).doesNotContain("<payment>");
  }

  @Test
  void exactMessageSearchAndPreparedParametersWork() {
    ingestion.archive(message("ID:1", "BO-SEPA", "corr-A"));
    ingestion.archive(message("ID:2", "BO-SWIFT", "corr-B"));
    assertThat(repository.search(filters("BO-SEPA", null, 25)).items()).hasSize(1);
    assertThat(repository.search(filters("' OR 1=1 --", null, 25)).items()).isEmpty();
    assertThat(
            repository
                .search(
                    new MessageSearch(
                        MessageStatus.STORED, null, "corr-B", null, null, null, null, 25))
                .items())
        .extracting(MessageSummary::mqMessageId)
        .containsExactly("ID:2");
  }

  @Test
  void rollbackAndReplayKeepTheArchiveConsistent() {
    var tx = new TransactionTemplate(txManager);
    tx.executeWithoutResult(
        status -> {
          ingestion.archive(message("ID:rollback", "BO-SEPA", "c"));
          status.setRollbackOnly();
        });
    assertThat(repository.search(filters(null, null, 25)).items()).isEmpty();
    assertThat(ingestion.archive(message("ID:rollback", "BO-SEPA", "c"))).isTrue();
    assertThat(ingestion.archive(message("ID:rollback", "BO-SEPA", "c"))).isFalse();
  }

  @Test
  void httpValidationDetailAndNotFound() throws Exception {
    ingestion.archive(message("ID:test", "BO-SEPA", "corr"));
    long id = repository.search(filters(null, null, 1)).items().getFirst().id();
    assertThat(get("/api/messages/" + id).body()).contains("€1250").contains("textPayload");
    assertThat(get("/api/messages").body()).contains("ID:test").doesNotContain("€1250");
    assertThat(get("/api/messages/999999").statusCode()).isEqualTo(404);
    for (String query :
        java.util.List.of(
            "limit=0",
            "limit=101",
            "beforeId=-1",
            "status=BAD",
            "from=not-a-date",
            "from=2026-10-17T12:00:00Z&to=2026-10-16T12:00:00Z"))
      assertThat(get("/api/messages?" + query).statusCode()).as(query).isEqualTo(400);
  }

  @Test
  void timeWindowsAndBinaryContentWork() {
    ingestion.archive(message("ID:old", "BO-SEPA", "old"));
    jdbc.update("UPDATE payment_message SET received_at=now()-interval '2 days'");
    ingestion.archive(
        new ReceivedMessage(
            "ID:binary",
            null,
            "BO-SEPA",
            "DEV.PAYMENTS.IN",
            null,
            BodyType.BYTES,
            new byte[] {0, (byte) 255},
            2,
            false,
            MessageStatus.STORED,
            null));
    var recent =
        repository.search(
            new MessageSearch(
                null, null, null, null, Instant.now().minusSeconds(60), null, null, 25));
    assertThat(recent.items()).hasSize(1);
    assertThat(repository.findById(recent.items().getFirst().id()).orElseThrow().payload())
        .containsExactly((byte) 0, (byte) 255);
  }
}
