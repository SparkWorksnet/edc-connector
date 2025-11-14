package net.sparkworks.edc.extensions.data.http;

import org.eclipse.edc.connector.dataplane.http.spi.HttpDataAddress;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSink;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSinkFactory;
import org.eclipse.edc.http.spi.EdcHttpClient;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage;

import java.util.concurrent.ExecutorService;

/**
 * Factory that creates CustomHttpDataSinkWithPartName instances
 */
public class CustomHttpDataSinkWithPartNameFactory implements DataSinkFactory {
    
    private final EdcHttpClient httpClient;
    private final Monitor monitor;
    private final ExecutorService executorService;
    
    public CustomHttpDataSinkWithPartNameFactory(EdcHttpClient httpClient, Monitor monitor, ExecutorService executorService) {
        this.httpClient = httpClient;
        this.monitor = monitor;
        this.executorService = executorService;
    }
    
    //    @Override
    //    public boolean canHandle(DataFlowStartMessage request) {
    //        // This factory handles HttpData destination types
    //        var destinationType = request.getDestinationDataAddress().getType();
    //        boolean canHandle = "HttpData".equals(destinationType);
    //
    //        if (canHandle) {
    //            monitor.info("CustomHttpDataSinkFactory can handle request with destination type: " + destinationType);
    //        }
    //
    //        return canHandle;
    //    }
    
    @Override
    public Result<Void> validateRequest(DataFlowStartMessage request) {
        try {
            // Validate that we can build an HttpDataAddress from the destination
            HttpDataAddress.Builder.newInstance().copyFrom(request.getDestinationDataAddress()).build();
            
            monitor.debug("Request validation successful");
            return Result.success();
            
        } catch (Exception e) {
            monitor.warning("Request validation failed: " + e.getMessage());
            return Result.failure("Invalid HttpData address: " + e.getMessage());
        }
    }
    
    @Override
    public String supportedType() {
        return "HttpData";
    }
    
    @Override
    public DataSink createSink(DataFlowStartMessage request) {
        monitor.info("Creating custom HTTP data sink for request: " + request.getId());
        
        // Build the destination address from the request
        var destinationAddress = HttpDataAddress.Builder.newInstance().copyFrom(request.getDestinationDataAddress()).build();
        
        monitor.info("Destination URL: " + destinationAddress.getBaseUrl());
        
        // Create and return the custom sink
        return new CustomHttpDataSinkWithPartName(httpClient, destinationAddress, monitor, executorService);
    }
}
