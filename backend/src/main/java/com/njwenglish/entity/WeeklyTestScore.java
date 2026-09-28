package com.njwenglish.entity;

import com.njwenglish.common.entity.BaseTimeEntity;
import com.njwenglish.entity.enums.TestResult;
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
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 학생 한 명의 한 칸. <b>선생님이 채운 칸만 행이 생긴다.</b>
 *
 * <p>숙제·출석과 달리 미리 깔지 않는다(CLAUDE.md 4번과 반대 방향). 성적은 미제출자를
 * 역산할 일이 없고, 빈 행이 남으면 학부모 화면에 빈 항목으로 샌다.
 *
 * <p>재시험 점수는 별도 칸이 없다. correctCount를 덮어쓴다.
 */
@Entity
@Table(name = "weekly_test_scores")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WeeklyTestScore extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "weekly_test_id", nullable = false)
    private WeeklyTest weeklyTest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "correct_count")
    private Short correctCount;

    @Column(name = "internal_correct")
    private Short internalCorrect;

    @Column(name = "external_correct")
    private Short externalCorrect;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private TestResult result;

    @Column(name = "retest_passed", nullable = false)
    private boolean retestPassed;

    public static WeeklyTestScore create(WeeklyTest weeklyTest, Student student,
                                         Short correctCount, Short internalCorrect,
                                         Short externalCorrect, TestResult result,
                                         boolean retestPassed) {
        WeeklyTestScore score = new WeeklyTestScore();
        score.weeklyTest = weeklyTest;
        score.student = student;
        score.correctCount = correctCount;
        score.internalCorrect = internalCorrect;
        score.externalCorrect = externalCorrect;
        score.result = result;
        score.retestPassed = retestPassed;
        return score;
    }

    /** 같은 칸을 다시 저장하는 건 오타 수정이라는 정상 흐름이다. 409를 던지지 마라. */
    /** @return 값이 하나라도 바뀌었으면 true. 같은 값을 다시 저장한 칸에는 알림이 가지 않는다. */
    public boolean rewrite(Short correctCount, Short internalCorrect, Short externalCorrect,
                           TestResult result, boolean retestPassed) {
        boolean changed = !Objects.equals(this.correctCount, correctCount)
            || !Objects.equals(this.internalCorrect, internalCorrect)
            || !Objects.equals(this.externalCorrect, externalCorrect)
            || this.result != result
            || this.retestPassed != retestPassed;
        this.correctCount = correctCount;
        this.internalCorrect = internalCorrect;
        this.externalCorrect = externalCorrect;
        this.result = result;
        this.retestPassed = retestPassed;
        return changed;
    }

    /** 값이 전부 비었으면 행을 지운다. ck_wts_not_empty가 DB에서도 막는다. */
    public boolean isEmpty() {
        return correctCount == null && internalCorrect == null
            && externalCorrect == null && result == null;
    }

    /**
     * 미통과인데 아직 재시험 통과 체크가 안 된 상태다.
     * <b>이 판정은 서버가 한다.</b> 프론트에서 다시 계산하면 화면마다 규칙이 갈린다.
     */
    public boolean isRetestScheduled() {
        return result == TestResult.FAIL && !retestPassed;
    }
}
