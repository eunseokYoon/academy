package com.njwenglish.support;

import com.njwenglish.common.security.AuthUser;
import com.njwenglish.entity.ClassRoom;
import com.njwenglish.entity.Clinic;
import com.njwenglish.entity.ClinicReservation;
import com.njwenglish.entity.Lesson;
import com.njwenglish.entity.Parent;
import com.njwenglish.entity.Student;
import com.njwenglish.entity.Teacher;
import com.njwenglish.entity.User;
import com.njwenglish.entity.enums.ClassRoomStatus;
import com.njwenglish.entity.enums.UserRole;
import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.beans.BeanUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

/** 엔티티에 setter가 없어서 id는 리플렉션으로 채운다. 테스트 전용이다. */
public final class Fixtures {

    private Fixtures() {
    }

    public static User user(Long id, UserRole role, String phone) {
        return user(id, role, phone, "$2a$10$hash");
    }

    public static User user(Long id, UserRole role, String phone, String passwordHash) {
        User user = User.create(role, phone, "이름" + id, passwordHash);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    public static Student student(Long id, String name) {
        Student student = Student.create(name);
        ReflectionTestUtils.setField(student, "id", id);
        return student;
    }

    /**
     * ClassRoom은 Phase 3에서 생성 팩토리가 생긴다. 그 전까지는 테스트에서 리플렉션으로 채운다.
     */
    public static ClassRoom classRoom(Long id, String name, String joinCode,
                                      boolean joinCodeActive, ClassRoomStatus status) {
        ClassRoom classRoom = BeanUtils.instantiateClass(ClassRoom.class);
        ReflectionTestUtils.setField(classRoom, "id", id);
        ReflectionTestUtils.setField(classRoom, "name", name);
        ReflectionTestUtils.setField(classRoom, "joinCode", joinCode);
        ReflectionTestUtils.setField(classRoom, "joinCodeActive", joinCodeActive);
        ReflectionTestUtils.setField(classRoom, "status", status);
        return classRoom;
    }

    /** 열려 있는 정상 반. */
    public static ClassRoom openClassRoom(Long id, String name, String joinCode) {
        return classRoom(id, name, joinCode, true, ClassRoomStatus.ACTIVE);
    }

    /** Teacher에는 생성 팩토리가 없다(시드로만 생긴다). 테스트에서만 리플렉션으로 만든다. */
    public static Teacher teacherEntity(Long id) {
        Teacher teacher = BeanUtils.instantiateClass(Teacher.class);
        ReflectionTestUtils.setField(teacher, "id", id);
        return teacher;
    }

    public static Lesson lesson(Long id, ClassRoom classRoom, LocalDate lessonDate) {
        Lesson lesson = Lesson.create(classRoom, lessonDate,
            (short) lessonDate.getYear(), (short) lessonDate.getMonthValue(), (short) 1);
        ReflectionTestUtils.setField(lesson, "id", id);
        return lesson;
    }

    public static Clinic clinic(Long id, LocalDate date, LocalTime startTime, Short capacity) {
        Clinic clinic = Clinic.open(teacherEntity(1L), date, startTime,
            startTime.plusHours(1), capacity, null);
        ReflectionTestUtils.setField(clinic, "id", id);
        return clinic;
    }

    public static ClinicReservation reservation(Long id, Clinic clinic, Student student,
                                                Teacher assignedBy) {
        ClinicReservation reservation = ClinicReservation.reserve(clinic, student, assignedBy);
        ReflectionTestUtils.setField(reservation, "id", id);
        return reservation;
    }

    public static Parent parent(Long id, User user) {
        Parent parent = Parent.create(user);
        ReflectionTestUtils.setField(parent, "id", id);
        return parent;
    }

    /** SecurityContext에 로그인 사용자를 심는다. 테스트 뒤에는 반드시 clearContext(). */
    public static void login(AuthUser authUser) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(authUser, null, java.util.List.of()));
    }

    public static AuthUser teacher(Long userId) {
        return new AuthUser(userId, UserRole.TEACHER, false);
    }

    public static AuthUser studentUser(Long userId) {
        return new AuthUser(userId, UserRole.STUDENT, false);
    }

    public static AuthUser parentUser(Long userId) {
        return new AuthUser(userId, UserRole.PARENT, false);
    }
}
