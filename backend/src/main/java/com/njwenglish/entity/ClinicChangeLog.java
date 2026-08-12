package com.njwenglish.entity;

import com.njwenglish.common.entity.BaseCreatedEntity;
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
 * 학생이 클리닉을 바꾼 기록. <b>요청이 아니라 이미 일어난 일이다</b> —
 * 선생님 승인 단계는 2026-08-10에 없앴다(강사가 1명이라 승인이 병목이었다).
 *
 * <p>승인이 없으니 <b>이 기록이 유일한 대응책이다.</b> 예약을 바꾸는 경로는
 * 반드시 여기에 한 줄을 남겨야 한다. 남기지 않으면 선생님은 명단이 왜 바뀌었는지 알 수 없다.
 *
 * <p>from을 함께 저장하는 이유: 예약 행은 이미 새 값으로 바뀐 뒤라 원래 시각을 복원할 수 없다.
 * 선생님이 봐야 하는 건 "누가 언제서 언제로 옮겼는가"다.
 */
@Entity
@Table(name = "clinic_change_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClinicChangeLog extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_clinic_id", nullable = false)
    private Clinic fromClinic;

    @Column(name = "from_arrival_time", nullable = false)
    private LocalTime fromArrivalTime;

    /** null이면 취소다. toArrivalTime과 항상 짝이다(ck_clinic_change_logs_target). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_clinic_id")
    private Clinic toClinic;

    @Column(name = "to_arrival_time")
    private LocalTime toArrivalTime;

    /**
     * 학생이 직접 적는다. <b>코드가 아니라 자유 텍스트다</b> — 선택지 목록이 미확정인 채로
     * 남아 있던 reason_code를 없애고 이걸로 대체했다. 다시 코드 컬럼을 만들지 마라.
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    /** 시간 변경과 클리닉 이동 모두 이 경로다 — 둘의 차이는 toClinic이 같은 클리닉인지뿐이다. */
    public static ClinicChangeLog moved(Student student, Clinic fromClinic,
                                        LocalTime fromArrivalTime, Clinic toClinic,
                                        LocalTime toArrivalTime, String reason) {
        ClinicChangeLog log = new ClinicChangeLog();
        log.student = student;
        log.fromClinic = fromClinic;
        log.fromArrivalTime = fromArrivalTime;
        log.toClinic = toClinic;
        log.toArrivalTime = toArrivalTime;
        log.reason = reason;
        return log;
    }

    /**
     * to*가 비어 있는 행. <b>지금은 만들어지지 않는다</b> — 학생 취소를 없앴다(2026-08-10 확정).
     * 컬럼이 nullable로 남아 있어(V13) 읽는 쪽에서 방어만 한다. 취소를 되살릴 게 아니라면
     * 이 값이 true인 행은 생기지 않는다.
     */
    public boolean isCancel() {
        return toClinic == null;
    }
}
