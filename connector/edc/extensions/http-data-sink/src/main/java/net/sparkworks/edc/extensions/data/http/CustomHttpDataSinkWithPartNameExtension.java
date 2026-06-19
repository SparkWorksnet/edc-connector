package net.sparkworks.edc.extensions.data.http;


import org.eclipse.edc.connector.dataplane.spi.pipeline.PipelineService;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

import java.util.concurrent.Executors;

@Extension(value = "Custom HTTP Data Sink with Part Name Extension")
public class CustomHttpDataSinkWithPartNameExtension implements ServiceExtension {

    @Inject
    private PipelineService pipelineService;

    @Override
    public String name() {
        return "Custom HTTP Data Sink with Part Name";
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var monitor = context.getMonitor();

        var executorService = Executors.newFixedThreadPool(10);
        var factory = new CustomHttpDataSinkWithPartNameFactory(monitor, executorService);

        pipelineService.registerFactory(factory);

        monitor.info("Custom HTTP Data Sink registered (type: HttpData, using raw OkHttpClient)");
    }
}
