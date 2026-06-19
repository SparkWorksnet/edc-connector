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

package net.sparkworks.edc.extensions.transfer.minio;

import org.eclipse.edc.connector.dataplane.spi.pipeline.PipelineService;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

public class MinioAssetTransferExtension implements ServiceExtension {

    @Inject
    private PipelineService pipelineService;

    @Override
    public String name() {
        return "MinIO Asset Transfer";
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var monitor = context.getMonitor();

        pipelineService.registerFactory(new MinioAssetDataSourceFactory(monitor));

        monitor.info("MinIO Asset Transfer extension registered (source type: MinioAsset)");
    }

}