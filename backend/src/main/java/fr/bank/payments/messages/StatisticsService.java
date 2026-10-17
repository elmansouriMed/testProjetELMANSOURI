package fr.bank.payments.messages;

import fr.bank.payments.config.MqProperties;
import fr.bank.payments.messages.MessageViews.Statistics;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.stereotype.Service;
import java.time.Instant;

@Service
public class StatisticsService {
    private final MessageRepository repository;
    private final MqProperties properties;
    private final DefaultMessageListenerContainer listener;
    private volatile Statistics cached;
    public StatisticsService(MessageRepository repository, MqProperties properties, DefaultMessageListenerContainer listener) {
        this.repository = repository; this.properties = properties; this.listener = listener;
    }
    public synchronized Statistics get() {
        Instant now = Instant.now();
        if (cached == null || cached.sampledAt().plusSeconds(5).isBefore(now)) {
            var s = repository.statistics(now);
            cached = new Statistics(s[0],s[1],s[2],s[3],now,properties.enabled(),listener.isRunning(),listener.isRecovering());
        }
        return cached;
    }
}
