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

import org.eclipse.edc.connector.dataplane.spi.pipeline.PipelineService;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

import java.util.concurrent.Executors;

/**
 * Extension that registers the Piveau routing data sink.
 * Routes files to different destinations based on file type:
 * - JSON files: Piveau Hub Repo API
 * - CSV files: MinIO / S3-compatible bucket (configured per transfer via destination DataAddress)
 */
public class PiveauDataSinkExtension implements ServiceExtension {

    @Override
    public String name() {
        return "Piveau Routing Data Sink";
    }

    @Inject
    private PipelineService pipelineService;

    @Override
    public void initialize(ServiceExtensionContext context) {
        var monitor = context.getMonitor();
        var executorService = Executors.newFixedThreadPool(10);

        pipelineService.registerFactory(new PiveauDataSinkFactory(monitor, executorService));

        monitor.info("✓ Piveau Routing Data Sink registered");
        monitor.info("  Type: PiveauData");
        monitor.info("  JSON files → Piveau Hub Repo API (configured per transfer)");
        monitor.info("  CSV files  → MinIO bucket (configured per transfer)");
    }
}
