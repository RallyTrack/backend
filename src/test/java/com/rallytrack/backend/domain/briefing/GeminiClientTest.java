package com.rallytrack.backend.domain.briefing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rallytrack.backend.global.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(OutputCaptureExtension.class)
class GeminiClientTest {
    HttpClient http = mock(HttpClient.class);
    GeminiClient client = new GeminiClient(new ObjectMapper(), "synthetic-provider-secret", "gemini-test", true, http, Duration.ofMillis(100));
    @SuppressWarnings("unchecked")
    HttpResponse<byte[]> response(int status, String body) {
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status); when(response.body()).thenReturn(body.getBytes()); return response;
    }
    @Test void sendsCredentialOnlyAsHeaderToFixedProviderAndExtractsText() throws Exception {
        doReturn(CompletableFuture.completedFuture(response(200,"{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\"safe coaching\"}]}}]}")))
                .when(http).sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
        assertThat(client.generate("synthetic stats")).isEqualTo("safe coaching");
        var request=org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).sendAsync(request.capture(),any(HttpResponse.BodyHandler.class));
        assertThat(request.getValue().uri().toString()).isEqualTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-test:generateContent");
        assertThat(request.getValue().headers().firstValue("x-goog-api-key")).contains("synthetic-provider-secret");
    }
    @Test void reservesOutputBudgetForBriefingInsteadOfDefaultDynamicThinking() throws Exception {
        doReturn(CompletableFuture.completedFuture(response(200,"{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\"complete briefing\"}]}}]}")))
                .when(http).sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
        client.generate("synthetic match statistics");
        var request=org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).sendAsync(request.capture(),any(HttpResponse.BodyHandler.class));
        var bytes = new ByteArrayOutputStream();
        var received = new CompletableFuture<byte[]>();
        request.getValue().bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            public void onNext(ByteBuffer buffer) {
                byte[] chunk = new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk);
            }
            public void onError(Throwable error) { received.completeExceptionally(error); }
            public void onComplete() { received.complete(bytes.toByteArray()); }
        });
        var body = new ObjectMapper().readTree(received.get(1, TimeUnit.SECONDS));
        var config = body.path("generationConfig");
        // Gemini counts hidden thinking against maxOutputTokens. Our production-shaped
        // fixture exhausted 1438/1500 tokens before producing a complete answer.
        assertThat(config.path("thinkingConfig").path("thinkingLevel").asText()).isEqualTo("low");
        assertThat(config.path("maxOutputTokens").asInt()).isEqualTo(1500);
        assertThat(config.path("thinkingConfig").has("thinkingBudget")).isFalse();
        assertThat(new String(received.get(), java.nio.charset.StandardCharsets.UTF_8))
                .doesNotContain("synthetic-provider-secret");
    }
    @Test void truncatedAnswerIsRejectedWithoutExposingItsText(CapturedOutput output) {
        doReturn(CompletableFuture.completedFuture(response(200,"{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\",\"content\":{\"parts\":[{\"text\":\"private unfinished coaching\"}]}}],\"usageMetadata\":{\"thoughtsTokenCount\":1438,\"candidatesTokenCount\":56}}")))
                .when(http).sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
        assertThatThrownBy(() -> client.generate("private match statistics"))
                .isInstanceOf(ApiException.class).hasMessageNotContaining("private")
                .extracting("errorCode").isEqualTo("BRIEFING_OUTPUT_LIMIT");
        assertThat(output.getAll()).contains("finishReason=MAX_TOKENS", "outputTokens=56", "thoughtTokens=1438")
                .doesNotContain("private unfinished coaching", "private match statistics", "synthetic-provider-secret");
    }
    @Test void disabledMissingKeyAndOversizedPromptDoNotReachProvider() {
        for (var instance : List.of(new GeminiClient(new ObjectMapper(),"","test",true,http,Duration.ofSeconds(1)),
                new GeminiClient(new ObjectMapper(),"secret","test",false,http,Duration.ofSeconds(1)))) {
            assertThatThrownBy(()->instance.generate("test")).isInstanceOf(ApiException.class).extracting("status").isEqualTo(503);
        }
        assertThatThrownBy(()->client.generate("x".repeat(12001))).isInstanceOf(ApiException.class).extracting("status").isEqualTo(400);
        verifyNoInteractions(http);
    }
    @Test void providerErrorsAndInvalidBodiesNeverLeakProviderContent() {
        for (var response : List.of(response(403,"synthetic-provider-secret"),response(200,"not json"),
                response(200,"{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\"}]}"))) {
            doReturn(CompletableFuture.completedFuture(response)).when(http).sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
            assertThatThrownBy(()->client.generate("stats")).isInstanceOf(ApiException.class).hasMessageNotContaining("synthetic-provider-secret")
                    .extracting("status").isEqualTo(502);
        }
    }
    @Test void totalTimeoutCancelsRequestAndTransportTimeoutHasSameStatus() {
        CompletableFuture<HttpResponse<byte[]>> stuck = new CompletableFuture<>();
        doReturn(stuck).when(http).sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
        assertThatThrownBy(()->client.generate("stats")).isInstanceOf(ApiException.class).extracting("status").isEqualTo(504);
        assertThat(stuck.isCancelled()).isTrue();
        doReturn(CompletableFuture.failedFuture(new HttpTimeoutException("secret upstream error")))
                .when(http).sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
        assertThatThrownBy(()->client.generate("stats")).isInstanceOf(ApiException.class).extracting("status").isEqualTo(504);
    }
    @Test void responseReaderCancelsAtByteLimit() {
        var body=new GeminiClient.LimitedBodySubscriber(8);
        var subscription=mock(java.util.concurrent.Flow.Subscription.class);
        body.onSubscribe(subscription);body.onNext(List.of(ByteBuffer.wrap(new byte[9])));
        verify(subscription).cancel();assertThat(body.getBody().toCompletableFuture()).isCompletedExceptionally();
    }
}
