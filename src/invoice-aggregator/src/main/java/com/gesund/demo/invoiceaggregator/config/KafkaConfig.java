package com.gesund.demo.invoiceaggregator.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gesund.demo.invoiceaggregator.model.PaymentMessage;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
@Slf4j
@ConditionalOnProperty(name = "messaging.system", havingValue = "kafka")
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.application.name}")
    private String applicationName;

    @Bean
    public ConsumerFactory<String, PaymentMessage> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, applicationName);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        // Note: Header deserializer is handled by the JsonDeserializer
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.gesund.demo.*");
        props.put(JsonDeserializer.TYPE_MAPPINGS, 
                "paymentMessage:com.gesund.demo.invoiceaggregator.model.PaymentMessage");
        
        // Configure JsonDeserializer to use JavaTimeModule
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, PaymentMessage.class);
        
        // Enable propagation of headers
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public CommonErrorHandler errorHandler() {
        // Configure the error handler with a fixed backoff
        // Retry 3 times with a 5 second interval between retries
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                new FixedBackOff(5000L, 3L));
        
        // Configure which exceptions should not be retried
        errorHandler.addNotRetryableExceptions(RuntimeException.class);
        
        return errorHandler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentMessage> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, PaymentMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setCommonErrorHandler(errorHandler());
        
        // Important for context propagation
        factory.getContainerProperties().setPollTimeout(3000);
        factory.setConcurrency(3); // Match your threading model
        
        return factory;
    }
}
