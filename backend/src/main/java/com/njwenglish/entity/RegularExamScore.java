package com.njwenglish.entity;

import com.njwenglish.common.entity.BaseTimeEntity;
import com.njwenglish.entity.enums.RegularExamSlot;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 학교 내신·모의고사 원점수. <b>선생님만 본다.</b>
 *
 * <p>학생·학부모 응답 DTO에 이 엔티티가 흘러 들어가면 안 된다. 등급·과목·시험명은 없다.
 * 데이터가 학생에 붙으므로 반을 옮겨도 점수는 남는다.
 */
@Entity
@Table(name = "regular_exam_scores")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegularExamScore extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "year", nullable = false)
    private Short year;

    @Enumerated(EnumType.STRING)
    @Column(name = "exam_slot", nullable = false, length = 20)
    private RegularExamSlot examSlot;

    /** 100점 만점 원점수. */
    @Column(name = "raw_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal rawScore;

    public static RegularExamScore create(Student student, short year,
                                          RegularExamSlot examSlot, BigDecimal rawScore) {
        RegularExamScore score = new RegularExamScore();
        score.student = student;
        score.year = year;
        score.examSlot = examSlot;
        score.rawScore = rawScore;
        return score;
    }

    public void changeScore(BigDecimal rawScore) {
        this.rawScore = rawScore;
    }
}
