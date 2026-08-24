package com.rallytrack.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Slack Incoming Webhook 알림.
 * webhook URL이 비어 있으면 아무것도 하지 않으며, 전송 실패가 본 로직에 영향을 주지 않는다.
 */
@Component
@RequiredArgsConstructor
public class SlackNotifier {

    private final RestTemplate restTemplate;

    // env: SLACK_BACK_WEBHOOK (rally-track-back-log 채널)
    @Value("${app.slack.webhook-url:}")
    private String webhookUrl;

    public void notify(String text) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return;
        }
        try {
            restTemplate.postForEntity(webhookUrl, Map.of("text", text), String.class);
        } catch (Exception e) {
            System.err.println("[Slack 알림 실패] " + e.getMessage());
        }
    }
}
