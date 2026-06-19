package net.sparkworks.edc.extensions.data.http;

import org.eclipse.edc.connector.dataplane.http.spi.HttpDataAddress;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSink;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSinkFactory;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage;

import java.util.concurrent.ExecutorService;

/**
 * Factory that creates CustomPresignedHttpDataSinkWithPartName instances
 * for PresignedHttpData destination type.
 */
public class CustomPresignedHttpDataSinkWithPartNameFactory implements DataSinkFactory {

    private final Monitor monitor;
    private final ExecutorService executorService;

    public CustomPresignedHttpDataSinkWithPartNameFactory(Monitor monitor, ExecutorService executorService) {
        this.monitor = monitor;
        this.executorService = executorService;
    }

    @Override
    public Result<Void> validateRequest(DataFlowStartMessage request) {
        try {
            HttpDataAddress.Builder.newInstance().copyFrom(request.getDestinationDataAddress()).build();
            return Result.success();
        } catch (Exception e) {
            monitor.warning("Request validation failed: " + e.getMessage());
            return Result.failure("Invalid PresignedHttpData address: " + e.getMessage());
        }
    }

    @Override
    public String supportedType() {
        return "PresignedHttpData";
    }

    @Override
    public DataSink createSink(DataFlowStartMessage request) {
        monitor.info("Creating PresignedHttpData sink for request: " + request.getId());

        var destinationAddress = HttpDataAddress.Builder.newInstance().copyFrom(request.getDestinationDataAddress()).build();

        monitor.info("Destination URL: " + destinationAddress.getBaseUrl());

        return new CustomPresignedHttpDataSinkWithPartName(destinationAddress, monitor, executorService);
    }
}
