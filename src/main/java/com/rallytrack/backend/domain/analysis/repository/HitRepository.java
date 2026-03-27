package com.rallytrack.backend.domain.analysis.repository;

import com.rallytrack.backend.domain.analysis.entity.Hit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HitRepository extends JpaRepository<Hit, Long> {
    List<Hit> findByAnalysisResultAnalysisIdOrderByHitNumberAsc(Long analysisId);
    void deleteByAnalysisResultAnalysisId(Long analysisId);
}
