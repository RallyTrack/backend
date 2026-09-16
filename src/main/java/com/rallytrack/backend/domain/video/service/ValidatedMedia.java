package com.rallytrack.backend.domain.video.service;

import java.io.IOException;
import java.nio.file.*;

/** Only MediaValidationService can construct uploadable media. */
public final class ValidatedMedia implements AutoCloseable {
    private final Path path;
    private final String extension;
    private final String contentType;
    ValidatedMedia(Path path, String extension, String contentType) {
        this.path = path; this.extension = extension; this.contentType = contentType;
    }
    public Path path() { return path; }
    public String extension() { return extension; }
    public String contentType() { return contentType; }
    public void close() throws IOException { Files.deleteIfExists(path); }
}
