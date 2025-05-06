FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Download the OpenTelemetry Java agent
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar

# Add scripts
COPY entrypoint.sh /app/entrypoint.sh
COPY load-dt-metadata.sh /app/load-dt-metadata.sh
RUN chmod +x /app/entrypoint.sh /app/load-dt-metadata.sh

# Environment variables with defaults
ENV OPENTELEMETRY_ENABLED=true \
    BILLING_SERVICE_URL=http://billing-service:8080 \
    DYNATRACE_ENDPOINT=http://dynatrace-endpoint:9090 \
    DYNATRACE_API_TOKEN=your-api-token \
    OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf

EXPOSE 8083
ENTRYPOINT ["/app/entrypoint.sh"]
