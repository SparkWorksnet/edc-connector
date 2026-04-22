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

import org.eclipse.edc.connector.dataplane.spi.pipeline.PipelineService;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

import java.util.concurrent.Executors;

/**
 * EDC extension that registers a MinIO-backed data sink.
 * Handles destination data addresses of type {@code MinioData}.
 */
@Extension(value = "MinIO Data Sink Extension")
public class MinioDataSinkExtension implements ServiceExtension {

    @Inject
    private PipelineService pipelineService;

    @Override
    public String name() {
        return "MinIO Data Sink";
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var monitor = context.getMonitor();
        var executorService = Executors.newFixedThreadPool(10);

        pipelineService.registerFactory(new MinioDataSinkFactory(monitor, executorService));

        monitor.info("MinIO Data Sink registered (supported type: MinioData)");
    }
}