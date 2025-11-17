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

import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSource;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSourceFactory;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Optional;

public class LocalFilesDataSourceFactory implements DataSourceFactory {

    private final Monitor monitor;

    public LocalFilesDataSourceFactory(Monitor monitor) {
        this.monitor = monitor;
    }

    @Override
    public String supportedType() {
        return "LocalFiles";
    }

    @Override
    public DataSource createSource(DataFlowStartMessage dataFlowStartMessage) {
        var sourceFolder = sourceFolder(dataFlowStartMessage).get();

        return new LocalFilesDataSource(sourceFolder, monitor);
    }
    
    @Override
    public @NotNull Result<Void> validateRequest(DataFlowStartMessage dataFlowStartMessage) {
        return sourceFolder(dataFlowStartMessage).map(it -> Result.success()).orElseGet(() -> Result.failure("sourceFolder is not found or it does not exist"));
    }
    
    private Optional<File> sourceFolder(DataFlowStartMessage request) {
        return Optional.of(request).map(DataFlowStartMessage::getSourceDataAddress).map(it -> it.getStringProperty("sourceFolder")).map(File::new).filter(File::exists);
    }
}
