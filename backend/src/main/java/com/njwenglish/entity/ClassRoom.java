package com.njwenglish.entity;

import com.njwenglish.common.entity.BaseTimeEntity;
import com.njwenglish.entity.enums.ClassRoomStatus;
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
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 학생을 묶는 유일한 단위다. 학교·학년은 시스템에 없고 이름 하나로 구분한다.
 *
 * <p>joinCodeActive가 이 가입 경로의 유일한 방어선이다. 반 코드는 전화번호 대조가
 * 없어서 코드를 아는 사람은 누구나 가입한다. 등록 기간이 끝나면 반드시 닫아라.
 */
@Entity
@Table(name = "class_rooms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClassRoom extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private Teacher teacher;

    @Column(name = "join_code", nullable = false, unique = true, length = 10)
    private String joinCode;

    @Column(name = "join_code_active", nullable = false)
    private boolean joinCodeActive;

    /** 1=월요일 ~ 7=일요일 (ISO-8601, java.time.DayOfWeek.getValue()와 동일) */
    @Column(name = "day_of_week")
    private Short dayOfWeek;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "term_start")
    private LocalDate termStart;

    @Column(name = "term_end")
    private LocalDate termEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClassRoomStatus status;

    @Column(columnDefinition = "TEXT")
    private String memo;
}
