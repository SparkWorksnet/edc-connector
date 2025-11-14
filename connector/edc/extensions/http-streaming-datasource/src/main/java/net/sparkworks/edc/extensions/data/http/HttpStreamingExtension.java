package net.sparkworks.edc.extensions.data.http;

import org.eclipse.edc.connector.dataplane.spi.pipeline.PipelineService;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

public class HttpStreamingExtension implements ServiceExtension {
    
    @Override
    public String name() {
        return "Http Streaming";
    }
    
    @Inject
    private PipelineService pipelineService;
    
    @Override
    public void initialize(ServiceExtensionContext context) {
        pipelineService.registerFactory(new HttpStreamingDataSourceFactory());
    }
}
