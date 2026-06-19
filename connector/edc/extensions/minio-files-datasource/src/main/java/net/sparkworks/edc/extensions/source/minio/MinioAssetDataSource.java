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

package net.sparkworks.edc.extensions.source.minio;

import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.messages.Item;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSource;
import org.eclipse.edc.connector.dataplane.spi.pipeline.StreamResult;
import org.eclipse.edc.spi.monitor.Monitor;

import java.io.Closeable;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Data source that reads existing objects from a MinIO bucket and returns them
 * for transfer. One-shot read — no polling or file watching.
 */
public class MinioAssetDataSource implements DataSource, Closeable {

    private final MinioClient minioClient;
    private final String bucketName;
    private final String prefix;
    private final Monitor monitor;

    public MinioAssetDataSource(MinioClient minioClient, String bucketName, String prefix, Monitor monitor) {
        this.minioClient = minioClient;
        this.bucketName = bucketName;
        this.prefix = prefix != null ? prefix : "";
        this.monitor = monitor;

        monitor.info("Creating MinioAssetDataSource");
        monitor.info("  Bucket: " + bucketName);
        monitor.info("  Prefix: " + (this.prefix.isEmpty() ? "(root)" : this.prefix));
    }

    @Override
    public StreamResult<Stream<Part>> openPartStream() {
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder()
                    .bucket(bucketName)
                    .prefix(prefix)
                    .recursive(true)
                    .build()
            );

            List<Part> parts = new ArrayList<>();
            for (Result<Item> result : results) {
                Item item = result.get();
                String objectName = item.objectName();

                if (objectName.endsWith("/")) {
                    continue;
                }

                monitor.info("Transferring object: " + objectName + " (size: " + item.size() + ")");
                parts.add(new MinioAssetPart(item, minioClient, bucketName));
            }

            if (parts.isEmpty()) {
                monitor.warning("No objects found in bucket '" + bucketName + "' with prefix '" + prefix + "'");
                return StreamResult.error("No objects found matching prefix: " + prefix);
            }

            monitor.info("Found " + parts.size() + " object(s) to transfer");
            return StreamResult.success(parts.stream());

        } catch (Exception e) {
            monitor.severe("Failed to list objects in MinIO bucket: " + bucketName, e);
            return StreamResult.error("Failed to list MinIO objects: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        monitor.info("Closing MinioAssetDataSource");
    }

    private record MinioAssetPart(Item item, MinioClient minioClient, String bucketName) implements Part {

        @Override
        public String name() {
            return item.objectName();
        }

        @Override
        public InputStream openStream() {
            try {
                return minioClient.getObject(GetObjectArgs.builder().bucket(bucketName).object(item.objectName()).build());
            } catch (Exception e) {
                throw new RuntimeException("Failed to open MinIO object: " + item.objectName(), e);
            }
        }
    }
}