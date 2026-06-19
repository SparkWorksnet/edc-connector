package net.sparkworks.edc.extensions.data.http;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.eclipse.edc.connector.dataplane.http.spi.HttpDataAddress;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSink;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSource;
import org.eclipse.edc.connector.dataplane.spi.pipeline.StreamResult;
import org.eclipse.edc.spi.monitor.Monitor;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * HTTP Data Sink for presigned URL transfers. Uses a raw OkHttpClient to ensure
 * Content-Length is sent (required by S3/MinIO presigned PUT URLs).
 */
public class CustomPresignedHttpDataSinkWithPartName implements DataSink {

    private static final OkHttpClient OK_HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(300, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    private final HttpDataAddress destinationAddress;
    private final Monitor monitor;
    private final ExecutorService executorService;
    private final String authKey;

    public CustomPresignedHttpDataSinkWithPartName(HttpDataAddress destinationAddress, Monitor monitor, ExecutorService executorService) {
        this.destinationAddress = destinationAddress;
        this.monitor = monitor;
        this.executorService = executorService;

        this.authKey = destinationAddress.getAuthKey();
        if (authKey != null && !authKey.isEmpty()) {
            monitor.info("Auth token configured for PresignedHttpData sink");
        }
    }

    @Override
    public CompletableFuture<StreamResult<Object>> transfer(DataSource source) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var streamResult = source.openPartStream();

                if (streamResult.failed()) {
                    monitor.severe("Failed to open part stream: " + streamResult.getFailureDetail());
                    return StreamResult.<Object>error(streamResult.getFailureDetail());
                }

                var partStream = streamResult.getContent();

                partStream.forEach(part -> {
                    String filePath = part.name();

                    try (var inputStream = part.openStream()) {
                        byte[] fileContent = inputStream.readAllBytes();

                        var requestBody = RequestBody.create(fileContent, MediaType.parse("application/octet-stream"));

                        String method = destinationAddress.getStringProperty("method", "PUT");
                        var requestBuilder = new Request.Builder()
                                .url(destinationAddress.getBaseUrl())
                                .method(method, requestBody)
                                .header("Content-Length", String.valueOf(fileContent.length))
                                .header("X-File-Path", filePath)
                                .header("X-File-Name", extractFileName(filePath))
                                .header("Content-Type", "application/octet-stream");

                        if (authKey != null && !authKey.isEmpty()) {
                            requestBuilder.header("Authorization", "Bearer " + authKey);
                        }

                        var httpRequest = requestBuilder.build();

                        monitor.info("Sending HTTP " + method + " to: " + destinationAddress.getBaseUrl());

                        try (var response = OK_HTTP_CLIENT.newCall(httpRequest).execute()) {
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

                partStream.close();
                return StreamResult.success();

            } catch (Exception e) {
                monitor.severe("Transfer operation failed", e);
                return StreamResult.<Object>error("Transfer failed: " + e.getMessage());
            }
        }, executorService);
    }

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
