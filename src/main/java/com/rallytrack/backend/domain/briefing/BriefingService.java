package com.rallytrack.backend.domain.briefing;

import com.rallytrack.backend.domain.analysis.service.AnalysisService;
import com.rallytrack.backend.global.exception.ApiException;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;

@Service
public class BriefingService {
    private record Cached(String text, long until) {}
    private record Usage(long window, int count) {}
    private final AnalysisService analysis;
    private final GeminiClient client;
    private final Clock clock;
    private final Map<String, Cached> cache = new LinkedHashMap<>();
    private final Map<String, CompletableFuture<String>> inFlight = new HashMap<>();
    private final Map<Long, Usage> users = new HashMap<>();
    private Usage global = new Usage(0, 0);
    private final Object lock = new Object();

    @org.springframework.beans.factory.annotation.Autowired
    public BriefingService(AnalysisService analysis, GeminiClient client) { this(analysis, client, Clock.systemUTC()); }
    BriefingService(AnalysisService analysis, GeminiClient client, Clock clock) {
        this.analysis = analysis; this.client = client; this.clock = clock;
    }

    public String generate(Long userId, Long videoId, String player) {
        if (!Set.of("top", "bottom").contains(player == null ? "" : player))
            throw new ApiException(400, "INVALID_PLAYER", "선수 위치가 올바르지 않습니다.");
        // This MUST precede both the cache and the provider, even for an already cached video.
        var report = analysis.getReport(userId, videoId);
        client.requireEnabled();
        String prompt = BriefingPrompt.create(report, player);
        String key = userId + ":" + videoId + ":" + player + ":" + client.model() + ":" + BriefingPrompt.VERSION + ":" + digest(prompt);
        CompletableFuture<String> future;
        boolean leader = false;
        synchronized (lock) {
            long now = clock.millis();
            cache.values().removeIf(c -> c.until <= now);
            Cached existing = cache.get(key);
            if (existing != null) return existing.text;
            future = inFlight.get(key);
            if (future == null) {
                long window = now / 60000;
                users.values().removeIf(u -> u.window != window);
                Usage user = users.getOrDefault(userId, new Usage(window, 0));
                if (global.window != window) global = new Usage(window, 0);
                if (user.count >= 6 || global.count >= 30 || inFlight.size() >= 2 || users.size() >= 10000)
                    throw new ApiException(429, "BRIEFING_RATE_LIMIT", "요청이 많습니다. 잠시 후 다시 시도해주세요.");
                users.put(userId, new Usage(window, user.count + 1));
                global = new Usage(window, global.count + 1);
                future = new CompletableFuture<>(); inFlight.put(key, future); leader = true;
            }
        }
        if (leader) {
            try {
                String text = client.generate(prompt);
                synchronized (lock) {
                    if (cache.size() >= 256) cache.remove(cache.keySet().iterator().next());
                    cache.put(key, new Cached(text, clock.millis() + 1800000));
                }
                future.complete(text);
            } catch (RuntimeException e) { future.completeExceptionally(e);
            } finally { synchronized (lock) { inFlight.remove(key); } }
        }
        try { return future.get(35, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof ApiException a) throw a;
            throw new ApiException(502, "BRIEFING_PROVIDER_ERROR", "브리핑을 생성하지 못했습니다.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new ApiException(503, "BRIEFING_UNAVAILABLE", "브리핑이 중단되었습니다.");
        } catch (TimeoutException e) { throw new ApiException(504, "BRIEFING_TIMEOUT", "브리핑 응답이 지연되고 있습니다."); }
    }
    private String digest(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
