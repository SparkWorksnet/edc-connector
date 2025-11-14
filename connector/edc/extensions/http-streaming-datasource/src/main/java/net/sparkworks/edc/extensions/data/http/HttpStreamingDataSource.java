package net.sparkworks.edc.extensions.data.http;

import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSource;
import org.eclipse.edc.connector.dataplane.spi.pipeline.StreamResult;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.util.stream.StreamSupport.stream;

public class HttpStreamingDataSource implements DataSource, Closeable {
    
    private final WatchService watchService;
    private final File sourceFolder;
    
    public HttpStreamingDataSource(File sourceFolder) {
        System.out.println("Creating HttpStreamingDataSource: " + sourceFolder.getAbsolutePath());
        try {
            this.watchService = FileSystems.getDefault().newWatchService();
            this.sourceFolder = sourceFolder;
            Paths.get(this.sourceFolder.toURI()).register(watchService, ENTRY_CREATE, ENTRY_MODIFY);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    @Override
    public StreamResult<Stream<Part>> openPartStream() {
        Stream<Part> stream = openRecordsStream(watchService).filter(Objects::nonNull).flatMap(it -> it.pollEvents().stream()).map(event -> {
            var path = Paths.get(sourceFolder.toURI()).resolve(event.context().toString());
            System.out.println("event_NAME: " + event.kind().name());
            return new StreamingPart(path);
        });
        
        return StreamResult.success(stream);
    }
    
    @NotNull
    private Stream<WatchKey> openRecordsStream(WatchService watchService) {
        return stream(new WatchKeyAbstractSpliterator(watchService), false);
    }
    
    @Override
    public void close() throws IOException {
        watchService.close();
    }
    
    private record StreamingPart(Path path) implements Part {
        
        @Override
        public String name() {
            return path.toString();
        }
        
        @Override
        public InputStream openStream() {
            try {
                return new FileInputStream(path.toFile());
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }
    
    private static class WatchKeyAbstractSpliterator extends Spliterators.AbstractSpliterator<WatchKey> {
        
        private final WatchService watchService;
        
        WatchKeyAbstractSpliterator(WatchService watchService) {
            super(Long.MAX_VALUE, Spliterator.ORDERED);
            this.watchService = watchService;
        }
        
        @Override
        public boolean tryAdvance(Consumer<? super WatchKey> action) {
            var poll = watchService.poll();
            while (poll == null) {
                try {
                    poll = watchService.poll(100, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            action.accept(poll);
            poll.reset();
            return true;
        }
    }
}
