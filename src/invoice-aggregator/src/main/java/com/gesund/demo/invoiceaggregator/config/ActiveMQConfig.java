package com.gesund.demo.invoiceaggregator.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.jms.ConnectionFactory;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;
import lombok.extern.slf4j.Slf4j;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableJms
@Slf4j
@ConditionalOnProperty(name = "messaging.system", havingValue = "activemq")
public class ActiveMQConfig {
    
    @Bean
    public BeanPostProcessor jmsListenerContainerFactoryCustomizer() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof DefaultJmsListenerContainerFactory factory) {
                    factory.setErrorHandler(t -> log.error("JMS error: {}", t.getMessage(), t));
                }
                return bean;
            }
        };
    }

    @Value("${spring.activemq.broker-url}")
    private String brokerUrl;

    @Value("${spring.activemq.user:admin}")
    private String username;

    @Value("${spring.activemq.password:admin}")
    private String password;

    @Bean
    public ConnectionFactory connectionFactory() {
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory();
        connectionFactory.setBrokerURL(brokerUrl);
        connectionFactory.setUserName(username);
        connectionFactory.setPassword(password);
        connectionFactory.setTrustAllPackages(true);
        return connectionFactory;
    }

    @Bean
    public MessageConverter jacksonJmsMessageConverter() {
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        converter.setObjectMapper(objectMapper);
        converter.setTargetType(MessageType.TEXT);
        
        // Add explicit type mappings between classes from different modules
        Map<String, Class<?>> typeIdMappings = new HashMap<>();
        typeIdMappings.put("com.gesund.demo.paymentprocessor.model.PaymentMessage", 
                           com.gesund.demo.invoiceaggregator.model.PaymentMessage.class);
        converter.setTypeIdMappings(typeIdMappings);
        
        converter.setTypeIdPropertyName("_type");
        return converter;
    }

    @Bean
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory() {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory());
        factory.setMessageConverter(jacksonJmsMessageConverter());
        factory.setConcurrency("3-10"); // Set min-max concurrency
        factory.setPubSubDomain(false); // false for queue, true for topic
        
        // Important for context propagation
        factory.setSessionTransacted(true);
        factory.setReceiveTimeout(3000L);
        
        return factory;
    }
}
