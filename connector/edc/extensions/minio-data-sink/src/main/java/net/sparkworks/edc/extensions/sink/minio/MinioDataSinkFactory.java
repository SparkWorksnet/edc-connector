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

import io.minio.MinioClient;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSink;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSinkFactory;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutorService;

/**
 * Factory that creates {@link MinioDataSink} instances for destination addresses of type {@code MinioData}.
 */
public class MinioDataSinkFactory implements DataSinkFactory {

    private final Monitor monitor;
    private final ExecutorService executorService;

    public MinioDataSinkFactory(Monitor monitor, ExecutorService executorService) {
        this.monitor = monitor;
        this.executorService = executorService;
    }

    @Override
    public String supportedType() {
        return "MinioData";
    }

    @Override
    public @NotNull Result<Void> validateRequest(DataFlowStartMessage request) {
        var dest = request.getDestinationDataAddress();

        String endpoint = dest.getStringProperty("endpoint");
        String bucketName = dest.getStringProperty("bucketName");
        String accessKey = dest.getStringProperty("accessKey");
        String secretKey = dest.getStringProperty("secretKey");

        if (endpoint == null || endpoint.isEmpty()) {
            return Result.failure("MinIO endpoint is required in destination data address");
        }
        if (bucketName == null || bucketName.isEmpty()) {
            return Result.failure("MinIO bucketName is required in destination data address");
        }
        if (accessKey == null || accessKey.isEmpty()) {
            return Result.failure("MinIO accessKey is required in destination data address");
        }
        if (secretKey == null || secretKey.isEmpty()) {
            return Result.failure("MinIO secretKey is required in destination data address");
        }

        return Result.success();
    }

    @Override
    public DataSink createSink(DataFlowStartMessage request) {
        var dest = request.getDestinationDataAddress();

        String endpoint = dest.getStringProperty("endpoint");
        String bucketName = dest.getStringProperty("bucketName");
        String accessKey = dest.getStringProperty("accessKey");
        String secretKey = dest.getStringProperty("secretKey");
        String prefix = dest.getStringProperty("prefix", "");

        monitor.info("Creating MinioDataSink");
        monitor.info("  Endpoint:   " + endpoint);
        monitor.info("  Bucket:     " + bucketName);
        monitor.info("  Prefix:     " + (prefix.isEmpty() ? "(root)" : prefix));

        MinioClient minioClient = MinioClient.builder()
            .endpoint(endpoint)
            .credentials(accessKey, secretKey)
            .build();

        return new MinioDataSink(minioClient, bucketName, prefix, monitor, executorService);
    }
}