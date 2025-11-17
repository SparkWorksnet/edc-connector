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

package net.sparkworks.edc.extensions.source.local;

import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSource;
import org.eclipse.edc.connector.dataplane.spi.pipeline.StreamResult;
import org.eclipse.edc.spi.monitor.Monitor;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.util.stream.StreamSupport.stream;

/**
 * Data source that watches a local filesystem directory for new or modified files.
 * Monitors recursively, including all subdirectories.
 * Uses SHA-256 hash-based deduplication to prevent processing duplicate files.
 */
public class LocalFilesDataSource implements DataSource, Closeable {

    private final WatchService watchService;
    private final File sourceFolder;
    private final Set<String> sentFileHashes;
    private final Monitor monitor;
    private final Map<WatchKey, Path> watchKeyToPath;

    public LocalFilesDataSource(File sourceFolder, Monitor monitor) {
        this.monitor = monitor;
        this.watchKeyToPath = new HashMap<>();

        monitor.info("Creating LocalFilesDataSource: " + sourceFolder.getAbsolutePath());
        monitor.info("Monitoring recursively (including subdirectories)");
        monitor.info("Using SHA-256 hash-based deduplication");

        try {
            this.watchService = FileSystems.getDefault().newWatchService();
            this.sourceFolder = sourceFolder;
            this.sentFileHashes = ConcurrentHashMap.newKeySet();

            // Register the root directory and all subdirectories recursively
            registerDirectoryRecursively(Paths.get(this.sourceFolder.toURI()));

            monitor.info("Registered " + watchKeyToPath.size() + " directories for monitoring");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Recursively registers a directory and all its subdirectories with the WatchService
     */
    private void registerDirectoryRecursively(Path start) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                WatchKey key = dir.register(watchService, ENTRY_CREATE, ENTRY_MODIFY);
                watchKeyToPath.put(key, dir);
                monitor.debug("Registered directory: " + dir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                monitor.warning("Failed to visit: " + file + " - " + exc.getMessage());
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Registers a single directory (used for dynamically created subdirectories)
     */
    private void registerDirectory(Path dir) {
        try {
            WatchKey key = dir.register(watchService, ENTRY_CREATE, ENTRY_MODIFY);
            watchKeyToPath.put(key, dir);
            monitor.info("Registered new directory: " + dir);
        } catch (IOException e) {
            monitor.severe("Failed to register directory: " + dir, e);
        }
    }
    
    @Override
    public StreamResult<Stream<Part>> openPartStream() {
        Stream<Part> stream = openRecordsStream(watchService)
                .filter(Objects::nonNull)
                .flatMap(watchKey -> {
                    // Get the directory that triggered this event
                    Path dir = watchKeyToPath.get(watchKey);
                    if (dir == null) {
                        monitor.warning("WatchKey not found in map - this should not happen");
                        return Stream.empty();
                    }

                    return watchKey.pollEvents().stream()
                            .map(event -> {
                                // Resolve the full path by combining the watched directory with the event context
                                Path resolvedPath = dir.resolve((Path) event.context());
                                monitor.debug("File system event: " + event.kind().name() + " for " + resolvedPath);

                                // If this is a directory creation event, register the new directory
                                if (event.kind() == ENTRY_CREATE && Files.isDirectory(resolvedPath)) {
                                    monitor.info("New directory detected: " + resolvedPath);
                                    registerDirectory(resolvedPath);
                                }

                                return resolvedPath;
                            });
                })
                .filter(path -> {
                    // Skip directories - we only want to process files
                    if (Files.isDirectory(path)) {
                        return false;
                    }

                    // Compute hash of file contents
                    String fileHash = computeFileHash(path);

                    if (fileHash == null) {
                        // If hash computation failed, skip this file
                        monitor.warning("Skipping file due to hash computation failure: " + path);
                        return false;
                    }

                    // Check if we've already sent a file with this hash
                    if (sentFileHashes.contains(fileHash)) {
                        monitor.debug("Skipping duplicate file (hash: " + fileHash + "): " + path);
                        return false;
                    }

                    // Add hash to the set and allow the file through
                    sentFileHashes.add(fileHash);
                    monitor.info("Processing new file (hash: " + fileHash + "): " + path.getFileName());
                    return true;
                })
                .map(StreamingPart::new);

        return StreamResult.success(stream);
    }

    /**
     * Computes the SHA-256 hash of a file's contents.
     *
     * @param path the path to the file
     * @return the hexadecimal string representation of the hash, or null if an error occurs
     */
    private String computeFileHash(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] fileBytes = Files.readAllBytes(path);
            byte[] hashBytes = digest.digest(fileBytes);

            // Convert bytes to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException | IOException e) {
            monitor.severe("Error computing file hash for " + path + ": " + e.getMessage(), e);
            return null;
        }
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
