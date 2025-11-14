package net.sparkworks.edc.extensions.data.http;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.eclipse.edc.connector.dataplane.http.spi.HttpDataAddress;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSink;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSource;
import org.eclipse.edc.connector.dataplane.spi.pipeline.StreamResult;
import org.eclipse.edc.http.spi.EdcHttpClient;
import org.eclipse.edc.spi.monitor.Monitor;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Custom HTTP Data Sink that adds the file path as a custom header
 */
public class CustomHttpDataSinkWithPartName implements DataSink {
    
    private final EdcHttpClient httpClient;
    private final HttpDataAddress destinationAddress;
    private final Monitor monitor;
    private final ExecutorService executorService;
    
    public CustomHttpDataSinkWithPartName(EdcHttpClient httpClient, HttpDataAddress destinationAddress, Monitor monitor, ExecutorService executorService) {
        this.httpClient = httpClient;
        this.destinationAddress = destinationAddress;
        this.monitor = monitor;
        this.executorService = executorService;
    }
    
    @Override
    public CompletableFuture<StreamResult<Object>> transfer(DataSource source) {
        return CompletableFuture.supplyAsync(() -> {
            //monitor.info("Starting custom HTTP transfer with file path headers");
            
            try {
                // Open the stream of parts from the data source
                var streamResult = source.openPartStream();
                
                if (streamResult.failed()) {
                    monitor.severe("Failed to open part stream: " + streamResult.getFailureDetail());
                    return StreamResult.<Object>error(streamResult.getFailureDetail());
                }
                
                var partStream = streamResult.getContent();
                
                // Process each part (file) in the stream
                partStream.forEach(part -> {
                    // Get the file path from part name
                    String filePath = part.name();
                    
                    //monitor.info("Processing file: " + filePath);
                    
                    try (var inputStream = part.openStream()) {
                        // Read the file content
                        byte[] fileContent = inputStream.readAllBytes();
                        
                        //monitor.info("Read " + fileContent.length + " bytes from file: " + filePath);
                        
                        // Create request body with file content
                        var requestBody = RequestBody.create(fileContent, MediaType.parse("application/octet-stream"));
                        
                        // Build HTTP request with custom headers
                        var httpRequest = new Request.Builder().url(destinationAddress.getBaseUrl()).post(requestBody).header("X-File-Path", filePath)                    // ← YOUR CUSTOM HEADER
                                .header("X-File-Name", extractFileName(filePath))   // ← OPTIONAL: just filename
                                .header("Content-Type", "application/octet-stream").build();
                        
                        monitor.info("Sending HTTP POST to: " + destinationAddress.getBaseUrl());
                        
                        // Execute the HTTP request
                        try (var response = httpClient.execute(httpRequest)) {
                            if (response.isSuccessful()) {
                                monitor.info("Successfully transferred file: " + filePath + " (status: " + response.code() + ")");
                            } else {
                                monitor.warning("Failed to transfer file: " + filePath + " (status: " + response.code() + ")");
                            }
                        }
                        
                    } catch (IOException e) {
                        monitor.severe("Error processing file: " + filePath, e);
                        throw new RuntimeException("Failed to transfer file: " + filePath, e);
                    }
                });
                
                // Close the stream
                partStream.close();
                
                //monitor.info("Custom HTTP transfer completed successfully");
                return StreamResult.success();
                
            } catch (Exception e) {
                monitor.severe("Transfer operation failed", e);
                return StreamResult.<Object>error("Transfer failed: " + e.getMessage());
            }
        }, executorService);
    }
    
    /**
     * Extract just the filename from the full path
     */
    private String extractFileName(String fullPath) {
        if (fullPath == null || fullPath.isEmpty()) {
            return "unknown";
        }
        
        int lastSlash = Math.max(fullPath.lastIndexOf('/'), fullPath.lastIndexOf('\\'));
        if (lastSlash >= 0 && lastSlash < fullPath.length() - 1) {
            return fullPath.substring(lastSlash + 1);
        }
        
        return fullPath;
    }
}
