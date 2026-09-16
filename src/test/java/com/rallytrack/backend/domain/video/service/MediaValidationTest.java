package com.rallytrack.backend.domain.video.service;

import com.rallytrack.backend.global.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockMultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

class MediaValidationTest {
    private final MediaValidationService media = new MediaValidationService("ffprobe");
    static byte[] fixture(String ext) throws IOException {
        try (var input = MediaValidationTest.class.getResourceAsStream("/media/sample." + ext)) { return input.readAllBytes(); }
    }
    static byte[] png(int w, int h) throws IOException {
        var out = new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), "png", out); return out.toByteArray();
    }
    @ParameterizedTest @CsvSource({"mp4,video/mp4", "mov,video/quicktime", "webm,video/webm"})
    void validVideoIsProbedAndTemporaryFileRemoved(String ext, String mime) throws Exception {
        Path path;
        try (var result = media.video(new MockMultipartFile("video", "sample."+ext, mime, fixture(ext)))) {
            assertThat(result.contentType()).isEqualTo(mime); assertThat(result.extension()).isEqualTo(ext);
            path = result.path(); assertThat(path).exists();
        }
        assertThat(path).doesNotExist();
    }
    @Test void acceptsActualVideoWithMissingMimeAndUntrustedNameNeverBecomesObjectKey() throws Exception {
        try (var result = media.video(new MockMultipartFile("video", "../../payload.html.mp4", null, fixture("mp4")))) {
            assertThat(result.path().getFileName().toString()).startsWith("rally-video-");
        }
    }
    @Test void rejectsHtmlSvgEmptyAndTruncatedFiles() throws Exception {
        for (byte[] bytes : new byte[][] {new byte[0], "<html>test</html>".getBytes(), "<svg/>".getBytes(), java.util.Arrays.copyOf(fixture("mp4"), 20)}) {
            assertThatThrownBy(() -> media.video(new MockMultipartFile("video", "test.mp4", "video/mp4", bytes))).isInstanceOf(ApiException.class);
        }
        assertThatThrownBy(() -> media.thumbnail(new MockMultipartFile("thumb", "test.png", "image/png", "<svg/>".getBytes())))
                .isInstanceOf(ApiException.class);
    }
    @Test void rejectsMimeOrExtensionMismatchAndOversize() throws Exception {
        assertThatThrownBy(() -> media.video(new MockMultipartFile("video", "test.mp4", "text/html", fixture("mp4")))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> media.video(new MockMultipartFile("video", "test.html", "video/mp4", fixture("mp4")))).isInstanceOf(ApiException.class);
        var oversized = new MockMultipartFile("video", "test.mp4", "video/mp4", new byte[]{1}) {
            @Override public long getSize() { return MediaValidationService.MAX_VIDEO_BYTES + 1; }
        };
        assertThatThrownBy(() -> media.video(oversized)).isInstanceOf(ApiException.class).extracting("status").isEqualTo(413);
    }
    @Test void pngAndJpegAreReencodedWithoutAppendedHtml() throws Exception {
        byte[] png = png(32, 24);
        var bytes = new ByteArrayOutputStream(); bytes.write(png); bytes.write("<script>marker</script>".getBytes());
        try (var first = media.thumbnail(new MockMultipartFile("thumb", "image.png", "image/png", bytes.toByteArray()))) {
            assertThat(first.contentType()).isEqualTo("image/jpeg");
            byte[] jpeg = Files.readAllBytes(first.path());
            assertThat(new String(jpeg, java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("marker");
            try (var second = media.thumbnail(new MockMultipartFile("thumb", "image.jpg", "image/jpeg", jpeg))) {
                assertThat(ImageIO.read(second.path().toFile()).getWidth()).isEqualTo(32);
            }
        }
    }
    @Test void excessiveDimensionsAndWrongImageMimeAreRejected() throws Exception {
        assertThatThrownBy(() -> media.thumbnail(new MockMultipartFile("thumb", "image.png", "image/png", png(4097, 1)))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> media.thumbnail(new MockMultipartFile("thumb", "image.png", "text/html", png(1, 1)))).isInstanceOf(ApiException.class);
    }
    @Test void missingValidatorFailsClosed() throws Exception {
        var unavailable = new MediaValidationService("/no/such/ffprobe");
        assertThatThrownBy(() -> unavailable.video(new MockMultipartFile("video", "test.mp4", "video/mp4", fixture("mp4"))))
                .isInstanceOf(ApiException.class).extracting("status").isEqualTo(503);
    }
    @Test void playlistsCannotMakeTheValidatorFetchExternalResources() {
        assertThatThrownBy(() -> media.video(new MockMultipartFile("video", "list.mp4", "video/mp4",
                "#EXTM3U\nhttp://127.0.0.1:1/never-fetch".getBytes()))).isInstanceOf(ApiException.class);
    }

    @Test void probeOutputLimitAndTimeoutFailClosed(@org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        Path huge = directory.resolve("huge-probe");
        Files.writeString(huge, "#!/bin/sh\nexec head -c 70000 /dev/zero\n");
        assertThat(huge.toFile().setExecutable(true)).isTrue();
        assertThatThrownBy(() -> new MediaValidationService(huge.toString()).video(
                new MockMultipartFile("video", "test.mp4", "video/mp4", fixture("mp4")))).isInstanceOf(ApiException.class);
        Path slow = directory.resolve("slow-probe");
        Files.writeString(slow, "#!/bin/sh\nexec sleep 30\n");
        assertThat(slow.toFile().setExecutable(true)).isTrue();
        long start = System.nanoTime();
        assertThatThrownBy(() -> new MediaValidationService(slow.toString()).video(
                new MockMultipartFile("video", "test.mp4", "video/mp4", fixture("mp4"))))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo("MEDIA_VALIDATION_TIMEOUT");
        assertThat(java.time.Duration.ofNanos(System.nanoTime()-start).toSeconds()).isLessThan(20);
    }
}
