package fr.bank.payments.config;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.mq")
public record MqProperties(
        @NotBlank String host, @Min(1) @Max(65535) int port,
        @NotBlank String queueManager, @NotBlank String channel,
        String user, String password, @NotBlank String inputQueue,
        @NotBlank String quarantineQueue, @Min(1) int consumers,
        @Min(1) @Max(104857600) int maxPayloadBytes, boolean enabled,
        String sslCipherSuite) {
    @AssertTrue(message = "Les files d'entrée et de quarantaine doivent être différentes")
    public boolean isQueueConfigurationValid() { return !inputQueue.equals(quarantineQueue); }
}
