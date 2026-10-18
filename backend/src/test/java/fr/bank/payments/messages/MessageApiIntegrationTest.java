// createdBy ELMANSOURI Mohamed
package fr.bank.payments.messages;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jayway.jsonpath.JsonPath;
import fr.bank.payments.messages.application.port.in.IngestMessageUseCase;
import fr.bank.payments.messages.application.port.out.MessageArchivePort;
import fr.bank.payments.messages.domain.model.*;
import fr.bank.payments.messages.infrastructure.adapter.in.mq.MessageMqAdapter;
import fr.bank.payments.messages.infrastructure.adapter.out.persistence.MessageEntity;
import fr.bank.payments.messages.infrastructure.config.MqProperties;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import jakarta.jms.*;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.LockModeType;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.RollbackException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
    r.add(
        "spring.jpa.properties.hibernate.session_factory.statement_inspector",
        () -> SqlCapture.class.getName());
  }

  public static class SqlCapture implements org.hibernate.resource.jdbc.spi.StatementInspector {
    static final java.util.List<String> STATEMENTS = new CopyOnWriteArrayList<>();

    @Override
    public String inspect(String sql) {
      STATEMENTS.add(sql);
      return sql;
    }
  }

  @Autowired IngestMessageUseCase ingestion;
  @Autowired MessageArchivePort repository;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager txManager;
  @Autowired MessageMqAdapter mqAdapter;
  @Autowired MqProperties mqProperties;
  @Autowired EntityManagerFactory entityManagers;
  @LocalServerPort int port;
  final HttpClient client = HttpClient.newHttpClient();

  @BeforeEach
  void clear() {
    jdbc.execute("TRUNCATE payment_message RESTART IDENTITY");
    SqlCapture.STATEMENTS.clear();
  }

  @Test
  void jpaListProjectionDoesNotReadPayloadOrCountTheWholeArchive() {
    ingestion.archive(message("ID:projection", "BO-SEPA", "c"));
    SqlCapture.STATEMENTS.clear();
    assertThat(repository.search(filters(null, null, 25)).items()).hasSize(1);
    assertThat(SqlCapture.STATEMENTS).hasSize(1);
    assertThat(SqlCapture.STATEMENTS.getFirst().toLowerCase(java.util.Locale.ROOT))
        .contains("select", "received_at")
        .doesNotContain("payload", "count(", "offset");
  }

  @Test
  void anOptimisticVersionConflictRejectsTheStaleTransaction() {
    ingestion.archive(message("ID:version", "BO-SEPA", "c"));
    long id = repository.search(filters(null, null, 1)).items().getFirst().id();
    assertThat(
            jdbc.queryForObject("SELECT version FROM payment_message WHERE id=?", Long.class, id))
        .isZero();
    try (var first = entityManagers.createEntityManager();
        var second = entityManagers.createEntityManager()) {
      first.getTransaction().begin();
      second.getTransaction().begin();
      try {
        var firstCopy = first.find(MessageEntity.class, id);
        var staleCopy = second.find(MessageEntity.class, id);
        first.lock(firstCopy, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        second.lock(staleCopy, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        first.getTransaction().commit();
        assertThatThrownBy(second.getTransaction()::commit)
            .isInstanceOf(RollbackException.class)
            .hasCauseInstanceOf(OptimisticLockException.class);
      } finally {
        if (first.getTransaction().isActive()) first.getTransaction().rollback();
        if (second.getTransaction().isActive()) second.getTransaction().rollback();
      }
    }
    assertThat(
            jdbc.queryForObject("SELECT version FROM payment_message WHERE id=?", Long.class, id))
        .isEqualTo(1);
    assertThat(ingestion.archive(message("ID:version", "BO-SEPA", "c"))).isFalse();
    assertThat(
            jdbc.queryForObject("SELECT version FROM payment_message WHERE id=?", Long.class, id))
        .isEqualTo(1);
  }

  ReceivedMessage message(String id, String source, String correlation) {
    byte[] body = "<payment>€1250</payment>".getBytes(StandardCharsets.UTF_8);
    return new ReceivedMessage(
        id,
        correlation,
        source,
        "DEV.PAYMENTS.IN",
        BodyType.TEXT,
        body,
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
            BodyType.BYTES,
            new byte[] {0, (byte) 255},
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

  @Test
  void theSameMqIdOnTwoQueuesIsNotAFalseDuplicate() {
    var first = message("ID:shared", "BO-SEPA", "c");
    var otherQueue =
        new ReceivedMessage(
            first.mqMessageId(),
            first.correlationId(),
            first.sourceApplication(),
            "ANOTHER.IN",
            first.bodyType(),
            first.payload(),
            first.status(),
            null);
    assertThat(ingestion.archive(first)).isTrue();
    assertThat(ingestion.archive(otherQueue)).isTrue();
    assertThat(repository.search(filters(null, null, 25)).items())
        .extracting(MessageSummary::sourceQueue)
        .containsExactly("ANOTHER.IN", "DEV.PAYMENTS.IN");
  }

  @Test
  void replayDoesNotOverwriteTheFirstContentOrItsMetadata() {
    var first = message("ID:replay", "BO-SEPA", "original-correlation");
    ingestion.archive(first);
    var original = repository.search(filters(null, null, 25)).items().getFirst();
    var replay =
        new ReceivedMessage(
            first.mqMessageId(),
            "changed",
            "BO-SWIFT",
            first.sourceQueue(),
            BodyType.TEXT,
            new byte[] {42},
            MessageStatus.STORED,
            null);
    assertThat(ingestion.archive(replay)).isFalse();
    var detail = repository.findById(original.id()).orElseThrow();
    assertThat(detail.metadata()).isEqualTo(original);
    assertThat(detail.payload()).containsExactly(first.payload());
  }

  @Test
  void timeWindowIncludesItsStartAndExcludesItsEnd() {
    var start = Instant.parse("2026-10-17T12:00:00Z");
    for (int offset = -1; offset <= 1; offset++) {
      String id = "ID:boundary:" + offset;
      ingestion.archive(message(id, "BO-SEPA", "c"));
      jdbc.update(
          "UPDATE payment_message SET received_at=? WHERE mq_message_id=?",
          java.sql.Timestamp.from(start.plusSeconds(offset)),
          id);
    }
    var page =
        repository.search(
            new MessageSearch(null, null, null, null, start, start.plusSeconds(1), null, 25));
    assertThat(page.items())
        .extracting(MessageSummary::mqMessageId)
        .containsExactly("ID:boundary:0");
  }

  @Test
  void aFullLastPageAndAnEmptyPageDoNotAdvertiseAnotherCursor() {
    ingestion.archive(message("ID:1", "BO-SEPA", "c"));
    ingestion.archive(message("ID:2", "BO-SEPA", "c"));
    var last = repository.search(filters(null, null, 2));
    assertThat(last.items()).hasSize(2);
    assertThat(last.hasMore()).isFalse();
    assertThat(last.nextCursor()).isNull();
    var empty = repository.search(filters(null, last.items().getLast().id(), 2));
    assertThat(empty.items()).isEmpty();
    assertThat(empty.hasMore()).isFalse();
    assertThat(empty.nextCursor()).isNull();
  }

  @Test
  void binaryContentAndIdsBeyondJavascriptPrecisionSurviveTheHttpContract() throws Exception {
    jdbc.queryForObject(
        "SELECT setval(pg_get_serial_sequence('payment_message','id')," + "9007199254740992,true)",
        Long.class);
    byte[] payload = {0, (byte) 128, (byte) 255, 13, 10};
    ingestion.archive(
        new ReceivedMessage(
            "ID:binary-http",
            null,
            "BO-SEPA",
            "IN",
            BodyType.BYTES,
            payload,
            MessageStatus.STORED,
            null));
    ingestion.archive(message("ID:next", "BO-SEPA", "c"));
    var page = get("/api/messages?limit=1");
    assertThat(page.statusCode()).isEqualTo(200);
    var json = JsonPath.parse(page.body());
    assertThat(json.read("$.items[0].id", Object.class))
        .isInstanceOf(String.class)
        .isEqualTo("9007199254740994");
    assertThat(json.read("$.nextCursor", Object.class)).isEqualTo("9007199254740994");
    var detail = get("/api/messages/9007199254740993");
    assertThat(detail.statusCode()).isEqualTo(200);
    var body = JsonPath.parse(detail.body());
    assertThat(body.read("$.metadata.id", Object.class)).isEqualTo("9007199254740993");
    assertThat(body.read("$.textPayload", Object.class)).isNull();
    assertThat(java.util.Base64.getDecoder().decode(body.read("$.base64Payload", String.class)))
        .containsExactly(payload);
    var next = get("/api/messages?limit=1&beforeId=9007199254740994");
    assertThat(JsonPath.parse(next.body()).read("$.items[0].id", Object.class))
        .isEqualTo("9007199254740993");
  }

  @Test
  void combinedHttpFiltersPreserveSpecialCharactersAndIntersect() throws Exception {
    String correlation = "réf+€&=42";
    ingestion.archive(message("ID:target", "BO-SEPA", correlation));
    ingestion.archive(message("ID:other-source", "BO-SWIFT", correlation));
    ingestion.archive(message("ID:other-correlation", "BO-SEPA", "other"));
    String query =
        "/api/messages?source=BO-SEPA&status=STORED&mqMessageId=ID:target&correlationId="
            + URLEncoder.encode(correlation, StandardCharsets.UTF_8)
            + "&from=2000-01-01T00:00:00Z&to=2100-01-01T00:00:00Z";
    var response = get(query);
    assertThat(response.statusCode()).isEqualTo(200);
    var body = JsonPath.parse(response.body());
    assertThat(body.read("$.items.length()", Integer.class)).isEqualTo(1);
    assertThat(body.read("$.items[0].correlationId", String.class)).isEqualTo(correlation);
    assertThat(get(query.replace("status=STORED", "status=QUARANTINED")).body())
        .contains("\"items\":[]");
  }

  @ParameterizedTest
  @CsvSource({"source,129", "correlationId,513", "mqMessageId,513"})
  void httpRejectsOverlongFiltersBeforeQuerying(String parameter, int length) throws Exception {
    var response = get("/api/messages?" + parameter + "=" + "a".repeat(length));
    assertThat(response.statusCode()).isEqualTo(400);
  }

  @Test
  void aRealSqlFailureReturnsASanitized503AndTheApiRecovers() throws Exception {
    jdbc.execute("ALTER TABLE payment_message RENAME TO unavailable_archive");
    try {
      for (String path : java.util.List.of("/api/messages", "/api/messages/1")) {
        var response = get(path);
        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.headers().firstValue("Retry-After")).contains("5");
        assertThat(response.headers().firstValue("Content-Type").orElseThrow())
            .startsWith("application/problem+json");
        assertThat(response.body())
            .contains("Stockage indisponible")
            .doesNotContain("SELECT", "jdbc:", "payment_message", "unavailable_archive");
      }
    } finally {
      jdbc.execute("ALTER TABLE unavailable_archive RENAME TO payment_message");
    }
    assertThat(get("/api/messages").statusCode()).isEqualTo(200);
  }

  @Test
  void quarantineSendObservesAnAlreadyCommittedRowFromAnotherSqlConnection() throws Exception {
    var message = mock(MapMessage.class);
    when(message.getJMSMessageID()).thenReturn("ID:committed-before-put");
    var session = mock(Session.class);
    var queue = mock(Queue.class);
    var producer = mock(MessageProducer.class);
    when(session.createQueue(mqProperties.quarantineQueue())).thenReturn(queue);
    when(session.createProducer(queue)).thenReturn(producer);
    doAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              // Une connexion indépendante ne peut pas voir une insertion non validée.
              try (var connection = PG.getPostgresDatabase().getConnection();
                  var statement =
                      connection.prepareStatement(
                          "SELECT status FROM payment_message WHERE mq_message_id=?")) {
                statement.setString(1, "ID:committed-before-put");
                try (var rows = statement.executeQuery()) {
                  assertThat(rows.next()).isTrue();
                  assertThat(rows.getString(1)).isEqualTo("QUARANTINED");
                }
              }
              return null;
            })
        .when(producer)
        .send(message, DeliveryMode.PERSISTENT, 0, 0);
    mqAdapter.onMessage(message, session);
    verify(producer).send(message, DeliveryMode.PERSISTENT, 0, 0);
    verify(producer).close();
    verify(message, never()).acknowledge();
    var stored = repository.search(filters(null, null, 1)).items().getFirst();
    var response = get("/api/messages/" + stored.id());
    assertThat(response.statusCode()).isEqualTo(200);
    var json = JsonPath.parse(response.body());
    assertThat(json.read("$.metadata.status", String.class)).isEqualTo("QUARANTINED");
    assertThat(json.read("$.metadata.rejectionReason", String.class)).isNotBlank();
    assertThat(json.read("$.textPayload", Object.class)).isNull();
    assertThat(json.read("$.base64Payload", Object.class)).isNull();
  }
}
