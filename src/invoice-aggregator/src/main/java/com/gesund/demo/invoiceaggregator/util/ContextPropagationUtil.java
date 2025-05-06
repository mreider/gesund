package com.gesund.demo.invoiceaggregator.util;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.springframework.jms.core.MessagePostProcessor;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Utility class for propagating OpenTelemetry context across messaging systems.
 */
@Slf4j
public class ContextPropagationUtil {

    private static final OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();

    /**
     * Extracts OpenTelemetry context from Kafka record headers.
     *
     * @param record The Kafka consumer record to extract context from
     * @return The extracted context
     */
    public static Context extractContextFromKafkaRecord(ConsumerRecord<?, ?> record) {
        return extractContextFromKafkaHeaders(record.headers());
    }

    /**
     * Extracts OpenTelemetry context from Kafka record headers.
     *
     * @param headers The Kafka headers to extract context from
     * @return The extracted context
     */
    public static Context extractContextFromKafkaHeaders(Headers headers) {
        return openTelemetry.getPropagators().getTextMapPropagator().extract(
                Context.current(),
                headers,
                new TextMapGetter<Headers>() {
                    @Override
                    public Iterable<String> keys(Headers carrier) {
                        return () -> {
                            final Iterator<Header> iterator = carrier.iterator();
                            return new Iterator<String>() {
                                @Override
                                public boolean hasNext() {
                                    return iterator.hasNext();
                                }
                                
                                @Override
                                public String next() {
                                    return iterator.next().key();
                                }
                            };
                        };
                    }

                    @Override
                    public String get(Headers carrier, String key) {
                        Header header = carrier.lastHeader(key);
                        if (header == null) {
                            return null;
                        }
                        return new String(header.value(), StandardCharsets.UTF_8);
                    }
                }
        );
    }

    /**
     * Extracts OpenTelemetry context from JMS message properties.
     *
     * @param message The JMS message to extract context from
     * @return The extracted context
     */
    public static Context extractContextFromJmsMessage(Message message) {
        try {
            Map<String, String> contextMap = new HashMap<>();
            
            // Get all property names from the message
            @SuppressWarnings("unchecked")
            java.util.Enumeration<String> propertyNames = message.getPropertyNames();
            while (propertyNames.hasMoreElements()) {
                String name = propertyNames.nextElement();
                contextMap.put(name, message.getStringProperty(name));
            }

            return openTelemetry.getPropagators().getTextMapPropagator().extract(
                    Context.current(),
                    contextMap,
                    new TextMapGetter<Map<String, String>>() {
                        @Override
                        public Iterable<String> keys(Map<String, String> carrier) {
                            return carrier.keySet();
                        }

                        @Override
                        public String get(Map<String, String> carrier, String key) {
                            return carrier.get(key);
                        }
                    }
            );
        } catch (JMSException e) {
            log.error("Failed to extract context from JMS message: {}", e.getMessage(), e);
            return Context.current();
        }
    }

    /**
     * Injects the current OpenTelemetry context into Kafka record headers.
     *
     * @param headers The Kafka headers to inject context into
     */
    public static void injectContextToKafkaHeaders(Headers headers) {
        openTelemetry.getPropagators().getTextMapPropagator().inject(
                Context.current(),
                headers,
                new TextMapSetter<Headers>() {
                    @Override
                    public void set(Headers carrier, String key, String value) {
                        carrier.add(key, value.getBytes(StandardCharsets.UTF_8));
                    }
                }
        );
        log.debug("Injected OpenTelemetry context into Kafka headers");
    }

    /**
     * Creates a JMS MessagePostProcessor that injects the current OpenTelemetry context into JMS message properties.
     *
     * @return A MessagePostProcessor that injects context
     */
    public static MessagePostProcessor createContextInjector() {
        return message -> {
            injectContextToJmsMessage(message);
            return message;
        };
    }

    /**
     * Injects the current OpenTelemetry context into JMS message properties.
     *
     * @param message The JMS message to inject context into
     */
    public static void injectContextToJmsMessage(Message message) {
        try {
            Map<String, String> contextMap = new HashMap<>();
            openTelemetry.getPropagators().getTextMapPropagator().inject(
                    Context.current(),
                    contextMap,
                    new TextMapSetter<Map<String, String>>() {
                        @Override
                        public void set(Map<String, String> carrier, String key, String value) {
                            carrier.put(key, value);
                        }
                    }
            );

            // Add all context entries as JMS properties
            for (Map.Entry<String, String> entry : contextMap.entrySet()) {
                message.setStringProperty(entry.getKey(), entry.getValue());
            }
            log.debug("Injected OpenTelemetry context into JMS message properties");
        } catch (JMSException e) {
            log.error("Failed to inject context into JMS message: {}", e.getMessage(), e);
        }
    }
}
