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

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import net.sparkworks.edc.extensions.sink.piveau.common.PiveauApiHandler;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSink;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSource;
import org.eclipse.edc.connector.dataplane.spi.pipeline.StreamResult;
import org.eclipse.edc.spi.monitor.Monitor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Data sink that routes files based on extension:
 * - .json files: Register dataset to Piveau Hub Repo API (consume, don't forward)
 * - .csv files: Upload to a MinIO / S3-compatible bucket
 */
public class PiveauDataSink implements DataSink {
    private final MinioClient minioClient;
    private final String bucketName;
    private final String prefix;
    private final Monitor monitor;
    private final PiveauApiHandler piveauApiHandler;
    private final ExecutorService executorService;
    private final ConnectionFactory rabbitConnectionFactory;
    private final String rabbitQueue;
    private final String httpDestinationUrl;
    private final String authKey;
    private final String experimentPrefix;

    public PiveauDataSink(MinioClient minioClient, String bucketName, String prefix,
                          PiveauApiHandler piveauApiHandler, Monitor monitor, ExecutorService executorService,
                          ConnectionFactory rabbitConnectionFactory, String rabbitQueue,
                          String httpDestinationUrl, String authKey, String experimentPrefix) {
        this.minioClient = minioClient;
        this.bucketName = bucketName;
        this.prefix = prefix != null ? prefix : "";
        this.piveauApiHandler = piveauApiHandler;
        this.monitor = monitor;
        this.executorService = executorService;
        this.rabbitConnectionFactory = rabbitConnectionFactory;
        this.rabbitQueue = rabbitQueue;
        this.httpDestinationUrl = httpDestinationUrl;
        this.authKey = authKey;
        this.experimentPrefix = experimentPrefix;
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
        String dirName = extractDirName(part.name());
        String fileName = extractFileName(part.name());
        String experimentId = experimentPrefix + "-" + dirName;
        String destinationBucket = experimentPrefix == null ? bucketName : experimentPrefix;
        monitor.info("════════════════════════════════════════════════");
        monitor.info("Part Name: " + part.name());
        monitor.info("JSON file detected: " + fileName);
        monitor.info("experimentPrefix: " + experimentPrefix);
        monitor.info("ExperimentId: " + experimentId);
        monitor.info("DestinationBucket: " + destinationBucket);
        monitor.info("Registering dataset to Piveau Hub Repo API");
        monitor.info("File will NOT be forwarded to downstream");
        monitor.info("════════════════════════════════════════════════");
        
        try {
            // Read JSON content once — used for both Piveau registration and MinIO upload
            byte[] fileBytes;
            try (var inputStream = part.openStream()) {
                fileBytes = inputStream.readAllBytes();
            }
            String jsonContent = new String(fileBytes);

            // Register to Piveau Hub Repo
            if (piveauApiHandler != null) {
                try {
                    String datasetId = piveauApiHandler.handleJsonFile(experimentId, fileName, destinationBucket, Path.of(fileName), jsonContent);
                    monitor.info("✓ Dataset registered to Piveau Hub Repo: " + datasetId);
                } catch (IOException e) {
                    monitor.warning("⚠ Failed to register dataset '" + experimentId + "' to Piveau: " + e.getMessage() + " — scheduling retry");
                    piveauApiHandler.schedulePendingDataset(experimentId, fileName, destinationBucket, jsonContent);
                }
            } else {
                monitor.warning("⚠ Piveau API handler is not configured, skipping registration");
            }

            // Upload JSON file to MinIO
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(destinationBucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(destinationBucket).build());
                monitor.info("Created bucket: " + destinationBucket);
            }
            String objectKey = buildObjectKey(experimentId + "-" + fileName);
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(destinationBucket)
                    .object(objectKey)
                    .stream(new ByteArrayInputStream(fileBytes), fileBytes.length, -1)
                    .contentType("application/json")
                    .build()
            );
            monitor.info("✓ Uploaded '" + objectKey + "' (" + fileBytes.length + " bytes) to bucket '" + destinationBucket + "'");

        } catch (Exception e) {
            monitor.severe("✗ Failed to upload JSON file to MinIO: " + fileName, e);
        }
    }
    
    private void handleCsvFile(DataSource.Part part) {
        String dirName = extractDirName(part.name());
        String fileName = extractFileName(part.name());
        String experimentId = experimentPrefix + "-" + dirName;
        String destinationBucket = experimentPrefix == null ? bucketName : experimentPrefix;
        monitor.info("════════════════════════════════════════════════");
        monitor.info("Registering dataset to Data Lake (s3):");
        monitor.info("Part Name: " + part.name());
        monitor.info("CSV file detected: " + fileName);
        monitor.info("ExperimentId: " + experimentId);
        monitor.info("DestinationBucket: " + destinationBucket);
        monitor.info("════════════════════════════════════════════════");

        try {
            // Ensure bucket exists
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(destinationBucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(destinationBucket).build());
                monitor.info("Created bucket: " + destinationBucket);
            }

            try (var inputStream = part.openStream()) {
                byte[] fileContent = inputStream.readAllBytes();

                // Create distribution in Piveau for this file (only if dataset is already registered)
                if (piveauApiHandler != null && dirName != null) {
                    if (piveauApiHandler.datasetExists(experimentId, destinationBucket)) {
                        try {
                            String distributionId = piveauApiHandler.createDistribution(experimentId, fileName);
                            monitor.info("✓ Distribution created in Piveau: " + distributionId);
                        } catch (IOException e) {
                            monitor.warning("⚠ Failed to create distribution in Piveau: " + e.getMessage());
                            // Continue with upload even if distribution creation fails
                        }
                    } else {
                        piveauApiHandler.schedulePendingDistribution(experimentId, fileName, destinationBucket);
                    }
                } else {
                    monitor.warning("⚠ Piveau API handler not configured or dataset ID not available, skipping distribution creation");
                }

                // Build the object key: optional prefix + original part path
                String objectKey = buildObjectKey(experimentId + "-" + fileName);

                minioClient.putObject(
                    PutObjectArgs.builder()
                        .bucket(destinationBucket)
                        .object(objectKey)
                        .stream(new ByteArrayInputStream(fileContent), fileContent.length, -1)
                        .contentType("application/octet-stream")
                        .build()
                );

                monitor.info("✓ Uploaded '" + objectKey + "' (" + fileContent.length + " bytes) to bucket '" + destinationBucket + "'");

                sendRabbitNotification(part.name(), "SUCCESS");
            }

        } catch (Exception e) {
            monitor.warning("⚠ MinIO upload failed for '" + fileName + "', falling back to HTTP: " + e.getMessage());
            if (httpDestinationUrl != null && !httpDestinationUrl.isEmpty()) {
                handleCsvFileOld(part);
            } else {
                monitor.severe("✗ MinIO failed and no HTTP fallback configured for: " + fileName);
                sendRabbitNotification(part.name(), "FAILED");
            }
        }
    }

    private void sendRabbitNotification(String requestId, String status) {
        if (rabbitConnectionFactory == null || rabbitQueue == null || rabbitQueue.isEmpty()) {
            monitor.warning("RabbitMQ not configured, skipping notification");
            return;
        }
        try (Connection connection = rabbitConnectionFactory.newConnection();
             Channel channel = connection.createChannel()) {
            channel.queueDeclare(rabbitQueue, true, false, false, null);
            String payload = "{\"request_id\": \"" + requestId + "\", \"status\": \"" + status + "\"}";
            channel.basicPublish("", rabbitQueue, null, payload.getBytes());
            monitor.info("✓ RabbitMQ notification sent to queue '" + rabbitQueue + "': " + payload);
        } catch (Exception e) {
            monitor.warning("⚠ Failed to send RabbitMQ notification: " + e.getMessage());
        }
    }

    private void handleCsvFileOld(DataSource.Part part) {
        String dirName = extractDirName(part.name());
        String fileName = extractFileName(part.name());
        String experimentId = experimentPrefix + "-" + dirName;
        String destinationBucket = experimentPrefix == null ? bucketName : experimentPrefix;
        monitor.info("════════════════════════════════════════════════");
        monitor.info("Registering dataset to Data Lake (http):");
        monitor.info("Part Name: " + part.name());
        monitor.info("CSV file detected: " + fileName);
        monitor.info("ExperimentId: " + experimentId);
        monitor.info("DestinationBucket: " + destinationBucket);
        monitor.info("════════════════════════════════════════════════");

        try (var inputStream = part.openStream()) {
            byte[] fileContent = inputStream.readAllBytes();

            // Create distribution in Piveau for this file
            if (piveauApiHandler != null && dirName != null) {
                try {
                    String distributionId = piveauApiHandler.createDistribution(experimentId, fileName);
                    monitor.info("✓ Distribution created in Piveau: " + distributionId);
                } catch (IOException e) {
                    monitor.warning("⚠ Failed to create distribution in Piveau: " + e.getMessage());
                }
            }

            var requestBody = RequestBody.create(fileContent, MediaType.parse("application/octet-stream"));

            var requestBuilder = new Request.Builder()
                .url(httpDestinationUrl)
                .post(requestBody)
                .header("X-File-Path", experimentId)
                .header("X-File-Name", fileName)
                .header("X-file-bucket", destinationBucket)
                .header("Content-Type", "application/octet-stream");

            if (authKey != null && !authKey.isEmpty()) {
                requestBuilder.header("Authorization", "Bearer " + authKey);
            }

            try (var response = new OkHttpClient().newCall(requestBuilder.build()).execute()) {
                if (response.isSuccessful()) {
                    monitor.info("✓ Successfully forwarded file: " + fileName + " (status: " + response.code() + ")");
                    sendRabbitNotification(part.name(), "SUCCESS");
                } else {
                    monitor.warning("⚠ HTTP forward failed for file: " + fileName + " (status: " + response.code() + ")");
                    sendRabbitNotification(part.name(), "FAILED");
                }
            }

        } catch (IOException e) {
            monitor.severe("✗ Failed to process CSV file (HTTP): " + fileName, e);
            sendRabbitNotification(part.name(), "FAILED");
        }
    }

    private String buildObjectKey(String partName) {
        if (prefix == null || prefix.isEmpty()) {
            return partName;
        }
        String normalizedPrefix = prefix.endsWith("/") ? prefix : prefix + "/";
        return normalizedPrefix + partName;
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
    
    /**
     * Extract dirname from full path
     */
    private String extractDirName(String fullPath) {
        if (fullPath == null) {
            return null;
        }
        final String[] parts = fullPath.split("/");
        if (parts.length > 1) {
            return parts[parts.length - 2];
        } else {
            return null;
        }
    }
}
