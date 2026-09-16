package com.rallytrack.backend.domain.briefing;

import com.rallytrack.backend.domain.analysis.dto.*;
import com.rallytrack.backend.domain.analysis.service.AnalysisService;
import com.rallytrack.backend.global.exception.ApiException;
import org.junit.jupiter.api.*;
import java.util.concurrent.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class BriefingServiceTest {
    AnalysisService analysis = mock(AnalysisService.class);
    GeminiClient client = mock(GeminiClient.class);
    BriefingService service = new BriefingService(analysis, client, java.time.Clock.fixed(java.time.Instant.parse("2026-09-16T00:00:00Z"),java.time.ZoneOffset.UTC));
    AnalysisReportResponse report(int score) {
        var player = PlayerReportDto.builder().abilityMetrics(AbilityMetricsDto.builder().aggression(85).rally(70).defense(50).mobility(30).consistency(0).build())
                .strokeTypes(StrokeTypesDto.builder().smash(1).clear(2).build()).build();
        return AnalysisReportResponse.builder().summary(SummaryDto.builder().myScore(score).opponentScore(2).matchOutcome("WIN").matchTime("1:00").totalStrokeCount(3).build())
                .players(PlayersDto.builder().top(player).bottom(player).build()).build();
    }
    @BeforeEach void setup() {
        when(analysis.getReport(anyLong(), anyLong())).thenReturn(report(3));
        when(client.model()).thenReturn("test-model"); when(client.generate(anyString())).thenReturn("safe text");
    }
    @Test void cacheSeparatesPlayersAndChangesWhenScoreChanges() {
        assertThat(service.generate(1L,1L,"top")).isEqualTo("safe text");
        service.generate(1L,1L,"top"); verify(client, times(1)).generate(anyString());
        service.generate(1L,1L,"bottom"); verify(client,times(2)).generate(anyString());
        when(analysis.getReport(1L,1L)).thenReturn(report(4));
        service.generate(1L,1L,"top"); verify(client,times(3)).generate(anyString());
    }
    @Test void authorizationIsCheckedEvenAfterCaching() {
        service.generate(1L,1L,"top");
        when(analysis.getReport(1L,1L)).thenThrow(ApiException.notFound());
        assertThatThrownBy(() -> service.generate(1L,1L,"top")).isInstanceOf(ApiException.class).extracting("status").isEqualTo(404);
        verify(client,times(1)).generate(anyString());
    }
    @Test void userRateLimitAndDisabledModeNeverCallProvider() {
        for (long id=1; id<=6; id++) service.generate(1L,id,"top");
        assertThatThrownBy(() -> service.generate(1L,7L,"top")).isInstanceOf(ApiException.class).extracting("status").isEqualTo(429);
        verify(client,times(6)).generate(anyString());
        doThrow(new ApiException(503,"BRIEFING_UNAVAILABLE","disabled")).when(client).requireEnabled();
        assertThatThrownBy(() -> service.generate(2L,1L,"top")).isInstanceOf(ApiException.class).extracting("status").isEqualTo(503);
        verify(client,times(6)).generate(anyString());
    }
    @Test void globalRateLimitAppliesAcrossUsers() {
        for (long id=1; id<=30; id++) service.generate(id,id,"top");
        assertThatThrownBy(() -> service.generate(31L,31L,"top")).isInstanceOf(ApiException.class).extracting("status").isEqualTo(429);
    }
    @Test void identicalConcurrentRequestsShareOneGeneration() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        when(client.generate(anyString())).thenAnswer(i -> { entered.countDown(); release.await(5,TimeUnit.SECONDS); return "one"; });
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            var first = executor.submit(() -> service.generate(1L,1L,"top"));
            assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> service.generate(1L,1L,"top"));
            var different = executor.submit(() -> service.generate(2L,2L,"top"));
            // Wait until both distinct generations entered the provider; no arbitrary sleeps.
            verify(client, timeout(2000).times(2)).generate(anyString());
            assertThatThrownBy(() -> service.generate(3L,3L,"top")).isInstanceOf(ApiException.class).extracting("status").isEqualTo(429);
            release.countDown();
            assertThat(first.get()).isEqualTo("one"); assertThat(second.get()).isEqualTo("one"); assertThat(different.get()).isEqualTo("one");
            verify(client,times(2)).generate(anyString());
        } finally { release.countDown(); executor.shutdownNow(); }
    }
    @Test void failuresAreNotCachedAndInvalidPlayerCannotTriggerRead() {
        when(client.generate(anyString())).thenThrow(new ApiException(504,"BRIEFING_TIMEOUT","timeout")).thenReturn("recovered");
        assertThatThrownBy(() -> service.generate(1L,1L,"top")).isInstanceOf(ApiException.class).extracting("status").isEqualTo(504);
        assertThat(service.generate(1L,1L,"top")).isEqualTo("recovered");
        clearInvocations(analysis,client);
        assertThatThrownBy(() -> service.generate(1L,1L,"custom prompt")).isInstanceOf(ApiException.class);
        verifyNoInteractions(analysis,client);
    }
    @Test void gradeBoundariesMatchExistingFrontendContract() {
        assertThat(BriefingPrompt.grade(85)).isEqualTo("S"); assertThat(BriefingPrompt.grade(84)).isEqualTo("A");
        assertThat(BriefingPrompt.grade(70)).isEqualTo("A"); assertThat(BriefingPrompt.grade(69)).isEqualTo("B");
        assertThat(BriefingPrompt.grade(50)).isEqualTo("B"); assertThat(BriefingPrompt.grade(49)).isEqualTo("C");
        assertThat(BriefingPrompt.grade(30)).isEqualTo("C"); assertThat(BriefingPrompt.grade(29)).isEqualTo("D");
    }

    @Test void promptUsesActualHitLabelsAndCurrentFiveSectionFormat() {
        var hitReport = report(3);
        // Rebuild immutable DTO with real per-hit labels, as the frontend does.
        var withHits = AnalysisReportResponse.builder().summary(hitReport.getSummary()).players(hitReport.getPlayers())
                .hitsData(java.util.List.of(AnalysisReportResponse.HitDto.builder().player("pink_top").strokeType("Lob").build(),
                        AnalysisReportResponse.HitDto.builder().player("pink_top").strokeType("스매시").build())).build();
        String prompt = BriefingPrompt.create(withHits,"top");
        assertThat(prompt).contains("프로 6종", "lob: 1회", "smash: 1회", "개인 스트로크 합계: 2회", "## 총평", "## 추천 훈련");
    }
}
