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

import org.eclipse.edc.connector.dataplane.spi.pipeline.PipelineService;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;

/**
 * Extension that registers a MinIO-based streaming data source.
 * Polls a MinIO bucket for new objects.
 */
public class MinioFilesDataSourceExtension implements ServiceExtension {
    
    @Setting(value = "Piveau Hub Repo API URL for dataset registration")
    private static final String PIVEAU_API_URL = "edc.external.api.url";
    
    @Setting(value = "API key for Piveau Hub Repo")
    private static final String PIVEAU_API_KEY = "edc.external.api.key";
    
    @Override
    public String name() {
        return "Piveau MinIO Streaming Data Source";
    }
    
    @Inject
    private PipelineService pipelineService;
    
    @Override
    public void initialize(ServiceExtensionContext context) {
        var monitor = context.getMonitor();
        
        // Register MinIO-based data source factory
        pipelineService.registerFactory(new MinioFilesDataSourceFactory(monitor));
        
        monitor.info("✓ Piveau MinIO Streaming Data Source registered");
        monitor.info("  Type: MinioStreaming");
    }
}
