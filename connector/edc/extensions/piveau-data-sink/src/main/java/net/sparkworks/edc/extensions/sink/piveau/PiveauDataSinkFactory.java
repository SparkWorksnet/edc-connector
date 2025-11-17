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

import net.sparkworks.edc.extensions.sink.piveau.common.PiveauApiHandler;
import org.eclipse.edc.connector.dataplane.http.spi.HttpDataAddress;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSink;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSinkFactory;
import org.eclipse.edc.connector.dataplane.spi.pipeline.StreamResult;
import org.eclipse.edc.http.spi.EdcHttpClient;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Factory for creating PiveauRoutingDataSink instances.
 */
public class PiveauDataSinkFactory implements DataSinkFactory {
    
    private final Monitor monitor;
    private final EdcHttpClient httpClient;
    private final ExecutorService executorService;
    
    public PiveauDataSinkFactory(Monitor monitor, EdcHttpClient httpClient, ExecutorService executorService) {
        this.monitor = monitor;
        this.httpClient = httpClient;
        this.executorService = executorService;
    }
    
    @Override
    public String supportedType() {
        return "PiveauData";
    }
    
    @Override
    public @NotNull Result<Void> validateRequest(DataFlowStartMessage request) {
        return Result.success();
    }
    
    @Override
    public DataSink createSink(DataFlowStartMessage request) {
        monitor.info("Creating PiveauDataSink for request: " + request.getId());
        
        var destinationAddress = HttpDataAddress.Builder.newInstance().copyFrom(request.getDestinationDataAddress()).build();
        
        // Create and return the routing sink
        return new PiveauDataSink(httpClient, destinationAddress, monitor, executorService);
    }
}
