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

package net.sparkworks.edc.extensions.source.local;

import org.eclipse.edc.connector.dataplane.spi.pipeline.PipelineService;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

public class LocalFilesDataSourceExtension implements ServiceExtension {

    @Override
    public String name() {
        return "Local Files Data Source";
    }

    @Inject
    private PipelineService pipelineService;

    @Override
    public void initialize(ServiceExtensionContext context) {
        var monitor = context.getMonitor();

        pipelineService.registerFactory(new LocalFilesDataSourceFactory(monitor));

        monitor.info("✓ Local Files Data Source registered");
        monitor.info("  Type: LocalFiles");
        monitor.info("  Deduplication: SHA-256 hash-based");
    }
}
