package com.gesund.demo.paymentprocessor.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Component
public class DynatraceMetadataUtil {
    private static final Logger log = LoggerFactory.getLogger(DynatraceMetadataUtil.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String[] METADATA_JSON_FILES = {
            "dt_metadata_e617c525669e072eebe3d0f08212e8f2.json",
            "/var/lib/dynatrace/enrichment/dt_metadata.json",
            "/var/lib/dynatrace/enrichment/dt_host_metadata.json"
    };
    
    private static final String[] METADATA_PROPERTIES_FILES = {
            "dt_metadata_e617c525669e072eebe3d0f08212e8f2.properties",
            "/var/lib/dynatrace/enrichment/dt_metadata.properties"
    };

    private final Map<String, String> enrichmentAttributes;

    public DynatraceMetadataUtil() {
        this.enrichmentAttributes = loadEnrichmentAttributes();
        log.info("Loaded {} Dynatrace metadata attributes", enrichmentAttributes.size());
    }

    private Map<String, String> loadEnrichmentAttributes() {
        Map<String, String> attributes = new HashMap<>();

        // Try to load JSON files
        for (String filePath : METADATA_JSON_FILES) {
            try {
                File file = new File(filePath);
                if (file.exists()) {
                    // Direct file access
                    Map<String, String> fileAttributes = objectMapper.readValue(file, 
                            objectMapper.getTypeFactory().constructMapType(Map.class, String.class, String.class));
                    attributes.putAll(fileAttributes);
                    log.debug("Loaded Dynatrace metadata from JSON file: {}", filePath);
                } else if (filePath.startsWith("/var/lib/dynatrace")) {
                    // Try to read from the absolute path
                    if (Files.exists(Paths.get(filePath))) {
                        Map<String, String> fileAttributes = objectMapper.readValue(
                                Files.readAllBytes(Paths.get(filePath)), 
                                objectMapper.getTypeFactory().constructMapType(Map.class, String.class, String.class));
                        attributes.putAll(fileAttributes);
                        log.debug("Loaded Dynatrace metadata from absolute JSON path: {}", filePath);
                    }
                }
            } catch (IOException e) {
                log.debug("Could not read Dynatrace metadata JSON file: {}", filePath);
            } catch (Exception e) {
                log.warn("Error processing Dynatrace metadata JSON file: {}", filePath, e);
            }
        }
        
        // Try to load Properties files
        for (String filePath : METADATA_PROPERTIES_FILES) {
            try {
                File file = new File(filePath);
                if (file.exists()) {
                    // Direct file access
                    Properties props = new Properties();
                    try (FileInputStream fis = new FileInputStream(file)) {
                        props.load(fis);
                        for (Map.Entry<Object, Object> entry : props.entrySet()) {
                            attributes.put(entry.getKey().toString(), entry.getValue().toString());
                        }
                    }
                    log.debug("Loaded Dynatrace metadata from Properties file: {}", filePath);
                } else if (filePath.startsWith("/var/lib/dynatrace")) {
                    // Try to read from the absolute path
                    if (Files.exists(Paths.get(filePath))) {
                        Properties props = new Properties();
                        try (FileInputStream fis = new FileInputStream(Paths.get(filePath).toFile())) {
                            props.load(fis);
                            for (Map.Entry<Object, Object> entry : props.entrySet()) {
                                attributes.put(entry.getKey().toString(), entry.getValue().toString());
                            }
                        }
                        log.debug("Loaded Dynatrace metadata from absolute Properties path: {}", filePath);
                    }
                }
            } catch (IOException e) {
                log.debug("Could not read Dynatrace metadata Properties file: {}", filePath);
            } catch (Exception e) {
                log.warn("Error processing Dynatrace metadata Properties file: {}", filePath, e);
            }
        }
        
        // Try to load from the special path format in the example
        try {
            String specialPath = "dt_metadata_e617c525669e072eebe3d0f08212e8f2.properties";
            File file = new File(specialPath);
            if (file.exists()) {
                Properties props = new Properties();
                String actualPath = Files.readAllLines(Paths.get(specialPath)).get(0);
                try (FileInputStream fis = new FileInputStream(actualPath)) {
                    props.load(fis);
                    for (Map.Entry<Object, Object> entry : props.entrySet()) {
                        attributes.put(entry.getKey().toString(), entry.getValue().toString());
                    }
                }
                log.debug("Loaded Dynatrace metadata from special path format: {}", actualPath);
            }
        } catch (IOException e) {
            log.debug("Could not read Dynatrace metadata from special path format");
        } catch (Exception e) {
            log.warn("Error processing Dynatrace metadata from special path format", e);
        }

        return attributes;
    }

    public Attributes enrichAttributes(Attributes baseAttributes) {
        if (enrichmentAttributes.isEmpty()) {
            return baseAttributes;
        }

        AttributesBuilder builder = Attributes.builder();
        
        // Add all base attributes
        builder.putAll(baseAttributes);
        
        // Add all enrichment attributes
        for (Map.Entry<String, String> entry : enrichmentAttributes.entrySet()) {
            builder.put(entry.getKey(), entry.getValue());
        }
        
        return builder.build();
    }

    public Map<String, String> getEnrichmentAttributes() {
        return new HashMap<>(enrichmentAttributes);
    }
}
