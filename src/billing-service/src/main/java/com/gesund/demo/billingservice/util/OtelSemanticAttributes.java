package com.gesund.demo.billingservice.util;

import io.opentelemetry.api.trace.SpanKind;

/**
 * Utility class that defines OpenTelemetry semantic convention attribute names as string constants.
 * This avoids the need for the semantic conventions JAR files.
 */
public final class OtelSemanticAttributes {
    // Span kind constants
    public static final String SPAN_KIND = "span.kind";
    public static final String SPAN_KIND_CLIENT = SpanKind.CLIENT.toString();
    public static final String SPAN_KIND_SERVER = SpanKind.SERVER.toString();
    public static final String SPAN_KIND_PRODUCER = SpanKind.PRODUCER.toString();
    public static final String SPAN_KIND_CONSUMER = SpanKind.CONSUMER.toString();
    public static final String SPAN_KIND_INTERNAL = SpanKind.INTERNAL.toString();
    // General resource attributes
    public static final String SERVICE_NAME = "service.name";
    public static final String SERVICE_VERSION = "service.version";
    public static final String SERVICE_INSTANCE_ID = "service.instance.id";
    
    // Messaging attributes
    public static final String MESSAGING_SYSTEM = "messaging.system";
    public static final String MESSAGING_DESTINATION = "messaging.destination";
    public static final String MESSAGING_DESTINATION_KIND = "messaging.destination_kind";
    public static final String MESSAGING_OPERATION = "messaging.operation";
    public static final String MESSAGING_MESSAGE_ID = "messaging.message_id";
    public static final String MESSAGING_CONVERSATION_ID = "messaging.conversation_id";
    public static final String MESSAGING_KAFKA_MESSAGE_KEY = "messaging.kafka.message_key";
    public static final String MESSAGING_KAFKA_CONSUMER_GROUP = "messaging.kafka.consumer.group";
    public static final String MESSAGING_KAFKA_DESTINATION_PARTITION = "messaging.kafka.destination.partition";
    public static final String MESSAGING_KAFKA_MESSAGE_OFFSET = "messaging.kafka.message.offset";
    
    // Database attributes
    public static final String DB_SYSTEM = "db.system";
    public static final String DB_CONNECTION_STRING = "db.connection_string";
    public static final String DB_USER = "db.user";
    public static final String DB_NAME = "db.name";
    public static final String DB_STATEMENT = "db.statement";
    public static final String DB_OPERATION = "db.operation";
    public static final String DB_SQL_TABLE = "db.sql.table";
    
    // HTTP attributes
    public static final String HTTP_METHOD = "http.method";
    public static final String HTTP_URL = "http.url";
    public static final String HTTP_STATUS_CODE = "http.status_code";
    public static final String HTTP_FLAVOR = "http.flavor";
    public static final String HTTP_USER_AGENT = "http.user_agent";
    public static final String HTTP_REQUEST_CONTENT_LENGTH = "http.request_content_length";
    public static final String HTTP_RESPONSE_CONTENT_LENGTH = "http.response_content_length";
    
    // Network attributes
    public static final String NET_PEER_NAME = "net.peer.name";
    public static final String NET_PEER_PORT = "net.peer.port";
    public static final String NET_TRANSPORT = "net.transport";
    
    // Peer attributes
    public static final String PEER_SERVICE = "peer.service";
    
    // Exception attributes
    public static final String EXCEPTION_TYPE = "exception.type";
    public static final String EXCEPTION_MESSAGE = "exception.message";
    public static final String EXCEPTION_STACKTRACE = "exception.stacktrace";
    
    private OtelSemanticAttributes() {
        // Private constructor to prevent instantiation
    }
}
