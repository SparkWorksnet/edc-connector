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
    public String handleJsonFile(final String datasetId, final String filename, Path filePath, String jsonContent) throws IOException {
        monitor.info("Processing dataset metadata from: " + filePath.getFileName());
        
        // Parse JSON input into DatasetMetadata object
        DatasetMetadata metadata = objectMapper.readValue(jsonContent, DatasetMetadata.class);
        
        // Validate required fields
        if (datasetId == null || datasetId.isEmpty()) {
            throw new IOException("Dataset ID is required in JSON metadata");
        }
        
        // Get current date for issued/modified if not provided
        String currentDate = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        String issuedDate = metadata.getIssued() != null ? metadata.getIssued() : currentDate;
        String modifiedDate = metadata.getModified() != null ? metadata.getModified() : currentDate;
        
        // Build DCAT-AP Turtle body
        String turtleBody = buildDcatTurtle(datasetId, metadata, issuedDate, modifiedDate);
        
        monitor.info(turtleBody);
        
        monitor.debug("Generated DCAT-AP Turtle:\n" + turtleBody);
        
        // Build the URL with catalogue query parameter if provided
        String url = apiUrl + "/" + datasetId + "?catalogue=" + this.catalogueId;
        
        // Build the HTTP request
        RequestBody requestBody = RequestBody.create(turtleBody, TURTLE);
        Request.Builder requestBuilder = new Request.Builder().url(url).put(requestBody).header("Content-Type", "text/turtle").header("Accept", "application/json");
        
        // Add API key if configured
        if (apiKey != null && !apiKey.isEmpty()) {
            requestBuilder.header("X-API-Key", apiKey);
        }
        
        Request request = requestBuilder.build();
        
        monitor.info("Sending DCAT-AP dataset to Piveau Hub Repo: " + apiUrl);
        monitor.info("  Dataset ID: " + datasetId);
        
        // Execute the request
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                monitor.info("✓ Dataset registered successfully in Piveau Hub");
                monitor.info("  Response code: " + response.code());
                if (!responseBody.isEmpty()) {
                    monitor.debug("  Response body: " + responseBody);
                }
                return datasetId;
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
    private String buildDcatTurtle(String datasetId, DatasetMetadata metadata, String issuedDate, String modifiedDate) {
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
        turtle.append("    dct:title               \"").append(escapeString(metadata.getTitle())).append("\"@en ;\n");
        turtle.append("    dct:description         \"").append(escapeString(metadata.getDescription())).append("\"@en ;\n");
        turtle.append("    dct:issued              \"").append(issuedDate).append("\"^^xsd:date ;\n");
        turtle.append("    dct:modified            \"").append(modifiedDate).append("\"^^xsd:date ;\n");
        turtle.append("    dcat:theme              <http://publications.europa.eu/resource/authority/data-theme/").append(metadata.getTheme()).append("> ;\n");

        // Add keywords if present
        String keywords = formatKeywords(metadata.getKeywords());
        if (keywords != null && !keywords.isEmpty()) {
            turtle.append("    dcat:keyword            ").append(keywords).append(" ;\n");
        }

        // Add columns (variable measured) if present
        if (metadata.getColumns() != null && !metadata.getColumns().isEmpty()) {
            turtle.append("    schema:variableMeasured ");
            for (int i = 0; i < metadata.getColumns().size(); i++) {
                if (i > 0) {
                    turtle.append(", ");
                }
                turtle.append("\"").append(escapeString(metadata.getColumns().get(i))).append("\"");
            }
            turtle.append(" ;\n");
        }

        // Add publisher if present
        if (metadata.getPublisher() != null && !metadata.getPublisher().isEmpty()) {
            turtle.append("    dct:publisher           [ a foaf:Agent ; foaf:name \"").append(escapeString(metadata.getPublisher())).append("\" ] ;\n");
        }

//        // Add record count if present
//        if (metadata.getRecordCount() != null && !metadata.getRecordCount().isEmpty()) {
//            turtle.append("    schema:numberOfItems    \"").append(escapeString(metadata.getRecordCount())).append("\" ;\n");
//        }

//        // Add number of files if present
//        if (metadata.getNumber_of_files() != null) {
//            turtle.append("    schema:workExample      [ a schema:DataDownload ; schema:numberOfItems ").append(metadata.getNumber_of_files()).append(" ] ;\n");
//        }

        // Add license (last line, no semicolon)
        turtle.append("    dct:license             <").append(metadata.getLicense()).append("> .\n");

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
    
    /**
     * Create a distribution for a file in an existing Piveau dataset.
     * The distribution represents the actual data file (CSV, etc.) associated with the dataset.
     *
     * @param datasetId  the ID of the dataset (from dirName)
     * @param fileName   the name of the file being uploaded
     * @return the distribution ID
     * @throws IOException if the API call fails
     */
    public String createDistribution(String datasetId, String fileName) throws IOException {
        if (datasetId == null || datasetId.isEmpty()) {
            throw new IOException("Dataset ID is required to create distribution");
        }

        if (fileName == null || fileName.isEmpty()) {
            throw new IOException("File name is required to create distribution");
        }

        monitor.info("Creating distribution for dataset: " + datasetId);
        monitor.info("  File: " + fileName);

        // Generate distribution ID from filename (remove extension and sanitize)
        String distributionId = generateDistributionId(fileName);

        // Get current date for issued/modified
        String currentDate = LocalDate.now().format(DateTimeFormatter.ISO_DATE);

        // Detect format and media type from file extension
        String format = detectFormat(fileName);
        String mediaType = detectMediaType(fileName);

        // Build DCAT-AP Turtle body for the distribution
        String turtleBody = buildDistributionTurtle(datasetId, distributionId, fileName, format, mediaType, currentDate);

        monitor.info("Generated Distribution Turtle:\n" + turtleBody);

        // Build the URL for distribution creation
        String url = apiUrl + "/" + datasetId + "/distributions";

        // Build the HTTP request
        RequestBody requestBody = RequestBody.create(turtleBody, TURTLE);
        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .post(requestBody)
                .header("Content-Type", "text/turtle")
                .header("Accept", "application/json");

        // Add API key if configured
        if (apiKey != null && !apiKey.isEmpty()) {
            requestBuilder.header("X-API-Key", apiKey);
        }

        Request request = requestBuilder.build();

        monitor.info("Sending distribution to Piveau Hub Repo: " + url);

        // Execute the request
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                monitor.info("✓ Distribution created successfully in Piveau Hub");
                monitor.info("  Response code: " + response.code());
                monitor.info("  Distribution ID: " + distributionId);
                if (!responseBody.isEmpty()) {
                    monitor.debug("  Response body: " + responseBody);
                }
                return distributionId;
            } else {
                String errorBody = response.body() != null ? response.body().string() : "No error details";
                String errorMessage = String.format(
                        "Failed to create distribution in Piveau Hub (HTTP %d): %s",
                        response.code(),
                        errorBody
                );
                monitor.severe("✗ " + errorMessage);
                throw new IOException(errorMessage);
            }
        } catch (IOException e) {
            monitor.severe("✗ Failed to communicate with Piveau Hub Repo API", e);
            throw e;
        }
    }

    /**
     * Build DCAT-AP Turtle representation of a distribution.
     */
    private String buildDistributionTurtle(String datasetId, String distributionId,
                                          String fileName, String format,
                                          String mediaType, String issuedDate) {
        StringBuilder turtle = new StringBuilder();

        // Add prefixes
        turtle.append("@prefix dcat:   <http://www.w3.org/ns/dcat#> .\n");
        turtle.append("@prefix dct:    <http://purl.org/dc/terms/> .\n");
        turtle.append("@prefix xsd:    <http://www.w3.org/2001/XMLSchema#> .\n\n");

        // Build distribution URI
        String distributionUri = apiUrl + "/" + datasetId + "/distributions/" + distributionId;

        // Add distribution definition
        turtle.append("<").append(distributionUri).append(">\n");
        turtle.append("    a                       dcat:Distribution ;\n");
        turtle.append("    dct:title               \"").append(escapeString(fileName)).append("\"@en ;\n");
        turtle.append("    dct:description         \"Data distribution for ").append(escapeString(fileName)).append("\"@en ;\n");
        turtle.append("    dcat:accessURL          <").append(distributionUri).append("> ;\n");
        turtle.append("    dct:format              \"").append(format).append("\" ;\n");
        turtle.append("    dcat:mediaType          \"").append(mediaType).append("\" ;\n");
        turtle.append("    dct:issued              \"").append(issuedDate).append("\"^^xsd:date ;\n");
        turtle.append("    dct:modified            \"").append(issuedDate).append("\"^^xsd:date .\n");

        return turtle.toString();
    }

    /**
     * Generate a distribution ID from a filename.
     * Removes file extension and sanitizes the name.
     */
    private String generateDistributionId(String fileName) {
        // Remove file extension
        int lastDot = fileName.lastIndexOf('.');
        String baseName = lastDot > 0 ? fileName.substring(0, lastDot) : fileName;

        // Sanitize: replace non-alphanumeric characters with hyphens
        return baseName.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", ""); // Remove leading/trailing hyphens
    }

    /**
     * Detect format from file extension.
     */
    private String detectFormat(String fileName) {
        String extension = getFileExtension(fileName).toLowerCase();
        switch (extension) {
            case "csv":
                return "CSV";
            case "json":
                return "JSON";
            case "xml":
                return "XML";
            case "xlsx":
            case "xls":
                return "XLSX";
            case "pdf":
                return "PDF";
            default:
                return extension.toUpperCase();
        }
    }

    /**
     * Detect media type from file extension.
     */
    private String detectMediaType(String fileName) {
        String extension = getFileExtension(fileName).toLowerCase();
        switch (extension) {
            case "csv":
                return "text/csv";
            case "json":
                return "application/json";
            case "xml":
                return "application/xml";
            case "xlsx":
                return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "xls":
                return "application/vnd.ms-excel";
            case "pdf":
                return "application/pdf";
            default:
                return "application/octet-stream";
        }
    }

    /**
     * Get file extension from filename.
     */
    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(lastDot + 1) : "";
    }
}
