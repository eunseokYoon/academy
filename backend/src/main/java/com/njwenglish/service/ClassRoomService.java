package com.njwenglish.service;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.security.CurrentUser;
import com.njwenglish.common.util.InviteCodeIssuer;
import com.njwenglish.dto.classroom.ClassRoomCreateRequest;
import com.njwenglish.dto.classroom.ClassRoomCreateResponse;
import com.njwenglish.dto.classroom.ClassRoomResponse;
import com.njwenglish.dto.classroom.ClassRoomScheduleDto;
import com.njwenglish.dto.classroom.ClassRoomUpdateRequest;
import com.njwenglish.dto.classroom.JoinCodeRequest;
import com.njwenglish.dto.classroom.JoinCodeResponse;
import com.njwenglish.entity.ClassRoom;
import com.njwenglish.entity.Teacher;
import com.njwenglish.entity.enums.ClassRoomStatus;
import com.njwenglish.repository.ClassRoomRepository;
import com.njwenglish.repository.EnrollmentRepository;
import com.njwenglish.repository.TeacherRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * T-3 반 관리. 반은 학생을 묶는 유일한 단위다 — 학교·학년은 시스템에 없다.
 *
 * <p>joinCodeActive가 반 코드 가입의 유일한 방어선이다. 반 코드는 20명이 나눠 쓰는 값이라
 * 전화번호로 본인을 묶을 수 없고, 열려 있는 동안은 코드를 아는 누구나 가입한다.
 */
@Service
@RequiredArgsConstructor
public class ClassRoomService {

    private final ClassRoomRepository classRoomRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final TeacherRepository teacherRepository;
    private final InviteCodeIssuer inviteCodeIssuer;

    @Transactional(readOnly = true)
    public List<ClassRoomResponse> list(ClassRoomStatus status) {
        List<ClassRoom> classRooms = status == null
            ? classRoomRepository.findAllByOrderByStatusAscNameAsc()
            : classRoomRepository.findByStatusOrderByNameAsc(status);

        return classRooms.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ClassRoomResponse detail(Long classRoomId) {
        return toResponse(findClassRoom(classRoomId));
    }

    @Transactional
    public ClassRoomCreateResponse create(ClassRoomCreateRequest request) {
        // 입력 검증을 먼저 끝낸다. 뒤에 두면 조회 실패가 먼저 터져 원인이 가려진다
        String name = requireName(request.name());
        List<ClassRoom.Slot> slots = validSlots(request.schedules());
        requireUniqueActiveName(name);

        ClassRoom classRoom = ClassRoom.create(
            currentTeacher(), name, inviteCodeIssuer.issue(), request.memo());
        classRoom.replaceSchedules(slots == null ? List.of() : slots);

        return ClassRoomCreateResponse.from(classRoomRepository.save(classRoom));
    }

    @Transactional
    public ClassRoomResponse update(Long classRoomId, ClassRoomUpdateRequest request) {
        ClassRoom classRoom = findClassRoom(classRoomId);

        if (request.name() != null) {
            String name = requireName(request.name());
            if (!name.equals(classRoom.getName())) {
                requireUniqueActiveName(name);
                classRoom.rename(name);
            }
        }
        // null은 "건드리지 마라", 빈 배열은 "전부 지워라". 둘을 구분해야 한다
        if (request.schedules() != null) {
            classRoom.replaceSchedules(validSlots(request.schedules()));
        }
        if (request.memo() != null) {
            classRoom.changeMemo(request.memo().isBlank() ? null : request.memo());
        }
        return toResponse(classRoom);
    }

    /**
     * 잘못 만든 반을 지우는 용도다. 수업·배정 이력·숙제가 하나라도 있으면 409이고
     * 그때는 close가 맞다. 지워버리면 학생의 과거 기록이 함께 사라진다.
     */
    @Transactional
    public void delete(Long classRoomId) {
        ClassRoom classRoom = findClassRoom(classRoomId);
        if (classRoomRepository.hasRecords(classRoomId)) {
            throw new BusinessException(ErrorCode.CLASS_ROOM_HAS_RECORDS);
        }
        classRoomRepository.delete(classRoom);
    }

    /** 학기 종료. 기록은 그대로 남고 코드도 함께 닫힌다. */
    @Transactional
    public ClassRoomResponse close(Long classRoomId) {
        ClassRoom classRoom = findClassRoom(classRoomId);
        classRoom.close();
        return toResponse(classRoom);
    }

    @Transactional
    public JoinCodeResponse changeJoinCode(Long classRoomId, JoinCodeRequest request) {
        ClassRoom classRoom = findClassRoom(classRoomId);

        if (Boolean.TRUE.equals(request.regenerate())) {
            classRoom.regenerateJoinCode(inviteCodeIssuer.issue());
        }
        if (request.active() != null) {
            classRoom.changeJoinCodeActive(request.active());
        }
        return JoinCodeResponse.from(classRoom);
    }

    // ---------- 내부 ----------

    private ClassRoom findClassRoom(Long classRoomId) {
        return classRoomRepository.findById(classRoomId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private ClassRoomResponse toResponse(ClassRoom classRoom) {
        return ClassRoomResponse.of(classRoom,
            enrollmentRepository.countActiveStudents(classRoom.getId()));
    }

    private Teacher currentTeacher() {
        return teacherRepository.findByUserId(CurrentUser.get().userId())
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /** 종료된 반의 이름은 다시 쓸 수 있다(uq_class_rooms_name이 ACTIVE 부분 인덱스). */
    private void requireUniqueActiveName(String name) {
        if (classRoomRepository.existsByNameAndStatus(name, ClassRoomStatus.ACTIVE)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }
    }

    /** 1=월 ~ 7=일. DB CHECK 제약과 같은 범위를 애플리케이션에서도 막는다. */
    private Short validDayOfWeek(Short dayOfWeek) {
        if (dayOfWeek != null && (dayOfWeek < 1 || dayOfWeek > 7)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        return dayOfWeek;
    }

    /**
     * null이면 null을 돌려준다 — 호출부가 "안 건드림"과 "비움"을 구분해야 한다.
     *
     * <p>요일 중복을 DB uq_crs_day에 닿기 전에 잡는다. 제약 위반으로 터지면
     * 500이나 모호한 409가 나간다.
     */
    private List<ClassRoom.Slot> validSlots(List<ClassRoomScheduleDto> schedules) {
        if (schedules == null) {
            return null;
        }
        Set<Short> seenDays = new HashSet<>();
        List<ClassRoom.Slot> slots = new ArrayList<>();
        for (ClassRoomScheduleDto dto : schedules) {
            Short day = validDayOfWeek(dto.dayOfWeek());
            if (day == null || dto.startTime() == null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED);
            }
            if (dto.endTime() != null && !dto.endTime().isAfter(dto.startTime())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED);
            }
            if (!seenDays.add(day)) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED);
            }
            slots.add(new ClassRoom.Slot(day, dto.startTime(), dto.endTime()));
        }
        // 정렬하지 않는다. 응답 순서는 ClassRoomResponse.of가 책임진다 — 두 곳에서
        // 정렬하면 한쪽만 고쳤을 때 화면마다 순서가 갈린다
        return slots;
    }

    private String requireName(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        return raw.trim();
    }
}
