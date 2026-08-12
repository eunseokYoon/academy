package com.njwenglish.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 반의 주간 수업 슬롯 하나. 요일당 하나다 (uq_crs_day).
 *
 * <p>행은 수정 시 통째로 지워지고 다시 생긴다. <b>이 id를 다른 테이블에서 참조하지 마라.</b>
 */
@Entity
@Table(name = "class_room_schedules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClassRoomSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_room_id", nullable = false)
    private ClassRoom classRoom;

    /** 1=월요일 ~ 7=일요일 (ISO-8601, java.time.DayOfWeek.getValue()와 동일) */
    @Column(name = "day_of_week", nullable = false)
    private Short dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    /** 표시 전용이다. 기존 반 이관분은 근거가 없어 null이다. */
    @Column(name = "end_time")
    private LocalTime endTime;

    /** 요일은 안 바꾼다 — 요일이 이 행의 식별자(uq_crs_day)라 바뀌면 다른 슬롯이다. */
    void changeTime(LocalTime startTime, LocalTime endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
    }

    static ClassRoomSchedule create(ClassRoom classRoom, Short dayOfWeek,
                                    LocalTime startTime, LocalTime endTime) {
        ClassRoomSchedule schedule = new ClassRoomSchedule();
        schedule.classRoom = classRoom;
        schedule.dayOfWeek = dayOfWeek;
        schedule.startTime = startTime;
        schedule.endTime = endTime;
        return schedule;
    }
}
