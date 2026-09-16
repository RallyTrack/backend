package com.rallytrack.backend.domain.briefing;

import com.rallytrack.backend.domain.analysis.dto.*;

final class BriefingPrompt {
    static final String VERSION = "v1";
    private BriefingPrompt() {}
    static String grade(int n) { return n >= 85 ? "S" : n >= 70 ? "A" : n >= 50 ? "B" : n >= 30 ? "C" : "D"; }
    private static int count(Integer n) { return n == null ? 0 : n; }
    static String create(AnalysisReportResponse report, String player) {
        var p = "top".equals(player) ? report.getPlayers().getTop() : report.getPlayers().getBottom();
        var a = p.getAbilityMetrics(); var summary = report.getSummary();
        String label = "top".equals(player) ? "Top Player" : "Bottom Player";
        var strokes = counts(report, player);
        boolean pro = java.util.stream.Stream.of(counts(report, "top"), counts(report, "bottom"))
                .anyMatch(c -> c.get("lob") > 0 || c.get("drop") > 0);
        var kinds = pro ? java.util.List.of("serve", "lob", "smash", "drop", "drive", "clear")
                : java.util.List.of("serve", "smash", "clear", "drive");
        int total = kinds.stream().mapToInt(strokes::get).sum();
        String breakdown = kinds.stream().map(k -> k + ": " + strokes.get(k) + "회")
                .collect(java.util.stream.Collectors.joining(", "));
        return """
                당신은 전문 배드민턴 코치입니다.
                아래 데이터는 경기 영상에서 분석한 [%s] 개인의 데이터입니다.
                [경기 전체 개요 — 참고용, 개인 수치 아님]
                - 경기 결과(Bottom 기준): %s (Bottom %d : Top %d)
                - 총 경기 시간: %s
                - 양측 합산 총 스트로크: %d회
                [%s 개인 스트로크]
                - 개인 스트로크 합계: %d회
                - 분류 체계: %s
                - %s
                [%s 능력치 등급 (S > A > B > C > D)]
                - 공격성 %s등급: 전체 타격 중 스매시 비율
                - 랠리력 %s등급: 랠리 지속력 및 지구력
                - 수비력 %s등급: 빠른 반응 속도
                - 기동력 %s등급: 코트 커버리지
                - 안정성 %s등급: 실책 없이 안정적으로 플레이하는 능력
                [기존 코치 피드백]
                %s
                [출력 형식]
                - 간결하게 (모바일 친화적)
                - 다음 H2 제목을 정확히 순서대로 사용: ## 총평, ## 핵심 지표, ## 강점, ## 보완점, ## 추천 훈련
                - 총평은 2~3문장. 핵심 지표는 불릿 3~5개. 강점과 보완점은 각각 불릿 2개. 추천 훈련은 불릿 3개.
                - 섹션 제목 외에는 H1/H2/H3를 쓰지 않는다.
                """.formatted(label, summary.getMatchOutcome(), summary.getMyScore(), summary.getOpponentScore(),
                summary.getMatchTime(), summary.getTotalStrokeCount(), label, total, pro ? "프로 6종" : "아마추어 4종",
                breakdown, label, grade(a.getAggression()), grade(a.getRally()), grade(a.getDefense()),
                grade(a.getMobility()), grade(a.getConsistency()),
                p.getAiCoaching() == null ? "(없음)" : p.getAiCoaching().getFeedbackText());
    }
    private static java.util.Map<String, Integer> counts(AnalysisReportResponse report, String player) {
        var p = "top".equals(player) ? report.getPlayers().getTop() : report.getPlayers().getBottom();
        var s = p.getStrokeTypes();
        var counts = new java.util.HashMap<String, Integer>();
        for (String key : java.util.List.of("serve", "lob", "smash", "drop", "drive", "clear", "net", "others")) counts.put(key, 0);
        int matched = 0;
        if (report.getHitsData() != null) for (var hit : report.getHitsData()) {
            String side = hit.getPlayer() == null ? "" : hit.getPlayer().toLowerCase(java.util.Locale.ROOT);
            if (!(player.equals(side) || ("top".equals(player) ? "pink_top" : "green_bottom").equals(side))) continue;
            String type = hit.getStrokeType() == null ? "" : hit.getStrokeType().toLowerCase(java.util.Locale.ROOT).trim();
            if (type.isEmpty()) continue;
            String key = type.contains("smash") || type.contains("스매시") ? "smash"
                    : type.contains("lob") || type.contains("로브") ? "lob"
                    : type.contains("drop") || type.contains("드롭") || type.contains("커트") ? "drop"
                    : type.contains("drive") || type.contains("드라이브") ? "drive"
                    : type.contains("serve") || type.contains("service") || type.contains("서브") ? "serve"
                    : type.contains("clear") || type.contains("클리어") ? "clear"
                    : type.contains("net") || type.contains("네트") || type.contains("헤어핀") ? "net" : "others";
            counts.merge(key, 1, Integer::sum); matched++;
        }
        if (matched == 0) {
            counts.put("serve", count(s.getServe())); counts.put("smash", count(s.getSmash()));
            counts.put("drop", count(s.getDrop())); counts.put("drive", count(s.getDrive()));
            counts.put("clear", count(s.getClear())); counts.put("net", count(s.getNet())); counts.put("others", count(s.getOthers()));
        }
        return counts;
    }
}
