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

    /**
     * joinCode는 서버가 만든 값만 받는다. 선생님이 직접 정하면 「고2반」처럼
     * 추측 가능한 값이 들어가고, 반 코드는 전화번호 대조가 없어 그대로 뚫린다.
     */
    public static ClassRoom create(Teacher teacher, String name, String joinCode,
                                   Short dayOfWeek, LocalTime startTime,
                                   LocalDate termStart, LocalDate termEnd, String memo) {
        ClassRoom classRoom = new ClassRoom();
        classRoom.teacher = teacher;
        classRoom.name = name;
        classRoom.joinCode = joinCode;
        classRoom.joinCodeActive = true;
        classRoom.dayOfWeek = dayOfWeek;
        classRoom.startTime = startTime;
        classRoom.termStart = termStart;
        classRoom.termEnd = termEnd;
        classRoom.memo = memo;
        classRoom.status = ClassRoomStatus.ACTIVE;
        return classRoom;
    }

    /** 이름 변경은 과거 기록에도 소급 적용된다. lessons·attendances가 이 행을 참조한다. */
    public void rename(String name) {
        this.name = name;
    }

    public void changeSchedule(Short dayOfWeek, LocalTime startTime) {
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
    }

    public void changeTerm(LocalDate termStart, LocalDate termEnd) {
        this.termStart = termStart;
        this.termEnd = termEnd;
    }

    public void changeMemo(String memo) {
        this.memo = memo;
    }

    /** 학기가 끝난 반. 삭제와 달리 수업·출석 기록이 남는다. 코드도 함께 닫는다. */
    public void close() {
        this.status = ClassRoomStatus.CLOSED;
        this.joinCodeActive = false;
    }

    public boolean isActive() {
        return status == ClassRoomStatus.ACTIVE;
    }

    /** 재발급하면 이전 코드는 즉시 무효다. 이미 가입한 학생의 enrollments는 그대로다. */
    public void regenerateJoinCode(String joinCode) {
        this.joinCode = joinCode;
    }

    /** 등록 기간이 끝나면 닫는다. 이 값이 반 코드 가입의 유일한 방어선이다. */
    public void changeJoinCodeActive(boolean active) {
        this.joinCodeActive = active;
    }
}
