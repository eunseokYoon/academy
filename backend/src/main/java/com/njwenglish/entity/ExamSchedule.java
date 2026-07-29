package com.njwenglish.entity;

import com.njwenglish.common.entity.BaseTimeEntity;
import com.njwenglish.entity.enums.ExamType;
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
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 학생·학부모 홈 D-day의 유일한 근거 데이터다. 등록이 없으면 D-day가 표시되지 않는다.
 *
 * <p>반 단위라 같은 시험도 반이 여러 개면 그 수만큼 행이 생긴다.
 * T-11 화면에서 반 다중 선택으로 한 번에 만들게 하고, 한 반이라도 빠지지 않게 하라.
 */
@Entity
@Table(name = "exam_schedules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExamSchedule extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_room_id", nullable = false)
    private ClassRoom classRoom;

    @Column(name = "year", nullable = false)
    private Short year;

    @Column(nullable = false)
    private Short semester;

    @Enumerated(EnumType.STRING)
    @Column(name = "exam_type", nullable = false, length = 20)
    private ExamType examType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "scope_note", columnDefinition = "TEXT")
    private String scopeNote;
}
