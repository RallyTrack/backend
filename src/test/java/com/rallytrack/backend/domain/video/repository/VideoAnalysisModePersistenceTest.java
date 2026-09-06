package com.rallytrack.backend.domain.video.repository;

import com.rallytrack.backend.domain.user.entity.User;
import com.rallytrack.backend.domain.user.repository.UserRepository;
import com.rallytrack.backend.domain.video.entity.Video;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class VideoAnalysisModePersistenceTest {

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsProAndAmateurModesSeparately() {
        User user = userRepository.save(User.builder()
                .email("mode-test@example.com")
                .password("test")
                .nickname("mode-test")
                .build());

        Long proId = videoRepository.save(Video.builder()
                .title("pro video")
                .analysisMode("pro")
                .user(user)
                .build()).getVideoId();
        Long amateurId = videoRepository.save(Video.builder()
                .title("amateur video")
                .analysisMode("amateur")
                .user(user)
                .build()).getVideoId();

        videoRepository.flush();
        entityManager.clear();

        assertThat(videoRepository.findById(proId).orElseThrow().getAnalysisMode())
                .isEqualTo("pro");
        assertThat(videoRepository.findById(amateurId).orElseThrow().getAnalysisMode())
                .isEqualTo("amateur");
    }
}
