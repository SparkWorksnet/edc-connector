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
import org.eclipse.edc.http.spi.EdcHttpClient;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;

import java.util.concurrent.Executors;

/**
 * Extension that registers a routing data sink.
 * Routes files to different destinations based on file type:
 * - JSON files: Piveau Hub Repo API
 * - CSV files: HTTP endpoint
 */
public class PiveauDataSinkExtension implements ServiceExtension {

    @Setting(value = "Piveau Hub Repo API URL for dataset registration")
    private static final String PIVEAU_API_URL = "edc.external.api.url";

    @Setting(value = "API key for Piveau Hub Repo")
    private static final String PIVEAU_API_KEY = "edc.external.api.key";

    @Override
    public String name() {
        return "Piveau Routing Data Sink";
    }

    @Inject
    private PipelineService pipelineService;

    @Inject
    private EdcHttpClient httpClient;

    @Override
    public void initialize(ServiceExtensionContext context) {
        var monitor = context.getMonitor();
        var executorService = Executors.newFixedThreadPool(10);

        var piveauApiUrl = context.getSetting(PIVEAU_API_URL, "http://localhost:8080/datasets");
        var piveauApiKey = context.getSetting(PIVEAU_API_KEY, "");

        // Register routing data sink factory
        pipelineService.registerFactory(new PiveauDataSinkFactory(monitor, httpClient, executorService));

        monitor.info("✓ Piveau Routing Data Sink registered");
        monitor.info("  Type: PiveauRouting");
        monitor.info("  JSON files → Piveau Hub Repo API: " + piveauApiUrl);
        monitor.info("  CSV files → HTTP endpoint (configured per transfer)");
    }
}
