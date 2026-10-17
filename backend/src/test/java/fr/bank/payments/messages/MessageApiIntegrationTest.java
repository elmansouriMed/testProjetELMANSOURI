package fr.bank.payments.messages;

import fr.bank.payments.ingestion.*;
import fr.bank.payments.messages.MessageViews.*;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={"app.mq.enabled=false"})
class MessageApiIntegrationTest {
    static final EmbeddedPostgres PG;
    static { try { PG = EmbeddedPostgres.builder().setPort(0).start(); } catch (Exception e) { throw new ExceptionInInitializerError(e); } }
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> PG.getJdbcUrl("postgres","postgres"));
        r.add("spring.datasource.username", () -> "postgres"); r.add("spring.datasource.password", () -> "");
    }
    @Autowired MessageIngestionService ingestion;
    @Autowired MessageRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager txManager;
    @LocalServerPort int port;
    final HttpClient client = HttpClient.newHttpClient();
    @BeforeEach void clear() { jdbc.execute("TRUNCATE payment_message RESTART IDENTITY"); }

    IncomingMessage message(String id, String source, String correlation) {
        byte[] body = "<payment>€1250</payment>".getBytes(StandardCharsets.UTF_8);
        return new IncomingMessage(id,correlation,source,"DEV.PAYMENTS.IN",Instant.now(),"TEXT",body,body.length,false,"STORED",null);
    }
    Filters filters(String source, Long cursor, int limit) { return new Filters(null,source,null,null,null,null,cursor,limit); }
    HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    @Test void concurrentDuplicateDeliveryStoresExactlyOneRow() throws Exception {
        try (var executor = Executors.newFixedThreadPool(8)) {
            var tasks = new java.util.ArrayList<Callable<Boolean>>();
            for (int i=0;i<24;i++) tasks.add(() -> ingestion.store(message("ID:same","BO-SEPA","corr-1")));
            var results = executor.invokeAll(tasks);
            int inserted=0; for (var result : results) if (result.get()) inserted++;
            assertThat(inserted).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_message",Long.class)).isEqualTo(1);
    }
    @Test void identicalBodiesWithDifferentMqIdsRemainDistinct() {
        ingestion.store(message("ID:1","BO-SEPA","c")); ingestion.store(message("ID:2","BO-SEPA","c"));
        assertThat(repository.find(filters(null,null,25)).items()).hasSize(2);
    }
    @Test void cursorPagesDoNotRepeatRowsWhenNewMessagesArrive() {
        for(int i=1;i<=5;i++) ingestion.store(message("ID:"+i,"BO-SEPA","c"));
        Page first = repository.find(filters(null,null,2));
        ingestion.store(message("ID:new","BO-SEPA","c"));
        Page second = repository.find(filters(null,Long.parseLong(first.nextCursor()),2));
        assertThat(first.items()).extracting(Summary::mqMessageId).containsExactly("ID:5","ID:4");
        assertThat(second.items()).extracting(Summary::mqMessageId).containsExactly("ID:3","ID:2");
        assertThat(first.items().getFirst().toString()).doesNotContain("<payment>");
    }
    @Test void exactFiltersAndPreparedParametersWork() {
        ingestion.store(message("ID:1","BO-SEPA","corr-A")); ingestion.store(message("ID:2","BO-SWIFT","corr-B"));
        assertThat(repository.find(filters("BO-SEPA",null,25)).items()).hasSize(1);
        assertThat(repository.find(filters("' OR 1=1 --",null,25)).items()).isEmpty();
        assertThat(repository.find(new Filters("STORED",null,"corr-B",null,null,null,null,25)).items()).extracting(Summary::mqMessageId).containsExactly("ID:2");
    }
    @Test void rollbackAndReplayKeepTheArchiveConsistent() {
        var tx = new TransactionTemplate(txManager);
        tx.executeWithoutResult(status -> { ingestion.store(message("ID:rollback","BO-SEPA","c")); status.setRollbackOnly(); });
        assertThat(repository.find(filters(null,null,25)).items()).isEmpty();
        assertThat(ingestion.store(message("ID:rollback","BO-SEPA","c"))).isTrue();
        assertThat(ingestion.store(message("ID:rollback","BO-SEPA","c"))).isFalse();
    }
    @Test void httpValidationDetailAndNotFound() throws Exception {
        ingestion.store(message("ID:test","BO-SEPA","corr"));
        String id = repository.find(filters(null,null,1)).items().getFirst().id();
        assertThat(get("/api/messages/"+id).body()).contains("€1250").contains("textPayload");
        assertThat(get("/api/messages").body()).contains("ID:test").doesNotContain("€1250");
        assertThat(get("/api/messages/999999").statusCode()).isEqualTo(404);
        for (String query : java.util.List.of("limit=0","limit=101","beforeId=-1","status=BAD","from=not-a-date","from=2026-10-17T12:00:00Z&to=2026-10-16T12:00:00Z"))
            assertThat(get("/api/messages?"+query).statusCode()).as(query).isEqualTo(400);
    }
    @Test void timeWindowsAndBinaryContentWork() {
        ingestion.store(message("ID:old","BO-SEPA","old"));
        jdbc.update("UPDATE payment_message SET received_at=now()-interval '2 days'");
        ingestion.store(new IncomingMessage("ID:binary",null,"BO-SEPA","DEV.PAYMENTS.IN",null,"BYTES",new byte[]{0,(byte)255},2,false,"STORED",null));
        long[] stats = repository.statistics(Instant.now());
        assertThat(stats).containsExactly(1,0,1,2);
        var recent = repository.find(new Filters(null,null,null,null,Instant.now().minusSeconds(60),null,null,25));
        assertThat(recent.items()).hasSize(1);
        assertThat(repository.findById(Long.parseLong(recent.items().getFirst().id())).orElseThrow().base64Payload()).isEqualTo("AP8=");
    }
}
