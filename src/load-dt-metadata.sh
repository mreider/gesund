#!/bin/sh
# Script to read Dynatrace metadata attributes from a file and set them as OTEL_RESOURCE_ATTRIBUTES

# Path to the Dynatrace metadata file
DT_METADATA_FILE="/var/lib/dynatrace/enrichment/dt_metadata.properties"

if [ -f "$DT_METADATA_FILE" ]; then
  echo "Loading Dynatrace metadata from $DT_METADATA_FILE"
  
  # Read file and convert to comma-separated string
  # Filter out empty lines and comments (lines starting with #)
  DT_ATTRS=$(cat "$DT_METADATA_FILE" | grep -v "^#" | grep -v "^$" | tr '\n' ',' | sed 's/,$//')
  
  # Append to existing OTEL_RESOURCE_ATTRIBUTES or set if not exists
  if [ -n "$OTEL_RESOURCE_ATTRIBUTES" ]; then
    export OTEL_RESOURCE_ATTRIBUTES="$OTEL_RESOURCE_ATTRIBUTES,$DT_ATTRS"
  else
    export OTEL_RESOURCE_ATTRIBUTES="$DT_ATTRS"
  fi
  
  echo "Added Dynatrace metadata as resource attributes: $DT_ATTRS"
else
  echo "Dynatrace metadata file not found at $DT_METADATA_FILE"
fi
