#!/bin/sh

# Define directory to wait for
DT_METADATA_DIR="/var/lib/dynatrace/enrichment"
MAX_WAIT_TIME=120  # Maximum wait time in seconds

echo "Checking for Dynatrace metadata directory at $DT_METADATA_DIR"

# Wait for directory to exist
wait_seconds=0
while [ ! -d "$DT_METADATA_DIR" ] && [ $wait_seconds -lt $MAX_WAIT_TIME ]; do
  echo "Waiting for Dynatrace metadata directory to be created... ($wait_seconds/$MAX_WAIT_TIME seconds)"
  sleep 5
  wait_seconds=$((wait_seconds + 5))
done

if [ ! -d "$DT_METADATA_DIR" ]; then
  echo "Warning: Dynatrace metadata directory not created after $MAX_WAIT_TIME seconds"
  echo "Creating directory as fallback"
  mkdir -p "$DT_METADATA_DIR"
else
  echo "Dynatrace metadata directory exists, continuing startup"
fi

# Source the Dynatrace metadata loading script if it exists
if [ -f "/app/load-dt-metadata.sh" ]; then
  echo "Sourcing Dynatrace metadata script"
  . /app/load-dt-metadata.sh
fi

if [ "$OPENTELEMETRY_ENABLED" = "true" ]; then
  echo "Starting with OpenTelemetry instrumentation"
  exec java \
    -javaagent:/app/opentelemetry-javaagent.jar \
    -Dotel.service.name=${SPRING_APPLICATION_NAME:-load-simulator} \
    -Dotel.exporter.otlp.endpoint=${DYNATRACE_ENDPOINT} \
    -Dotel.exporter.otlp.protocol=http/protobuf \
    -Dotel.exporter.otlp.headers="Authorization=Api-Token ${DYNATRACE_API_TOKEN}" \
    -Dotel.traces.exporter=otlp \
    -Dotel.metrics.exporter=otlp \
    -Dotel.logs.exporter=otlp \
    -Dotel.propagators=tracecontext,baggage \
    -Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true \
    -Dotel.instrumentation.common.experimental.suppress-messaging-receive-spans=false \
    -Dotel.propagation.internal.resource-spans-enabled=true \
    -Dotel.instrumentation.tasks.propagate-across-threads=true \
    -Dotel.instrumentation.executors.enabled=true \
    -Dotel.instrumentation.executor.propagate-context=true \
    -Dotel.instrumentation.common.experimental.trace-parent-spans=true \
    -Dotel.instrumentation.kafka.enabled=true \
    -Dotel.instrumentation.kafka.experimental.message-propagation.enabled=true \
    -Dotel.instrumentation.kafka.experimental.messages.enabled=true \
    -Dotel.instrumentation.kafka.experimental.consumer-parent-span-enabled=true \
    -Dotel.instrumentation.kafka.experimental.consumer-implementation.enabled=true \
    -Dotel.instrumentation.activemq.enabled=true \
    -Dotel.instrumentation.activemq.message-propagation.enabled=true \
    -Dotel.instrumentation.jms.enabled=true \
    -Dotel.instrumentation.jms.experimental.producer-messaging-links.enabled=true \
    -Dotel.instrumentation.jms.experimental.consumer-parent-span-enabled=true \
    -Dotel.instrumentation.jms.experimental.receive-telemetry.enabled=true \
    -Dotel.instrumentation.rabbitmq.experimental.message-propagation.enabled=true \
    -Dotel.instrumentation.rabbitmq.experimental.consumer-parent-span-enabled=true \
    -jar app.jar
else
  echo "Starting without OpenTelemetry instrumentation"
  exec java -jar app.jar
fi
