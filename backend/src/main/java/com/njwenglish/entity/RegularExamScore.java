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
 * <p>학생·학부모 응답 DTO에 이 엔티티가 흘러 들어가면 안 된다.
 * 데이터가 학생에 붙으므로 반을 옮겨도 점수는 남는다. 과목은 영어 하나뿐이라 컬럼이 없다.
 *
 * <p><b>등급·등수는 상대 지표 금지 규칙의 좁은 예외다(2026-08-24 확정).</b>
 * 학교·평가원이 매긴 값을 선생님이 <b>받아 적는 것만</b> 해당한다 — 학원이 자기 학생을
 * 줄 세워 계산하는 것은 여전히 금지다. 그래서 컬럼명도 schoolRank다.
 *
 * <p>세 값 모두 nullable이다. 등급만 알고 원점수는 모르는 칸이 흔하다.
 * <b>셋 다 비면 행을 두지 마라</b> — ck_res_any가 DB에서 막는다.
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

    /** 100점 만점 원점수. 등급만 아는 칸이 있어 nullable이다. */
    @Column(name = "raw_score", precision = 5, scale = 2)
    private BigDecimal rawScore;

    /** 1~9등급. 내신·모의 둘 다 9등급제다. */
    @Column(name = "grade")
    private Short grade;

    /** 학교가 매긴 석차. <b>내신 슬롯에만 값이 있다</b> (RegularExamSlot.hasSchoolRank). */
    @Column(name = "school_rank")
    private Short schoolRank;

    public static RegularExamScore create(Student student, short year, RegularExamSlot examSlot,
                                          BigDecimal rawScore, Short grade, Short schoolRank) {
        RegularExamScore score = new RegularExamScore();
        score.student = student;
        score.year = year;
        score.examSlot = examSlot;
        score.rawScore = rawScore;
        score.grade = grade;
        score.schoolRank = schoolRank;
        return score;
    }

    /** 세 값을 함께 바꾼다. 따로 두면 한 칸만 지우는 경로가 생겨 규칙이 갈라진다. */
    public void change(BigDecimal rawScore, Short grade, Short schoolRank) {
        this.rawScore = rawScore;
        this.grade = grade;
        this.schoolRank = schoolRank;
    }
}
