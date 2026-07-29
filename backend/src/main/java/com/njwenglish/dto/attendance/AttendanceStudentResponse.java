package com.njwenglish.dto.attendance;

import com.njwenglish.entity.enums.AttendanceStatus;

/**
 * 출석부 한 줄. 이름은 students.name이다 — users.name을 쓰면 미가입 학생이 사라진다.
 */
public record AttendanceStudentResponse(Long studentId,
                                        String name,
                                        AttendanceStatus status,
                                        String memo) {

    /** 확정 전 기본값은 전원 출석이다. 선생님은 안 온 학생만 지정한다. */
    public static AttendanceStudentResponse present(Long studentId, String name) {
        return new AttendanceStudentResponse(studentId, name, AttendanceStatus.PRESENT, null);
    }
}
