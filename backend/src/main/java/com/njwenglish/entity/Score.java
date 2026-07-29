package com.njwenglish.entity;

import com.njwenglish.common.entity.BaseTimeEntity;
import com.njwenglish.entity.enums.ScoreType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * uq_scores(student, score_type, subject, exam_name, exam_date)가 이중 입력을 막는다.
 * 같은 시험을 다시 저장하는 건 오타 수정이라는 정상 흐름이니 409를 던지지 말고 upsert로 처리해라.
 *
 * <p>등수·백분위·반 평균 같은 상대 지표는 계산도 노출도 하지 않는다.
 */
@Entity
@Table(name = "scores")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Score extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Enumerated(EnumType.STRING)
    @Column(name = "score_type", nullable = false, length = 20)
    private ScoreType scoreType;

    /** 내신(INTERNAL)일 때만 연결된다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_schedule_id")
    private ExamSchedule examSchedule;

    @Column(name = "exam_name", nullable = false, length = 100)
    private String examName;

    @Column(nullable = false, length = 50)
    private String subject;

    /** WORD는 반드시 100점 만점으로 환산해 저장한다. 원점수를 넣으면 주차별 그래프 축이 무너진다. */
    @Column(name = "raw_score", precision = 5, scale = 2)
    private BigDecimal rawScore;

    /** 성적 등급 1~9다. 학년이 아니다. */
    @Column(name = "grade_level")
    private Short gradeLevel;

    @Column(name = "exam_date", nullable = false)
    private LocalDate examDate;

    @Column(name = "year", nullable = false)
    private Short year;

    @Column(name = "month", nullable = false)
    private Short month;

    @Column(name = "week", nullable = false)
    private Short week;

    @Column(columnDefinition = "TEXT")
    private String memo;
}
