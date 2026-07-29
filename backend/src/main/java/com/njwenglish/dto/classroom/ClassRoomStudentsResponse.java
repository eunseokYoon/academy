package com.njwenglish.dto.classroom;

import java.time.LocalDate;
import java.util.List;

/**
 * 명단은 findActiveStudents 기준이고 정렬은 students.name이다.
 * 미가입 학생(user_id IS NULL)도 이름과 함께 나와야 한다.
 */
public record ClassRoomStudentsResponse(ClassRoomSummary classRoom, List<Member> students) {

    public record ClassRoomSummary(Long id, String name) {
    }

    public record Member(Long studentId, String name, LocalDate joinedAt, boolean signedUp) {
    }
}
