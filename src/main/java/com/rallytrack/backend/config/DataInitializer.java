package com.rallytrack.backend.config;

import com.rallytrack.backend.domain.analysis.entity.AnalysisResult;
import com.rallytrack.backend.domain.analysis.repository.AnalysisResultRepository;
import com.rallytrack.backend.domain.user.entity.User;
import com.rallytrack.backend.domain.user.repository.UserRepository;
import com.rallytrack.backend.domain.video.entity.TimelineEvent;
import com.rallytrack.backend.domain.video.entity.Video;
import com.rallytrack.backend.domain.video.repository.TimelineEventRepository;
import com.rallytrack.backend.domain.video.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final VideoRepository videoRepository;
    private final TimelineEventRepository timelineEventRepository;
    private final AnalysisResultRepository analysisResultRepository;

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.info("데이터가 이미 존재합니다. 초기화를 건너뜁니다.");
            return;
        }

        log.info("===== 테스트 데이터 초기화 시작 =====");

        // 1. 테스트 유저 생성
        User user = userRepository.save(User.builder()
                .email("example@email.com")
                .password("testpass123!")
                .nickname("테스트유저")
                .build());

        // 2. 영상 3개 생성
        Video video1 = videoRepository.save(Video.builder()
                .title("주말 복식 경기")
                .s3Url("https://s3.ap-northeast-2.amazonaws.com/rallytrack/video_101.mp4")
                .thumbnailUrl("https://s3.ap-northeast-2.amazonaws.com/rallytrack/thumb_101.jpg")
                .duration(2723).matchDate(LocalDate.of(2026, 1, 2))
                .matchScore("21-18, 19-21, 21-16").videoStatus("COMPLETED").user(user).build());

        Video video2 = videoRepository.save(Video.builder()
                .title("연습 경기 - 스매시 집중")
                .s3Url("https://s3.ap-northeast-2.amazonaws.com/rallytrack/video_102.mp4")
                .thumbnailUrl("https://s3.ap-northeast-2.amazonaws.com/rallytrack/thumb_102.jpg")
                .duration(1935).matchDate(LocalDate.of(2025, 12, 28))
                .matchScore("21-15, 21-12").videoStatus("COMPLETED").user(user).build());

        Video video3 = videoRepository.save(Video.builder()
                .title("클럽 대회 준결승")
                .s3Url("https://s3.ap-northeast-2.amazonaws.com/rallytrack/video_103.mp4")
                .thumbnailUrl("https://s3.ap-northeast-2.amazonaws.com/rallytrack/thumb_103.jpg")
                .duration(3520).matchDate(LocalDate.of(2025, 12, 20))
                .matchScore("18-21, 21-19, 21-17").videoStatus("COMPLETED").user(user).build());

        // 3. 타임라인 이벤트 (video1 기준)
        timelineEventRepository.saveAll(List.of(
                TimelineEvent.builder().video(video1).timestamp(0).displayTime("0:00")
                        .eventType("GAME_START").eventTitle("경기 시작").eventDescription("1세트 시작").build(),
                TimelineEvent.builder().video(video1).timestamp(120).displayTime("2:00")
                        .eventType("SCORE").eventTitle("득점").eventDescription("스매시 득점 (1-0)").build(),
                TimelineEvent.builder().video(video1).timestamp(245).displayTime("4:05")
                        .eventType("CONCEDE").eventTitle("실점 (롱 랠리)").eventDescription("네트 실수 (1-1)").build(),
                TimelineEvent.builder().video(video1).timestamp(580).displayTime("9:40")
                        .eventType("SMASH").eventTitle("스매시 (점수 11-20)").eventDescription("완벽한 타이밍의 스매시").build()
        ));

        // 4. 분석 결과 (video1 기준)
        analysisResultRepository.save(AnalysisResult.builder()
                .video(video1)
                .myScore(21).opponentScore(18).matchOutcome("WIN")
                .totalStrokeCount(165).matchTime("45:23")
                .heatmapData("[{\"x\":10.5,\"y\":20.2},{\"x\":50.1,\"y\":80.8},{\"x\":30.0,\"y\":45.5}]")
                .strokeTypes("{\"smash\":45,\"clear\":38,\"drop\":32,\"drive\":28,\"lob\":22}")
                .abilityMetrics("{\"smash\":85,\"defense\":72,\"speed\":78,\"stamina\":65,\"accuracy\":82}")
                .aiFeedback("스매시와 정확도가 뛰어납니다. 특히 전반부에서 강력한 스매시로 득점을 올렸습니다. 수비 시 포지셔닝을 개선하면 더 좋은 결과를 얻을 수 있습니다.")
                .build());

        log.info("===== 테스트 데이터 초기화 완료 =====");
    }
}
