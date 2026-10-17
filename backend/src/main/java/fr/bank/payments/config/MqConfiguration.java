package fr.bank.payments.config;

import com.ibm.mq.jakarta.jms.MQConnectionFactory;
import com.ibm.msg.client.jakarta.wmq.WMQConstants;
import fr.bank.payments.ingestion.MqMessageListener;
import jakarta.jms.JMSException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.connection.UserCredentialsConnectionFactoryAdapter;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
public class MqConfiguration {
    private static final Logger log = LoggerFactory.getLogger(MqConfiguration.class);

    @Bean
    UserCredentialsConnectionFactoryAdapter connectionFactory(MqProperties p) throws JMSException {
        var mq = new MQConnectionFactory();
        mq.setHostName(p.host()); mq.setPort(p.port());
        mq.setQueueManager(p.queueManager()); mq.setChannel(p.channel());
        mq.setTransportType(WMQConstants.WMQ_CM_CLIENT);
        mq.setBooleanProperty(WMQConstants.USER_AUTHENTICATION_MQCSP, true);
        mq.setClientReconnectOptions(WMQConstants.WMQ_CLIENT_RECONNECT_Q_MGR);
        mq.setClientReconnectTimeout(60);
        if (p.sslCipherSuite() != null && !p.sslCipherSuite().isBlank()) mq.setSSLCipherSuite(p.sslCipherSuite());
        var adapter = new UserCredentialsConnectionFactoryAdapter();
        adapter.setTargetConnectionFactory(mq); adapter.setUsername(p.user()); adapter.setPassword(p.password());
        return adapter;
    }

    @Bean
    DefaultMessageListenerContainer mqListener(UserCredentialsConnectionFactoryAdapter cf,
                                              MqProperties p, MqMessageListener listener) {
        var container = new DefaultMessageListenerContainer();
        container.setConnectionFactory(cf); container.setDestinationName(p.inputQueue());
        container.setMessageListener(listener);
        // Local MQ transaction: the listener must return only after the DB transaction commits.
        container.setSessionTransacted(true);
        container.setConcurrentConsumers(p.consumers());
        container.setMaxConcurrentConsumers(p.consumers());
        container.setReceiveTimeout(1000);
        var backOff = new ExponentialBackOff(1000, 2.0);
        backOff.setMaxInterval(30000);
        container.setBackOff(backOff);
        container.setAutoStartup(p.enabled());
        container.setErrorHandler(error -> log.error("Échec de consommation MQ ; transaction annulée (type={})", error.getClass().getSimpleName()));
        return container;
    }

    @Bean
    JmsTemplate jmsTemplate(UserCredentialsConnectionFactoryAdapter cf) { return new JmsTemplate(cf); }
}
