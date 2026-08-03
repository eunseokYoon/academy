package com.njwenglish.entity;

import com.njwenglish.common.entity.BaseTimeEntity;
import com.njwenglish.entity.enums.ClassRoomStatus;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
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

    /**
     * 요일당 하나. 수정은 replaceSchedules로 통째 교체한다.
     * OrderBy를 두는 이유는 목록·상세·대시보드에서 순서가 갈리지 않게 하려는 것이다.
     */
    @OneToMany(mappedBy = "classRoom", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dayOfWeek asc, startTime asc")
    private List<ClassRoomSchedule> schedules = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClassRoomStatus status;

    @Column(columnDefinition = "TEXT")
    private String memo;

    /**
     * joinCode는 서버가 만든 값만 받는다. 선생님이 직접 정하면 「고2반」처럼
     * 추측 가능한 값이 들어가고, 반 코드는 전화번호 대조가 없어 그대로 뚫린다.
     */
    public static ClassRoom create(Teacher teacher, String name, String joinCode, String memo) {
        ClassRoom classRoom = new ClassRoom();
        classRoom.teacher = teacher;
        classRoom.name = name;
        classRoom.joinCode = joinCode;
        classRoom.joinCodeActive = true;
        classRoom.memo = memo;
        classRoom.status = ClassRoomStatus.ACTIVE;
        return classRoom;
    }

    /** 이름 변경은 과거 기록에도 소급 적용된다. lessons·attendances가 이 행을 참조한다. */
    public void rename(String name) {
        this.name = name;
    }

    /**
     * 스케줄을 slots와 같은 상태로 만든다. 개별 슬롯 id를 밖에서 다루지 않는다 —
     * 슬롯이 1~3개고 화면은 폼 하나라 id를 주고받으면 프론트 상태만 늘고 얻는 것이 없다.
     *
     * <p><b>전량 삭제 후 재삽입이 아니라 요일 기준 차집합이다.</b> Hibernate는 flush에서
     * INSERT를 DELETE보다 먼저 실행해서, 지우고 다시 넣으면 같은 요일이 잠깐 두 줄이 되어
     * uq_crs_day에 걸린다. 남길 요일은 시각만 고치고, 빠진 요일만 지운다.
     */
    public void replaceSchedules(List<Slot> slots) {
        this.schedules.removeIf(existing -> slots.stream()
            .noneMatch(slot -> slot.dayOfWeek().equals(existing.getDayOfWeek())));

        for (Slot slot : slots) {
            this.schedules.stream()
                .filter(existing -> existing.getDayOfWeek().equals(slot.dayOfWeek()))
                .findFirst()
                .ifPresentOrElse(
                    existing -> existing.changeTime(slot.startTime(), slot.endTime()),
                    () -> this.schedules.add(ClassRoomSchedule.create(
                        this, slot.dayOfWeek(), slot.startTime(), slot.endTime())));
        }
    }

    /** 서비스 계층이 슬롯을 넘길 때 쓰는 값 객체. DTO를 엔티티로 들이지 않기 위한 것이다. */
    public record Slot(Short dayOfWeek, LocalTime startTime, LocalTime endTime) {
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
