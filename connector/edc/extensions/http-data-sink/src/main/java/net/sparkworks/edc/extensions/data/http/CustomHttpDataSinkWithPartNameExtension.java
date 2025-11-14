package net.sparkworks.edc.extensions.data.http;


import org.eclipse.edc.connector.dataplane.spi.pipeline.PipelineService;
import org.eclipse.edc.http.spi.EdcHttpClient;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

import java.util.concurrent.Executors;

@Extension(value = "Custom HTTP Data Sink with Part Name Extension")
public class CustomHttpDataSinkWithPartNameExtension implements ServiceExtension {
    
    // Set a high loading priority to ensure this loads AFTER the default HTTP extension
    private static final int PRIORITY = 100; // Higher than default (which is usually 0)
    
    @Inject
    private EdcHttpClient httpClient;
    
    @Inject
    private PipelineService pipelineService;
    
    @Override
    public String name() {
        return "Custom HTTP Data Sink with Part Name";
    }
    
    @Override
    public void initialize(ServiceExtensionContext context) {
        var monitor = context.getMonitor();
        
        monitor.info("=====================================================");
        monitor.info("Initializing Custom HTTP Data Sink Extension");
        monitor.info("Priority: " + PRIORITY);
        monitor.info("=====================================================");
        
        var executorService = Executors.newFixedThreadPool(10);
        
        var factory = new CustomHttpDataSinkWithPartNameFactory(httpClient, monitor, executorService);
        
        // Register with HIGH priority
        pipelineService.registerFactory(factory);
        
        monitor.info("Custom HTTP Data Sink Factory registered with HIGH priority");
        monitor.info("This will override the default HttpDataSink");
        monitor.info("=====================================================");
    }
}
