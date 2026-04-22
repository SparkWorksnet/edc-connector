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

package net.sparkworks.edc.extensions.sink.minio;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSink;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSource;
import org.eclipse.edc.connector.dataplane.spi.pipeline.StreamResult;
import org.eclipse.edc.spi.monitor.Monitor;

import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Data sink that uploads transferred files to a MinIO bucket.
 * Accepts data parts from any data source and stores each part as a MinIO object,
 * preserving the part name as the object key (optionally prefixed).
 */
public class MinioDataSink implements DataSink {

    private final MinioClient minioClient;
    private final String bucketName;
    private final String prefix;
    private final Monitor monitor;
    private final ExecutorService executorService;

    public MinioDataSink(MinioClient minioClient, String bucketName, String prefix, Monitor monitor, ExecutorService executorService) {
        this.minioClient = minioClient;
        this.bucketName = bucketName;
        this.prefix = prefix != null ? prefix : "";
        this.monitor = monitor;
        this.executorService = executorService;
    }

    @Override
    public CompletableFuture<StreamResult<Object>> transfer(DataSource source) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ensureBucketExists();

                var streamResult = source.openPartStream();

                if (streamResult.failed()) {
                    monitor.severe("Failed to open part stream: " + streamResult.getFailureDetail());
                    return StreamResult.<Object>error(streamResult.getFailureDetail());
                }

                var partStream = streamResult.getContent();

                partStream.forEach(part -> {
                    String objectKey = buildObjectKey(part.name());
                    monitor.info("Uploading part '" + part.name() + "' to MinIO as '" + objectKey + "'");

                    try (InputStream inputStream = part.openStream()) {
                        byte[] content = inputStream.readAllBytes();

                        minioClient.putObject(
                            PutObjectArgs.builder()
                                .bucket(bucketName)
                                .object(objectKey)
                                .stream(new java.io.ByteArrayInputStream(content), content.length, -1)
                                .contentType("application/octet-stream")
                                .build()
                        );

                        monitor.info("Successfully uploaded '" + objectKey + "' (" + content.length + " bytes) to bucket '" + bucketName + "'");

                    } catch (Exception e) {
                        monitor.severe("Failed to upload part '" + part.name() + "' to MinIO", e);
                        throw new RuntimeException("MinIO upload failed for object: " + objectKey, e);
                    }
                });

                partStream.close();

                return StreamResult.success();

            } catch (Exception e) {
                monitor.severe("MinIO data sink transfer failed", e);
                return StreamResult.<Object>error("Transfer failed: " + e.getMessage());
            }
        }, executorService);
    }

    private void ensureBucketExists() throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
        if (!exists) {
            monitor.info("Bucket '" + bucketName + "' does not exist, creating it");
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            monitor.info("Bucket '" + bucketName + "' created successfully");
        }
    }

    private String buildObjectKey(String partName) {
        if (prefix == null || prefix.isEmpty()) {
            return partName;
        }
        String normalizedPrefix = prefix.endsWith("/") ? prefix : prefix + "/";
        return normalizedPrefix + partName;
    }
}