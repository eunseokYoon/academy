package com.njwenglish.dto.score;

import com.njwenglish.entity.Score;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * P-4 · S-7 응답. 표가 아니라 <b>주차별 시계열</b>이다.
 * 학부모가 보려는 건 자녀의 단어 테스트 흐름이다.
 *
 * <p><b>등수·백분위·반 평균은 어디에도 넣지 마라.</b> 요구사항에 없고 학부모 간 비교로 이어진다.
 * 서버는 원본만 내려주고 차트 계산은 프론트에서 한다 — 별도 통계 API를 만들지 마라.
 */
public record ScoreChartResponse(
    WordSeries word,
    List<Item> internal,
    List<Item> mock
) {
    /** unit은 화면 축 라벨이다. 주차 단위 고정이라 값이 하나지만 프론트가 하드코딩하지 않게 내려준다. */
    public record WordSeries(String unit, List<Point> points) {
    }

    /**
     * label은 서버가 만든다 ("5월 3주"). 프론트가 조립하면 표기가 화면마다 갈린다.
     * score는 100점 만점 환산값이다.
     */
    public record Point(
        Short year,
        Short month,
        Short week,
        String label,
        BigDecimal score,
        String examName,
        LocalDate examDate
    ) {
        public static Point from(Score score) {
            return new Point(
                score.getYear(), score.getMonth(), score.getWeek(),
                score.getMonth() + "월 " + score.getWeek() + "주",
                score.getRawScore(), score.getExamName(), score.getExamDate());
        }
    }

    /** 내신·모의. examDate 내림차순이다. */
    public record Item(
        Long scoreId,
        String examName,
        String subject,
        BigDecimal rawScore,
        Short gradeLevel,
        LocalDate examDate,
        String memo
    ) {
        public static Item from(Score score) {
            return new Item(
                score.getId(), score.getExamName(), score.getSubject(),
                score.getRawScore(), score.getGradeLevel(), score.getExamDate(),
                score.getMemo());
        }
    }
}
