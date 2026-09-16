package com.rallytrack.backend.domain.briefing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rallytrack.backend.global.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

@Component
public class GeminiClient {
    private final ObjectMapper mapper;
    private final String apiKey;
    private final String model;
    private final boolean enabled;
    private final HttpClient http;
    private final Duration deadline;

    @org.springframework.beans.factory.annotation.Autowired
    public GeminiClient(ObjectMapper mapper, @Value("${GEMINI_API_KEY:}") String apiKey,
            @Value("${GEMINI_MODEL:gemini-3.6-flash}") String model,
            @Value("${GEMINI_ENABLED:false}") boolean enabled) {
        this(mapper, apiKey, model, enabled, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build(), Duration.ofSeconds(30));
    }
    GeminiClient(ObjectMapper mapper, String apiKey, String model, boolean enabled, HttpClient http, Duration deadline) {
        this.mapper = mapper; this.apiKey = apiKey; this.model = model; this.enabled = enabled;
        this.http = http; this.deadline = deadline;
        if (!model.matches("[a-zA-Z0-9.-]{1,80}")) throw new IllegalArgumentException("Invalid Gemini model setting");
    }
    public String model() { return model; }
    public void requireEnabled() {
        if (!enabled || apiKey.isBlank()) throw new ApiException(503, "BRIEFING_UNAVAILABLE", "브리핑 기능을 사용할 수 없습니다.");
    }
    public String generate(String prompt) {
        requireEnabled();
        if (prompt.length() > 12000) throw new ApiException(400, "BRIEFING_INPUT_TOO_LARGE", "분석 데이터가 너무 큽니다.");
        CompletableFuture<HttpResponse<byte[]>> pending = null;
        try {
            byte[] body = mapper.writeValueAsBytes(Map.of(
                    "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                    "generationConfig", Map.of("maxOutputTokens", 1500, "temperature", 0.4)));
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                    "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent"))
                    .header("x-goog-api-key", apiKey).header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(25)).POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            pending = http.sendAsync(request, info -> new LimitedBodySubscriber(65536));
            HttpResponse<byte[]> response = pending.get(deadline.toMillis(), TimeUnit.MILLISECONDS);
            if (response.statusCode() != 200) throw providerError();
            var root = mapper.readTree(response.body());
            var candidate = root.path("candidates").path(0);
            if (!"STOP".equals(candidate.path("finishReason").asText())) throw providerError();
            StringBuilder text = new StringBuilder();
            for (var part : candidate.path("content").path("parts")) {
                if (!part.path("thought").asBoolean(false)) text.append(part.path("text").asText(""));
            }
            if (text.isEmpty() || text.length() > 8000) throw providerError();
            return text.toString();
        } catch (TimeoutException e) {
            if (pending != null) pending.cancel(true);
            throw new ApiException(504, "BRIEFING_TIMEOUT", "브리핑 응답이 지연되고 있습니다. 잠시 후 다시 시도해주세요.");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof HttpTimeoutException)
                throw new ApiException(504, "BRIEFING_TIMEOUT", "브리핑 응답이 지연되고 있습니다. 잠시 후 다시 시도해주세요.");
            throw providerError();
        } catch (InterruptedException e) {
            if (pending != null) pending.cancel(true);
            Thread.currentThread().interrupt(); throw providerError();
        } catch (ApiException e) { throw e;
        } catch (Exception e) { throw providerError(); }
    }
    private ApiException providerError() {
        // Never propagate upstream bodies, URLs, headers or exceptions containing credentials.
        return new ApiException(502, "BRIEFING_PROVIDER_ERROR", "브리핑 생성에 실패했습니다. 잠시 후 다시 시도해주세요.");
    }

    static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private final int max;
        private int received;
        private Flow.Subscription subscription;
        LimitedBodySubscriber(int max) { this.max = max; }
        public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
        public void onSubscribe(Flow.Subscription s) { subscription = s; delegate.onSubscribe(s); }
        public void onNext(List<ByteBuffer> items) {
            long bytes = items.stream().mapToLong(ByteBuffer::remaining).sum();
            if (bytes > max - received) {
                subscription.cancel(); delegate.onError(new IllegalStateException("Provider response exceeds limit")); return;
            }
            received += (int) bytes; delegate.onNext(items);
        }
        public void onError(Throwable t) { delegate.onError(t); }
        public void onComplete() { delegate.onComplete(); }
    }
}
