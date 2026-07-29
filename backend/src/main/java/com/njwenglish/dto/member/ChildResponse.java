package com.njwenglish.dto.member;

import com.njwenglish.entity.Student;

/** 이름은 students.name이다. 미가입 자녀도 목록에 나와야 한다. */
public record ChildResponse(Long studentId, String name) {

    public static ChildResponse from(Student student) {
        return new ChildResponse(student.getId(), student.getName());
    }
}
