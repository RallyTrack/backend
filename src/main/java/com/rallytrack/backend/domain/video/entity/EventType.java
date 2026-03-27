package com.rallytrack.backend.domain.video.entity;

/**
 * 타임라인 이벤트 타입
 *
 * HIT      : 타점 이벤트 (hits_data 기반)
 * GAME_START : 경기 시작
 * GAME_END   : 경기 종료 (추후 확장)
 */
public enum EventType {
    HIT,
    GAME_START,
    GAME_END
}
