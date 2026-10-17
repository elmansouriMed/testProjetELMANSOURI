// createdBy ELMANSOURI Mohamed
package fr.bank.payments.messages.infrastructure.config;

import com.ibm.mq.jakarta.jms.MQConnectionFactory;
import com.ibm.msg.client.jakarta.wmq.WMQConstants;
import fr.bank.payments.messages.infrastructure.adapter.in.mq.MessageMqAdapter;
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
  UserCredentialsConnectionFactoryAdapter connectionFactory(MqProperties properties)
      throws JMSException {
    var mq = new MQConnectionFactory();
    mq.setHostName(properties.host());
    mq.setPort(properties.port());
    mq.setQueueManager(properties.queueManager());
    mq.setChannel(properties.channel());
    mq.setTransportType(WMQConstants.WMQ_CM_CLIENT);
    mq.setBooleanProperty(WMQConstants.USER_AUTHENTICATION_MQCSP, true);
    mq.setClientReconnectOptions(WMQConstants.WMQ_CLIENT_RECONNECT_Q_MGR);
    mq.setClientReconnectTimeout(60);
    if (properties.sslCipherSuite() != null && !properties.sslCipherSuite().isBlank())
      mq.setSSLCipherSuite(properties.sslCipherSuite());
    var adapter = new UserCredentialsConnectionFactoryAdapter();
    adapter.setTargetConnectionFactory(mq);
    adapter.setUsername(properties.user());
    adapter.setPassword(properties.password());
    return adapter;
  }

  @Bean
  DefaultMessageListenerContainer mqListener(
      UserCredentialsConnectionFactoryAdapter connectionFactory,
      MqProperties properties,
      MessageMqAdapter listener) {
    var container = new DefaultMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    container.setDestinationName(properties.inputQueue());
    container.setMessageListener(listener);
    // Le listener attend le commit SQL avant de laisser le conteneur acquitter le message MQ.
    container.setSessionTransacted(true);
    container.setConcurrentConsumers(properties.consumers());
    container.setMaxConcurrentConsumers(properties.consumers());
    container.setReceiveTimeout(1000);
    var backOff = new ExponentialBackOff(1000, 2.0);
    backOff.setMaxInterval(30000);
    container.setBackOff(backOff);
    container.setAutoStartup(properties.enabled());
    container.setErrorHandler(
        error ->
            log.error(
                "Échec de consommation MQ ; transaction annulée (type={})",
                error.getClass().getSimpleName()));
    return container;
  }

  @Bean
  JmsTemplate jmsTemplate(UserCredentialsConnectionFactoryAdapter connectionFactory) {
    return new JmsTemplate(connectionFactory);
  }
}
