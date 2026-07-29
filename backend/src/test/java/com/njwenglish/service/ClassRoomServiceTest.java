package com.njwenglish.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.security.AuthUser;
import com.njwenglish.common.util.InviteCodeIssuer;
import com.njwenglish.dto.classroom.ClassRoomCreateRequest;
import com.njwenglish.dto.classroom.ClassRoomCreateResponse;
import com.njwenglish.dto.classroom.ClassRoomUpdateRequest;
import com.njwenglish.dto.classroom.JoinCodeRequest;
import com.njwenglish.dto.classroom.JoinCodeResponse;
import com.njwenglish.entity.ClassRoom;
import com.njwenglish.entity.Teacher;
import com.njwenglish.entity.enums.ClassRoomStatus;
import com.njwenglish.entity.enums.UserRole;
import com.njwenglish.repository.ClassRoomRepository;
import com.njwenglish.repository.EnrollmentRepository;
import com.njwenglish.repository.TeacherRepository;
import com.njwenglish.support.Fixtures;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ClassRoomServiceTest {

    @Mock
    private ClassRoomRepository classRoomRepository;
    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private TeacherRepository teacherRepository;
    @Mock
    private InviteCodeIssuer inviteCodeIssuer;

    private ClassRoomService classRoomService;

    @BeforeEach
    void setUp() {
        classRoomService = new ClassRoomService(classRoomRepository, enrollmentRepository,
            teacherRepository, inviteCodeIssuer);
        Fixtures.login(Fixtures.teacher(1L));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void teacherFound() {
        Teacher teacher = BeanUtils.instantiateClass(Teacher.class);
        ReflectionTestUtils.setField(teacher, "id", 1L);
        given(teacherRepository.findByUserId(1L)).willReturn(Optional.of(teacher));
    }

    @Test
    @DisplayName("이름 하나로 만들 수 있고 코드는 서버가 발급한다")
    void 이름만으로_만든다() {
        teacherFound();
        given(inviteCodeIssuer.issue()).willReturn("HK7F2Q");
        given(classRoomRepository.save(any())).willAnswer(i -> i.getArgument(0));

        ClassRoomCreateResponse response = classRoomService.create(
            new ClassRoomCreateRequest("고2 심화반", null, null, null, null, null));

        assertThat(response.name()).isEqualTo("고2 심화반");
        assertThat(response.joinCode()).isEqualTo("HK7F2Q");
        assertThat(response.joinCodeActive()).isTrue();
    }

    @Test
    @DisplayName("활성 반끼리 이름이 겹치면 409다")
    void 이름_중복은_거부한다() {
        given(classRoomRepository.existsByNameAndStatus("고2 심화반", ClassRoomStatus.ACTIVE))
            .willReturn(true);

        assertThatThrownBy(() -> classRoomService.create(
            new ClassRoomCreateRequest("고2 심화반", null, null, null, null, null)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_RESOURCE);
    }

    @Test
    @DisplayName("요일은 1~7만 받는다")
    void 요일_범위를_검사한다() {
        assertThatThrownBy(() -> classRoomService.create(
            new ClassRoomCreateRequest("고2 심화반", (short) 8, null, null, null, null)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("수업·배정 이력이 있으면 삭제하지 않고 409다")
    void 기록이_있으면_삭제를_거부한다() {
        ClassRoom classRoom = Fixtures.openClassRoom(3L, "고2 심화반", "HK7F2Q");
        given(classRoomRepository.findById(3L)).willReturn(Optional.of(classRoom));
        given(classRoomRepository.hasRecords(3L)).willReturn(true);

        assertThatThrownBy(() -> classRoomService.delete(3L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.CLASS_ROOM_HAS_RECORDS);

        verify(classRoomRepository, never()).delete(any());
    }

    @Test
    @DisplayName("기록이 없는 반은 삭제된다")
    void 기록이_없으면_삭제한다() {
        ClassRoom classRoom = Fixtures.openClassRoom(3L, "잘못 만든 반", "HK7F2Q");
        given(classRoomRepository.findById(3L)).willReturn(Optional.of(classRoom));
        given(classRoomRepository.hasRecords(3L)).willReturn(false);

        classRoomService.delete(3L);

        verify(classRoomRepository).delete(classRoom);
    }

    @Test
    @DisplayName("종료 처리하면 상태가 CLOSED가 되고 가입 코드도 닫힌다")
    void 종료하면_코드도_닫는다() {
        ClassRoom classRoom = Fixtures.openClassRoom(3L, "고2 심화반", "HK7F2Q");
        given(classRoomRepository.findById(3L)).willReturn(Optional.of(classRoom));

        classRoomService.close(3L);

        assertThat(classRoom.getStatus()).isEqualTo(ClassRoomStatus.CLOSED);
        assertThat(classRoom.isJoinCodeActive()).isFalse();
    }

    @Test
    @DisplayName("active만 보내면 코드는 그대로 두고 닫는다")
    void 코드를_닫는다() {
        ClassRoom classRoom = Fixtures.openClassRoom(3L, "고2 심화반", "OLD123");
        given(classRoomRepository.findById(3L)).willReturn(Optional.of(classRoom));

        JoinCodeResponse response = classRoomService.changeJoinCode(3L,
            new JoinCodeRequest(null, false));

        assertThat(response.joinCode()).isEqualTo("OLD123");
        assertThat(response.joinCodeActive()).isFalse();
    }

    @Test
    @DisplayName("regenerate면 코드가 새로 발급되고 이전 코드는 즉시 무효다")
    void 코드를_재발급한다() {
        ClassRoom classRoom = Fixtures.openClassRoom(3L, "고2 심화반", "OLD123");
        given(classRoomRepository.findById(3L)).willReturn(Optional.of(classRoom));
        given(inviteCodeIssuer.issue()).willReturn("HK7F2Q");

        JoinCodeResponse response = classRoomService.changeJoinCode(3L,
            new JoinCodeRequest(true, true));

        assertThat(response.joinCode()).isEqualTo("HK7F2Q");
        assertThat(response.joinCodeActive()).isTrue();
    }

    @Test
    @DisplayName("PATCH는 보낸 필드만 바꾼다")
    void 보낸_필드만_바꾼다() {
        ClassRoom classRoom = ClassRoom.create(null, "고2 심화반", "HK7F2Q", (short) 3,
            LocalTime.of(19, 0), LocalDate.of(2026, 3, 2), LocalDate.of(2027, 2, 28), "메모");
        ReflectionTestUtils.setField(classRoom, "id", 3L);
        given(classRoomRepository.findById(3L)).willReturn(Optional.of(classRoom));

        classRoomService.update(3L, new ClassRoomUpdateRequest(
            "고2 심화반 (수)", null, null, null, null, null));

        assertThat(classRoom.getName()).isEqualTo("고2 심화반 (수)");
        assertThat(classRoom.getDayOfWeek()).isEqualTo((short) 3);
        assertThat(classRoom.getStartTime()).isEqualTo(LocalTime.of(19, 0));
        assertThat(classRoom.getTermEnd()).isEqualTo(LocalDate.of(2027, 2, 28));
        assertThat(classRoom.getMemo()).isEqualTo("메모");
    }

    @Test
    @DisplayName("같은 이름으로 수정하면 중복 검사에 걸리지 않는다")
    void 자기_이름은_중복이_아니다() {
        ClassRoom classRoom = Fixtures.openClassRoom(3L, "고2 심화반", "HK7F2Q");
        given(classRoomRepository.findById(3L)).willReturn(Optional.of(classRoom));

        classRoomService.update(3L, new ClassRoomUpdateRequest(
            "고2 심화반", null, null, null, null, null));

        verify(classRoomRepository, never()).existsByNameAndStatus(any(), any());
    }

    @Test
    @DisplayName("로그인한 선생님이 teachers에 없으면 404다")
    void 선생님이_없으면_404다() {
        Fixtures.login(new AuthUser(99L, UserRole.TEACHER, false));
        given(teacherRepository.findByUserId(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> classRoomService.create(
            new ClassRoomCreateRequest("새 반", null, null, null, null, null)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }
}
