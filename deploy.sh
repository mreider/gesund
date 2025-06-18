#!/bin/bash
set -e

# Create namespaces
echo "Creating namespaces..."
kubectl apply -f k8s/namespaces.yaml

echo "Ensuring dynatrace namespace exists..."
kubectl create namespace dynatrace || echo "Namespace dynatrace already exists or error creating it."

# --- BEGIN Dynatrace Operator Deployment ---
echo "Deploying Dynatrace Operator..."

# dynatrace namespace is expected to exist at this point
echo "Installing Dynatrace Operator with CSI..."
kubectl apply -f https://github.com/Dynatrace/dynatrace-operator/releases/download/v1.5.1/kubernetes-csi.yaml

echo "Waiting for Dynatrace Operator webhook to be ready..."
kubectl -n dynatrace wait pod --for=condition=ready --selector=app.kubernetes.io/name=dynatrace-operator,app.kubernetes.io/component=webhook --timeout=300s

echo "Applying Dynatrace Operator secret (k8s/dynakube-operator-secret.yaml) to dynatrace namespace..."
echo "NOTE: Ensure you have created 'k8s/dynakube-operator-secret.yaml' from the template and filled in your tokens."
kubectl apply -f k8s/dynakube-operator-secret.yaml -n dynatrace

echo "Applying DynaKube custom resource..."
kubectl apply -f k8s/dynakube.yaml -n dynatrace
# --- END Dynatrace Operator Deployment ---

# Create Dynatrace secret in each namespace
echo "Creating Dynatrace secret..."
kubectl apply -f k8s/dynatrace-secret.yaml -n otel-kafka
kubectl apply -f k8s/dynatrace-secret.yaml -n otel-activemq
kubectl apply -f k8s/dynatrace-secret.yaml -n otel-rabbitmq
kubectl apply -f k8s/dynatrace-secret.yaml -n oneagent-kafka
kubectl apply -f k8s/dynatrace-secret.yaml -n oneagent-activemq
kubectl apply -f k8s/dynatrace-secret.yaml -n oneagent-rabbitmq

# Create Postgres secret in each namespace
echo "Creating Postgres secret..."
kubectl create secret generic postgres-secret \
  --from-literal=username=postgres \
  --from-literal=password=postgres \
  -n otel-kafka --dry-run=client -o yaml | kubectl apply -f -

kubectl create secret generic postgres-secret \
  --from-literal=username=postgres \
  --from-literal=password=postgres \
  -n otel-activemq --dry-run=client -o yaml | kubectl apply -f -

kubectl create secret generic postgres-secret \
  --from-literal=username=postgres \
  --from-literal=password=postgres \
  -n otel-rabbitmq --dry-run=client -o yaml | kubectl apply -f -

kubectl create secret generic postgres-secret \
  --from-literal=username=postgres \
  --from-literal=password=postgres \
  -n oneagent-kafka --dry-run=client -o yaml | kubectl apply -f -

kubectl create secret generic postgres-secret \
  --from-literal=username=postgres \
  --from-literal=password=postgres \
  -n oneagent-activemq --dry-run=client -o yaml | kubectl apply -f -

kubectl create secret generic postgres-secret \
  --from-literal=username=postgres \
  --from-literal=password=postgres \
  -n oneagent-rabbitmq --dry-run=client -o yaml | kubectl apply -f -

# Deploy infrastructure components
echo "Deploying Postgres..."
kubectl apply -f k8s/postgres.yaml -n otel-kafka
kubectl apply -f k8s/postgres.yaml -n otel-activemq
kubectl apply -f k8s/postgres.yaml -n otel-rabbitmq
kubectl apply -f k8s/postgres.yaml -n oneagent-kafka
kubectl apply -f k8s/postgres.yaml -n oneagent-activemq
kubectl apply -f k8s/postgres.yaml -n oneagent-rabbitmq

echo "Deploying Kafka..."
kubectl apply -f k8s/kafka.yaml -n otel-kafka
kubectl apply -f k8s/kafka.yaml -n oneagent-kafka

echo "Deploying ActiveMQ..."
kubectl apply -f k8s/activemq.yaml -n otel-activemq
kubectl apply -f k8s/activemq.yaml -n oneagent-activemq

echo "Deploying RabbitMQ..."
kubectl apply -f k8s/rabbitmq.yaml -n otel-rabbitmq
kubectl apply -f k8s/rabbitmq.yaml -n oneagent-rabbitmq

# Wait for infrastructure to be ready
echo "Waiting for infrastructure to be ready..."
sleep 30

# Deploy application components for otel-kafka namespace
echo "Deploying applications to otel-kafka namespace..."
kubectl apply -f k8s/otel-kafka/billing-service.yaml
kubectl apply -f k8s/otel-kafka/payment-processor.yaml
kubectl apply -f k8s/otel-kafka/invoice-aggregator.yaml
kubectl apply -f k8s/otel-kafka/load-simulator.yaml

# Deploy application components for otel-activemq namespace
echo "Deploying applications to otel-activemq namespace..."
kubectl apply -f k8s/otel-activemq/billing-service.yaml
kubectl apply -f k8s/otel-activemq/payment-processor.yaml
kubectl apply -f k8s/otel-activemq/invoice-aggregator.yaml
kubectl apply -f k8s/otel-activemq/load-simulator.yaml

# Deploy application components for oneagent-kafka namespace
echo "Deploying applications to oneagent-kafka namespace..."
kubectl apply -f k8s/oneagent-kafka/billing-service.yaml
kubectl apply -f k8s/oneagent-kafka/payment-processor.yaml
kubectl apply -f k8s/oneagent-kafka/invoice-aggregator.yaml
kubectl apply -f k8s/oneagent-kafka/load-simulator.yaml

# Deploy application components for oneagent-activemq namespace
echo "Deploying applications to oneagent-activemq namespace..."
kubectl apply -f k8s/oneagent-activemq/billing-service.yaml
kubectl apply -f k8s/oneagent-activemq/payment-processor.yaml
kubectl apply -f k8s/oneagent-activemq/invoice-aggregator.yaml
kubectl apply -f k8s/oneagent-activemq/load-simulator.yaml

# Deploy application components for otel-rabbitmq namespace
echo "Deploying applications to otel-rabbitmq namespace..."
kubectl apply -f k8s/otel-rabbitmq/billing-service.yaml
kubectl apply -f k8s/otel-rabbitmq/payment-processor.yaml
kubectl apply -f k8s/otel-rabbitmq/invoice-aggregator.yaml
kubectl apply -f k8s/otel-rabbitmq/load-simulator.yaml

# Deploy application components for oneagent-rabbitmq namespace
echo "Deploying applications to oneagent-rabbitmq namespace..."
kubectl apply -f k8s/oneagent-rabbitmq/billing-service.yaml
kubectl apply -f k8s/oneagent-rabbitmq/payment-processor.yaml
kubectl apply -f k8s/oneagent-rabbitmq/invoice-aggregator.yaml
kubectl apply -f k8s/oneagent-rabbitmq/load-simulator.yaml

echo "Deployment completed successfully!"
echo "To check the status of the deployments, run:"
echo "kubectl get pods -n otel-kafka"
echo "kubectl get pods -n otel-activemq"
echo "kubectl get pods -n otel-rabbitmq"
echo "kubectl get pods -n oneagent-kafka"
echo "kubectl get pods -n oneagent-activemq"
echo "kubectl get pods -n oneagent-rabbitmq"
