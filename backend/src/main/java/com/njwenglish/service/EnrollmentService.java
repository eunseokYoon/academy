package com.njwenglish.service;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.security.StudentAccessGuard;
import com.njwenglish.dto.classroom.ClassRoomStudentsResponse;
import com.njwenglish.dto.classroom.EnrollmentCreateRequest;
import com.njwenglish.entity.ClassRoom;
import com.njwenglish.entity.Enrollment;
import com.njwenglish.entity.Student;
import com.njwenglish.repository.ClassRoomRepository;
import com.njwenglish.repository.EnrollmentRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 반 배정. 학생을 어느 반에 넣을지에 제약이 없다 — 반에 학교·학년이 없어 비교할 값 자체가 없고,
 * 한 학생이 여러 반에 속할 수도 있다.
 *
 * <p>배정 해제는 행을 지우지 않고 left_at을 기록한다. 과거 수업·출석이 이 행에 이어져 있다.
 */
@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final ClassRoomRepository classRoomRepository;
    private final StudentAccessGuard studentAccessGuard;

    /** asOf를 주면 그 날짜 기준 명단이다. 과거 출석 확정 화면에서 필요하다. */
    @Transactional(readOnly = true)
    public ClassRoomStudentsResponse students(Long classRoomId, LocalDate asOf) {
        ClassRoom classRoom = findClassRoom(classRoomId);
        LocalDate targetDate = asOf == null ? LocalDate.now() : asOf;

        List<ClassRoomStudentsResponse.Member> members =
            enrollmentRepository.findActiveEnrollments(classRoomId, targetDate).stream()
                .map(e -> new ClassRoomStudentsResponse.Member(
                    e.getStudent().getId(), e.getStudent().getName(), e.getJoinedAt(),
                    e.getStudent().getUser() != null))
                .toList();

        return new ClassRoomStudentsResponse(
            new ClassRoomStudentsResponse.ClassRoomSummary(classRoom.getId(), classRoom.getName()),
            members);
    }

    /** 이미 배정된 학생은 건너뛴다. 409를 던지지 않고 멱등하게 처리한다. */
    @Transactional
    public ClassRoomStudentsResponse assign(Long classRoomId, EnrollmentCreateRequest request) {
        ClassRoom classRoom = findClassRoom(classRoomId);
        LocalDate joinedAt = request.joinedAt() == null ? LocalDate.now() : request.joinedAt();

        request.studentIds().stream().distinct().forEach(studentId -> {
            Student student = studentAccessGuard.requireAccessible(studentId);
            boolean alreadyAssigned = enrollmentRepository
                .findByStudentIdAndClassRoomIdAndLeftAtIsNull(studentId, classRoomId)
                .isPresent();
            if (!alreadyAssigned) {
                enrollmentRepository.save(Enrollment.create(student, classRoom, joinedAt));
            }
        });

        return students(classRoomId, null);
    }

    @Transactional
    public void unassign(Long classRoomId, Long studentId) {
        studentAccessGuard.requireAccessible(studentId);
        Enrollment enrollment = enrollmentRepository
            .findByStudentIdAndClassRoomIdAndLeftAtIsNull(studentId, classRoomId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        enrollment.leave(LocalDate.now());
    }

    private ClassRoom findClassRoom(Long classRoomId) {
        return classRoomRepository.findById(classRoomId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
