/*
 *  Copyright (c) 2024 SparkWorks
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       SparkWorks - initial implementation
 *
 */

package net.sparkworks.edc.extensions.sink.piveau.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.eclipse.edc.spi.monitor.Monitor;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Handles interaction with the Piveau Hub Repo API.
 * Registers dataset metadata (DCAT-AP JSON-LD) to the Piveau catalog.
 */
public class PiveauApiHandler {
    
    private static final MediaType TURTLE = MediaType.parse("text/turtle");
    
    private final String apiUrl;
    private final String apiKey;
    private final String catalogueId;
    private final Monitor monitor;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    public PiveauApiHandler(String apiUrl, String apiKey, String catalogueId, Monitor monitor) {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.catalogueId = catalogueId;
        this.monitor = monitor;
        this.objectMapper = new ObjectMapper();
        this.httpClient = new OkHttpClient.Builder().connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS).readTimeout(30, java.util.concurrent.TimeUnit.SECONDS).writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS).build();
        
        monitor.info("PiveauApiHandler initialized");
        monitor.info("  API URL: " + apiUrl);
        monitor.info("  API Key: " + (apiKey != null && !apiKey.isEmpty() ? "***configured***" : "not set"));
        monitor.info("  Catalogue Id: " + catalogueId);
    }
    
    /**
     * Handle a JSON file containing dataset metadata.
     * Parses the JSON, transforms it to DCAT-AP Turtle format, and sends to Piveau Hub Repo API.
     *
     * @param filePath    the path to the JSON file
     * @param jsonContent the content of the JSON file with dataset metadata
     * @throws IOException if parsing or API call fails
     */
    public void handleJsonFile(Path filePath, String jsonContent) throws IOException {
        monitor.info("Processing dataset metadata from: " + filePath.getFileName());
        
        // Parse JSON input into DatasetMetadata object
        DatasetMetadata metadata = objectMapper.readValue(jsonContent, DatasetMetadata.class);
        
        // Validate required fields
        if (metadata.getDatasetId() == null || metadata.getDatasetId().isEmpty()) {
            throw new IOException("Dataset ID is required in JSON metadata");
        }
        
        // Get current date for issued/modified if not provided
        String currentDate = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        String issuedDate = metadata.getIssued() != null ? metadata.getIssued() : currentDate;
        String modifiedDate = metadata.getModified() != null ? metadata.getModified() : currentDate;
        
        // Format keywords
        String keywords = formatKeywords(metadata.getKeywords());
        
        // Build DCAT-AP Turtle body
        String turtleBody = buildDcatTurtle(metadata.getDatasetId(), metadata.getTitle(), metadata.getDescription(), issuedDate, modifiedDate, metadata.getTheme(), keywords, metadata.getLicense());
        
        monitor.debug("Generated DCAT-AP Turtle:\n" + turtleBody);
        
        // Build the URL with catalogue query parameter if provided
        String url = apiUrl + "/" + metadata.getDatasetId() + "?catalogue=" + this.catalogueId;
        
        // Build the HTTP request
        RequestBody requestBody = RequestBody.create(turtleBody, TURTLE);
        Request.Builder requestBuilder = new Request.Builder().url(url).put(requestBody).header("Content-Type", "text/turtle").header("Accept", "application/json");
        
        // Add API key if configured
        if (apiKey != null && !apiKey.isEmpty()) {
            requestBuilder.header("X-API-Key", apiKey);
        }
        
        Request request = requestBuilder.build();
        
        monitor.info("Sending DCAT-AP dataset to Piveau Hub Repo: " + apiUrl);
        monitor.info("  Dataset ID: " + metadata.getDatasetId());
        
        // Execute the request
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                monitor.info("✓ Dataset registered successfully in Piveau Hub");
                monitor.info("  Response code: " + response.code());
                if (!responseBody.isEmpty()) {
                    monitor.debug("  Response body: " + responseBody);
                }
            } else {
                String errorBody = response.body() != null ? response.body().string() : "No error details";
                String errorMessage = String.format("Failed to register dataset in Piveau Hub (HTTP %d): %s", response.code(), errorBody);
                monitor.severe("✗ " + errorMessage);
                throw new IOException(errorMessage);
            }
        } catch (IOException e) {
            monitor.severe("✗ Failed to communicate with Piveau Hub Repo API", e);
            throw e;
        }
    }
    
    /**
     * Build DCAT-AP Turtle representation of the dataset.
     */
    private String buildDcatTurtle(String datasetId, String title, String description, String issuedDate, String modifiedDate, String theme, String keywords, String license) {
        StringBuilder turtle = new StringBuilder();
        
        // Add prefixes
        turtle.append("@prefix dcat:   <http://www.w3.org/ns/dcat#> .\n");
        turtle.append("@prefix dct:    <http://purl.org/dc/terms/> .\n");
        turtle.append("@prefix foaf:   <http://xmlns.com/foaf/0.1/> .\n");
        turtle.append("@prefix vcard:  <http://www.w3.org/2006/vcard/ns#> .\n");
        turtle.append("@prefix adms:   <http://www.w3.org/ns/adms#> .\n");
        turtle.append("@prefix schema: <http://schema.org/> .\n");
        turtle.append("@prefix skos:   <http://www.w3.org/2004/02/skos/core#> .\n");
        turtle.append("@prefix prov:   <http://www.w3.org/ns/prov#> .\n");
        turtle.append("@prefix xsd:    <http://www.w3.org/2001/XMLSchema#> .\n\n");
        
        // Build dataset URI
        String datasetUri = apiUrl + "/" + datasetId;
        
        // Add dataset definition
        turtle.append("<").append(datasetUri).append(">\n");
        turtle.append("    a                       dcat:Dataset ;\n");
        turtle.append("    dct:title               \"").append(escapeString(title)).append("\"@en ;\n");
        turtle.append("    dct:description         \"").append(escapeString(description)).append("\"@en ;\n");
        turtle.append("    dct:issued              \"").append(issuedDate).append("\"^^xsd:date ;\n");
        turtle.append("    dct:modified            \"").append(modifiedDate).append("\"^^xsd:date ;\n");
        turtle.append("    dcat:theme              <http://publications.europa.eu/resource/authority/data-theme/").append(theme).append("> ;\n");
        
        // Add keywords if present
        if (keywords != null && !keywords.isEmpty()) {
            turtle.append("    dcat:keyword            ").append(keywords).append(" ;\n");
        }
        
        // Add license (last line, no semicolon)
        turtle.append("    dct:license             <").append(license).append("> .\n");
        
        return turtle.toString();
    }
    
    /**
     * Format keywords list into comma-separated quoted strings for Turtle.
     */
    private String formatKeywords(List<String> keywordsList) {
        if (keywordsList == null || keywordsList.isEmpty()) {
            return "";
        }
        
        StringBuilder keywords = new StringBuilder();
        for (int i = 0; i < keywordsList.size(); i++) {
            if (i > 0) {
                keywords.append(", ");
            }
            keywords.append("\"").append(escapeString(keywordsList.get(i))).append("\"");
        }
        return keywords.toString();
    }
    
    /**
     * Escape special characters in strings for Turtle format.
     */
    private String escapeString(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
    
    /**
     * Test the connection to the Piveau Hub Repo API.
     *
     * @return true if the API is reachable, false otherwise
     */
    public boolean testConnection() {
        try {
            Request.Builder requestBuilder = new Request.Builder().url(apiUrl).head();  // HEAD request to check connectivity
            
            if (apiKey != null && !apiKey.isEmpty()) {
                requestBuilder.header("X-API-Key", apiKey);
            }
            
            Request request = requestBuilder.build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                boolean isSuccessful = response.isSuccessful() || response.code() == 405; // 405 = Method Not Allowed is OK for HEAD
                if (isSuccessful) {
                    monitor.info("✓ Piveau Hub Repo API is reachable");
                } else {
                    monitor.warning("⚠ Piveau Hub Repo API returned status: " + response.code());
                }
                return isSuccessful;
            }
        } catch (Exception e) {
            monitor.warning("⚠ Failed to connect to Piveau Hub Repo API: " + e.getMessage());
            return false;
        }
    }
}
