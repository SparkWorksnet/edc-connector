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

import com.rabbitmq.client.ConnectionFactory;
import io.minio.MinioClient;

import java.net.URI;
import net.sparkworks.edc.extensions.sink.piveau.common.PiveauApiHandler;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSink;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSinkFactory;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutorService;

/**
 * Factory for creating PiveauDataSink instances.
 */
public class PiveauDataSinkFactory implements DataSinkFactory {

    private final Monitor monitor;
    private final ExecutorService executorService;
    private final ConnectionFactory rabbitConnectionFactory;
    private final String rabbitQueue;

    public PiveauDataSinkFactory(Monitor monitor, ExecutorService executorService,
                                 ConnectionFactory rabbitConnectionFactory, String rabbitQueue) {
        this.monitor = monitor;
        this.executorService = executorService;
        this.rabbitConnectionFactory = rabbitConnectionFactory;
        this.rabbitQueue = rabbitQueue;
    }

    @Override
    public String supportedType() {
        return "PiveauData";
    }

    @Override
    public @NotNull Result<Void> validateRequest(DataFlowStartMessage request) {
        var dest = request.getDestinationDataAddress();
        if (dest.getStringProperty("endpoint") == null) {
            return Result.failure("MinIO endpoint is required in destination data address");
        }
        if (dest.getStringProperty("bucketName") == null) {
            return Result.failure("MinIO bucketName is required in destination data address");
        }
        if (dest.getStringProperty("accessKey") == null) {
            return Result.failure("MinIO accessKey is required in destination data address");
        }
        if (dest.getStringProperty("secretKey") == null) {
            return Result.failure("MinIO secretKey is required in destination data address");
        }
        return Result.success();
    }

    @Override
    public DataSink createSink(DataFlowStartMessage request) {
        monitor.info("Creating PiveauDataSink for request: " + request.getId());

        var dest = request.getDestinationDataAddress();
        String endpoint   = dest.getStringProperty("endpoint");
        String bucketName = dest.getStringProperty("bucketName");
        String accessKey  = dest.getStringProperty("accessKey");
        String secretKey  = dest.getStringProperty("secretKey");
        String prefix     = dest.getStringProperty("prefix", "");

        String piveauUrl       = dest.getStringProperty("piveauUrl");
        String piveauApiKey    = dest.getStringProperty("piveauApiKey");
        String piveauCatalogue = dest.getStringProperty("piveauCatalogue");

        monitor.info("  MinIO endpoint: " + endpoint);
        monitor.info("  MinIO bucket:   " + bucketName);
        monitor.info("  MinIO prefix:   " + (prefix.isEmpty() ? "(root)" : prefix));

        MinioClient minioClient = MinioClient.builder()
            .endpoint(endpoint)
            .credentials(accessKey, secretKey)
            .build();

        PiveauApiHandler piveauApiHandler = new PiveauApiHandler(piveauUrl, piveauApiKey, piveauCatalogue, monitor);

        String httpDestinationUrl = dest.getStringProperty("httpDestinationUrl");
        String authKey            = dest.getStringProperty("authKey");

        return new PiveauDataSink(minioClient, bucketName, prefix, piveauApiHandler, monitor, executorService, rabbitConnectionFactory, rabbitQueue, httpDestinationUrl, authKey);
    }
}
