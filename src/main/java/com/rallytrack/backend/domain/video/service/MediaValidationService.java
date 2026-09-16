package com.rallytrack.backend.domain.video.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rallytrack.backend.global.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

@Service
public class MediaValidationService {
    public static final long MAX_VIDEO_BYTES = 500L * 1024 * 1024;
    public static final long MAX_THUMBNAIL_BYTES = 5L * 1024 * 1024;
    private final Semaphore probes = new Semaphore(2);
    private final String ffprobe;
    private final ObjectMapper mapper = new ObjectMapper();
    public MediaValidationService(@Value("${MEDIA_FFPROBE:ffprobe}") String ffprobe) { this.ffprobe = ffprobe; }

    public ValidatedMedia video(MultipartFile file) throws IOException {
        checkSize(file, MAX_VIDEO_BYTES);
        if (!probes.tryAcquire()) throw new ApiException(429, "MEDIA_BUSY", "영상 검사 중입니다. 잠시 후 다시 시도해주세요.");
        Path input = null;
        boolean keep = false;
        try {
            input = copyBounded(file, MAX_VIDEO_BYTES);
            byte[] header;
            try (InputStream in = Files.newInputStream(input)) { header = in.readNBytes(4096); }
            String ext;
            String mime;
            if (header.length >= 12 && new String(header, 4, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("ftyp")) {
                boolean mov = new String(header, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("qt  ");
                ext = mov ? "mov" : "mp4"; mime = mov ? "video/quicktime" : "video/mp4";
            } else if (header.length > 8 && header[0] == 0x1a && (header[1] & 255) == 0x45
                    && (header[2] & 255) == 0xdf && (header[3] & 255) == 0xa3
                    && contains(header, new byte[]{0x42, (byte)0x82, (byte)0x84, 'w', 'e', 'b', 'm'})) {
                ext = "webm"; mime = "video/webm";
            } else { throw invalid(); }
            String name = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
            if (!name.endsWith("." + ext)) throw invalid();
            checkMime(file, mime);
            probe(input, ext);
            keep = true;
            return new ValidatedMedia(input, ext, mime);
        } finally {
            if (!keep && input != null) Files.deleteIfExists(input);
            probes.release();
        }
    }

    private void probe(Path input, String ext) throws IOException {
        Process process;
        try {
            process = new ProcessBuilder(ffprobe, "-v", "error", "-max_alloc", "33554432",
                    "-protocol_whitelist", "file", "-format_whitelist", "mov,matroska,webm",
                    "-enable_drefs", "0", "-use_absolute_path", "0", "-max_streams", "32",
                    "-probesize", "5242880", "-analyzeduration", "5000000",
                    "-show_entries", "format=format_name:stream=codec_type,width,height", "-of", "json",
                    "-i", input.toString()).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        } catch (IOException e) {
            throw new ApiException(503, "MEDIA_VALIDATOR_UNAVAILABLE", "영상 검사기를 사용할 수 없습니다.");
        }
        CompletableFuture<byte[]> output = new CompletableFuture<>();
        Thread reader = new Thread(() -> {
            try (InputStream stream = process.getInputStream()) {
                byte[] bytes = stream.readNBytes(65537);
                if (bytes.length > 65536) { process.destroyForcibly(); output.completeExceptionally(invalid()); }
                else output.complete(bytes);
            } catch (Exception e) { output.completeExceptionally(e); }
        }, "media-probe-output");
        reader.setDaemon(true); reader.start();
        try {
            if (!process.waitFor(15, TimeUnit.SECONDS)) throw new ApiException(422, "MEDIA_VALIDATION_TIMEOUT", "영상 검사 시간이 초과되었습니다.");
            if (process.exitValue() != 0) throw invalid();
            var json = mapper.readTree(output.get(1, TimeUnit.SECONDS));
            String format = json.path("format").path("format_name").asText();
            if ("webm".equals(ext) ? !format.contains("webm") : !format.contains("mov")) throw invalid();
            boolean video = false;
            for (var stream : json.path("streams")) {
                if ("video".equals(stream.path("codec_type").asText())) {
                    int width = stream.path("width").asInt(); int height = stream.path("height").asInt();
                    if (width <= 0 || height <= 0 || width > 7680 || height > 4320 || (long)width * height > 33177600) throw invalid();
                    video = true;
                }
            }
            if (!video) throw invalid();
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw invalid();
        } catch (ExecutionException | TimeoutException e) { throw invalid();
        } finally {
            process.destroyForcibly();
            try { process.getInputStream().close(); } catch (IOException ignored) {}
        }
    }

    public ValidatedMedia thumbnail(MultipartFile file) throws IOException {
        if (!probes.tryAcquire()) throw new ApiException(429, "MEDIA_BUSY", "파일 검사 중입니다. 잠시 후 다시 시도해주세요.");
        try { return normalizeThumbnail(file); } finally { probes.release(); }
    }

    private ValidatedMedia normalizeThumbnail(MultipartFile file) throws IOException {
        checkSize(file, MAX_THUMBNAIL_BYTES);
        // Small, bounded encoded image; inspect dimensions before allocating pixels.
        byte[] bytes;
        try (InputStream stream = file.getInputStream()) { bytes = stream.readNBytes((int)MAX_THUMBNAIL_BYTES + 1); }
        if (bytes.length == 0 || bytes.length > MAX_THUMBNAIL_BYTES) throw invalid();
        try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw invalid();
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!Set.of("jpeg", "png").contains(format)) throw invalid();
                checkMime(file, format.equals("png") ? "image/png" : "image/jpeg");
                reader.setInput(stream, true, true);
                int w = reader.getWidth(0), h = reader.getHeight(0);
                if (w <= 0 || h <= 0 || w > 4096 || h > 4096 || (long)w * h > 8388608) throw invalid();
                BufferedImage decoded = reader.read(0);
                BufferedImage rgb = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                var graphics = rgb.createGraphics();
                try { graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, w, h); graphics.drawImage(decoded, 0, 0, null); }
                finally { graphics.dispose(); decoded.flush(); }
                Path result = Files.createTempFile("rally-thumb-", ".jpg");
                try {
                    if (!ImageIO.write(rgb, "jpeg", result.toFile())) throw invalid();
                    return new ValidatedMedia(result, "jpg", "image/jpeg");
                } catch (Exception e) { Files.deleteIfExists(result); throw e;
                } finally { rgb.flush(); }
            } finally { reader.dispose(); }
        } catch (javax.imageio.IIOException e) { throw invalid(); }
    }

    private Path copyBounded(MultipartFile file, long max) throws IOException {
        Path path = Files.createTempFile("rally-video-", ".media");
        try (InputStream in = file.getInputStream(); OutputStream out = Files.newOutputStream(path)) {
            byte[] buffer = new byte[65536]; long total = 0; int n;
            while ((n = in.read(buffer)) != -1) {
                total += n; if (total > max) throw tooLarge(); out.write(buffer, 0, n);
            }
            if (total == 0) throw invalid();
            return path;
        } catch (Exception e) { Files.deleteIfExists(path); throw e; }
    }
    private void checkSize(MultipartFile file, long max) {
        if (file == null || file.isEmpty()) throw invalid();
        if (file.getSize() > max) throw tooLarge();
    }
    private void checkMime(MultipartFile file, String actual) {
        String mime = file.getContentType();
        if (mime != null && !mime.isBlank() && !mime.equalsIgnoreCase("application/octet-stream") && !mime.equalsIgnoreCase(actual)) throw invalid();
    }
    private boolean contains(byte[] bytes, byte[] needle) {
        outer: for (int i = 0; i <= bytes.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) if (bytes[i+j] != needle[j]) continue outer;
            return true;
        }
        return false;
    }
    private ApiException invalid() { return new ApiException(415, "INVALID_MEDIA", "MP4·MOV·WebM 영상과 JPEG·PNG 썸네일만 업로드할 수 있습니다."); }
    private ApiException tooLarge() { return new ApiException(413, "UPLOAD_TOO_LARGE", "영상은 500MB, 썸네일은 5MB까지 업로드할 수 있습니다."); }
}
