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

  # Delete all configmaps
  kubectl delete configmaps --all -n $namespace --ignore-not-found=true

  # Delete all daemonsets
  kubectl delete daemonsets --all -n $namespace --ignore-not-found=true
  
  echo "Cleanup completed for namespace: $namespace"
}

# Clean up resources in all relevant namespaces
echo "Cleaning up existing resources..."
cleanup_namespace "dynatrace"
cleanup_namespace "otel-kafka"
cleanup_namespace "otel-activemq"
cleanup_namespace "otel-rabbitmq"
cleanup_namespace "oneagent-kafka"
cleanup_namespace "oneagent-activemq"
cleanup_namespace "oneagent-rabbitmq"

# Delete namespaces
echo "Deleting namespaces..."
kubectl delete -f k8s/namespaces.yaml --ignore-not-found=true
kubectl delete namespace dynatrace --ignore-not-found=true

echo "All specified resources and namespaces have been removed."
