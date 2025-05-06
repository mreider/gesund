#!/bin/bash
set -e

# Function to clean up resources in a namespace
cleanup_namespace() {
  local namespace=$1
  echo "Cleaning up resources in namespace: $namespace"
  
  # Delete all deployments
  kubectl delete deployments --all -n $namespace --ignore-not-found=true
  
  # Delete all services
  kubectl delete services --all -n $namespace --ignore-not-found=true
  
  # Delete all pods
  kubectl delete pods --all -n $namespace --ignore-not-found=true
  
  # Delete all PVCs
  kubectl delete pvc --all -n $namespace --ignore-not-found=true
  
  # Delete all secrets
  kubectl delete secrets --all -n $namespace --ignore-not-found=true
  
  echo "Cleanup completed for namespace: $namespace"
}

# Create namespaces
echo "Creating namespaces..."
kubectl apply -f k8s/namespaces.yaml

# Clean up existing resources
echo "Cleaning up existing resources..."
cleanup_namespace "otel-kafka"
cleanup_namespace "otel-activemq"
cleanup_namespace "otel-rabbitmq"
cleanup_namespace "oneagent-kafka"
cleanup_namespace "oneagent-activemq"
cleanup_namespace "oneagent-rabbitmq"

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
