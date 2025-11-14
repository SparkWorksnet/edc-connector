package net.sparkworks.edc.extensions.data.http;

import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSource;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSourceFactory;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Optional;

public class HttpStreamingDataSourceFactory implements DataSourceFactory {
    
    @Override
    public String supportedType() {
        return "HttpStreaming";
    }
    
    @Override
    public DataSource createSource(DataFlowStartMessage dataFlowStartMessage) {
        return new HttpStreamingDataSource(sourceFolder(dataFlowStartMessage).get());
    }
    
    @Override
    public @NotNull Result<Void> validateRequest(DataFlowStartMessage dataFlowStartMessage) {
        return sourceFolder(dataFlowStartMessage).map(it -> Result.success()).orElseGet(() -> Result.failure("sourceFolder is not found or it does not exist"));
    }
    
    private Optional<File> sourceFolder(DataFlowStartMessage request) {
        return Optional.of(request).map(DataFlowStartMessage::getSourceDataAddress).map(it -> it.getStringProperty("sourceFolder")).map(File::new).filter(File::exists);
    }
}
