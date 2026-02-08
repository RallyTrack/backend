package com.rallytrack.backend.domain.analysis.repository;

import com.rallytrack.backend.domain.analysis.entity.AnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AnalysisResultRepository extends JpaRepository<AnalysisResult, Long> {
    Optional<AnalysisResult> findByVideoVideoId(Long videoId);
}
