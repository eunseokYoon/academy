package com.njwenglish.entity;

import com.njwenglish.common.entity.BaseTimeEntity;
import com.njwenglish.common.util.MonthWeeks;
import com.njwenglish.entity.enums.WeeklyTestType;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 반 × 주차 × 종류 = 그리드의 열 하나. 전체 문항 수가 여기 한 번만 산다.
 *
 * <p>셀이 없는 헤더는 남겨둔다 — 선생님이 문항 수만 먼저 적어둘 수 있어야 한다.
 * 학생·학부모 조회는 헤더가 아니라 셀 기준이라 빈 헤더가 있어도 섹션이 뜨지 않는다.
 */
@Entity
@Table(name = "weekly_tests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WeeklyTest extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_room_id", nullable = false)
    private ClassRoom classRoom;

    @Enumerated(EnumType.STRING)
    @Column(name = "test_type", nullable = false, length = 20)
    private WeeklyTestType testType;

    @Column(name = "year", nullable = false)
    private Short year;

    @Column(name = "month", nullable = false)
    private Short month;

    @Column(name = "week", nullable = false)
    private Short week;

    /** WORD·PRACTICE 전체 문항 수. */
    @Column(name = "total_count")
    private Short totalCount;

    /** CLINIC 내부지문 전체 문항 수. */
    @Column(name = "internal_total")
    private Short internalTotal;

    /** CLINIC 외부지문 전체 문항 수. */
    @Column(name = "external_total")
    private Short externalTotal;

    public static WeeklyTest create(ClassRoom classRoom, WeeklyTestType testType,
                                    short year, short month, short week,
                                    Short totalCount, Short internalTotal, Short externalTotal) {
        WeeklyTest test = new WeeklyTest();
        test.classRoom = classRoom;
        test.testType = testType;
        test.year = year;
        test.month = month;
        test.week = week;
        test.totalCount = totalCount;
        test.internalTotal = internalTotal;
        test.externalTotal = externalTotal;
        return test;
    }

    /**
     * 전체 문항 수만 바꾼다. 저장된 맞힌 개수는 그대로고 정답률만 따라 바뀐다.
     * 환산 점수를 저장하지 않는 이유가 이것이다.
     */
    public void changeTotals(Short totalCount, Short internalTotal, Short externalTotal) {
        this.totalCount = totalCount;
        this.internalTotal = internalTotal;
        this.externalTotal = externalTotal;
    }

    /** "5월 3주" — 라벨은 서버가 만든다. 프론트가 조립하면 화면마다 표기가 갈린다. */
    public String weekLabel() {
        return MonthWeeks.label(month, week);
    }
}
