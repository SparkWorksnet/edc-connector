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

package net.sparkworks.edc.extensions.sink.piveau;

import net.sparkworks.edc.extensions.sink.piveau.common.PiveauApiHandler;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.eclipse.edc.connector.dataplane.http.spi.HttpDataAddress;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSink;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSource;
import org.eclipse.edc.connector.dataplane.spi.pipeline.StreamResult;
import org.eclipse.edc.http.spi.EdcHttpClient;
import org.eclipse.edc.spi.monitor.Monitor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Data sink that routes files based on extension:
 * - .json files: Register dataset to Piveau Hub Repo API (consume, don't forward)
 * - .csv files: Forward to configured HTTP endpoint
 * - Other files: Forward to configured HTTP endpoint (or ignore based on config)
 */
public class PiveauDataSink implements DataSink {
    private final EdcHttpClient httpClient;
    private final HttpDataAddress destinationAddress;
    private final Monitor monitor;
    private final PiveauApiHandler piveauApiHandler;
    private final ExecutorService executorService;
    private final String authKey;
    
    public PiveauDataSink(EdcHttpClient httpClient, HttpDataAddress destinationAddress, Monitor monitor, ExecutorService executorService) {
        this.httpClient = httpClient;
        this.monitor = monitor;
        this.destinationAddress = destinationAddress;
        this.piveauApiHandler = new PiveauApiHandler(
                destinationAddress.getStringProperty("piveauUrl"),
                destinationAddress.getStringProperty("piveauApiKey"),
                destinationAddress.getStringProperty("piveauCatalogue"),
                monitor);
        this.executorService = executorService;
        
        // Extract auth token from destination address properties
        this.authKey = destinationAddress.getAuthKey();
        if (authKey != null && !authKey.isEmpty()) {
            monitor.info("Auth token configured for HTTP data sink");
        }
    }
    
    @Override
    public CompletableFuture<StreamResult<Object>> transfer(DataSource source) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                monitor.info("PiveauRoutingDataSink starting transfer");
                
                var streamResult = source.openPartStream();
                if (streamResult.failed()) {
                    monitor.severe("Failed to open source stream: " + streamResult.getFailureDetail());
                    return StreamResult.error("Failed to open source stream: " + streamResult.getFailureDetail());
                }
                
                var stream = streamResult.getContent();
                
                stream.forEach(part -> {
                    String fileName = extractFileName(part.name());
                    monitor.info("Processing file: " + fileName);
                    
                    try {
                        if (fileName.toLowerCase().endsWith(".json")) {
                            handleJsonFile(part);
                        } else if (fileName.toLowerCase().endsWith(".csv")) {
                            handleCsvFile(part);
                        }
                    } catch (Exception e) {
                        monitor.severe("Error processing file: " + fileName, e);
                    }
                });
                
                monitor.info("✓ PiveauRoutingDataSink transfer completed");
                return StreamResult.success();
                
            } catch (Exception e) {
                monitor.severe("PiveauRoutingDataSink transfer failed", e);
                return StreamResult.error("Transfer failed: " + e.getMessage());
            }
        }, executorService);
    }
    
    /**
     * Handle JSON file - register to Piveau Hub Repo API
     */
    private void handleJsonFile(DataSource.Part part) {
        String fileName = extractFileName(part.name());
        monitor.info("════════════════════════════════════════════════");
        monitor.info("JSON file detected: " + fileName);
        monitor.info("Registering dataset to Piveau Hub Repo API");
        monitor.info("File will NOT be forwarded to downstream");
        monitor.info("════════════════════════════════════════════════");
        
        try {
            // Read JSON content
            String jsonContent;
            try (var inputStream = part.openStream()) {
                jsonContent = new String(inputStream.readAllBytes());
            }
            
            // Register to Piveau Hub Repo
            if (piveauApiHandler != null) {
                piveauApiHandler.handleJsonFile(Path.of(fileName), jsonContent);
                monitor.info("✓ Dataset registered to Piveau Hub Repo: " + fileName);
            } else {
                monitor.warning("⚠ Piveau API handler is not configured, skipping registration");
            }
            
        } catch (IOException e) {
            monitor.severe("✗ Failed to process JSON file: " + fileName, e);
        }
    }
    
    private void handleCsvFile(DataSource.Part part) {
        String fileName = extractFileName(part.name());
        monitor.info("════════════════════════════════════════════════");
        monitor.info("CSV file detected: " + fileName);
        monitor.info("Registering dataset to Data Lake");
        monitor.info("════════════════════════════════════════════════");
        
        String filePath = part.name();
        
        try {
            // Read JSON content
            String jsonContent;
            try (var inputStream = part.openStream()) {
                byte[] fileContent = inputStream.readAllBytes();
                
                var requestBody = RequestBody.create(fileContent, MediaType.parse("application/octet-stream"));
                
                // Build HTTP request with custom headers
                var requestBuilder = new Request.Builder().url(destinationAddress.getBaseUrl()).post(requestBody).header("X-File-Path", filePath).header("X-File-Name", extractFileName(filePath)).header("Content-Type", "application/octet-stream");
                
                // Add Authorization header if auth token is configured
                if (authKey != null && !authKey.isEmpty()) {
                    requestBuilder.header("Authorization", "Bearer " + authKey);
                    monitor.debug("Adding Authorization header to request");
                }
                
                var httpRequest = requestBuilder.build();
                
                monitor.info("Sending HTTP POST to: " + destinationAddress.getBaseUrl());
                
                // Execute the HTTP request
                try (var response = httpClient.execute(httpRequest)) {
                    if (response.isSuccessful()) {
                        monitor.info("Successfully transferred file: " + filePath + " (status: " + response.code() + ")");
                    } else {
                        monitor.warning("Failed to transfer file: " + filePath + " (status: " + response.code() + ")");
                    }
                }
            }
            
        } catch (IOException e) {
            monitor.severe("✗ Failed to process CSV file: " + fileName, e);
        }
    }
    
    /**
     * Extract filename from full path
     */
    private String extractFileName(String fullPath) {
        if (fullPath == null) {
            return "unknown";
        }
        int lastSlash = Math.max(fullPath.lastIndexOf('/'), fullPath.lastIndexOf('\\'));
        return lastSlash >= 0 ? fullPath.substring(lastSlash + 1) : fullPath;
    }
}
