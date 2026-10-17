package fr.bank.payments.tools;

import fr.bank.payments.config.MqProperties;
import jakarta.jms.DeliveryMode;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.SessionCallback;
import java.util.UUID;

@Configuration
@ConditionalOnProperty(name="app.publisher.enabled", havingValue="true")
public class SamplePublisher {
    @Bean
    ApplicationRunner publish(JmsTemplate jms, MqProperties properties, Environment env,
                              ConfigurableApplicationContext context) {
        return args -> {
            int count = env.getProperty("app.publisher.count", Integer.class, 20);
            if (count < 1 || count > 1000000) throw new IllegalArgumentException("count doit être compris entre 1 et 1000000");
            boolean invalid = env.getProperty("app.publisher.invalid", Boolean.class, false);
            jms.execute((SessionCallback<Void>) session -> {
                try (var producer = session.createProducer(session.createQueue(properties.inputQueue()))) {
                    producer.setDeliveryMode(DeliveryMode.PERSISTENT);
                    for (int i=0; i<count; i++) {
                        var message = invalid ? session.createMapMessage() : session.createTextMessage("""
                                {"messageType":"pacs.008","reference":"%s","amount":1250.00,"currency":"EUR"}
                                """.formatted(UUID.randomUUID()));
                        message.setStringProperty("sourceApplication", i % 2 == 0 ? "BO-SEPA" : "BO-SWIFT");
                        message.setJMSCorrelationID("demo-" + UUID.randomUUID());
                        producer.send(message);
                    }
                }
                return null;
            });
            System.out.println("Messages publiés dans MQ : " + count);
            SpringApplication.exit(context, () -> 0);
        };
    }
}
