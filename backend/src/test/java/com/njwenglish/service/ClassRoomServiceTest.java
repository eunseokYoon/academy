package com.njwenglish.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
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
import com.njwenglish.dto.classroom.ClassRoomResponse;
import com.njwenglish.dto.classroom.ClassRoomScheduleDto;
import com.njwenglish.dto.classroom.ClassRoomUpdateRequest;
import com.njwenglish.dto.classroom.JoinCodeRequest;
import com.njwenglish.dto.classroom.JoinCodeResponse;
import com.njwenglish.entity.ClassRoom;
import com.njwenglish.entity.ClassRoomSchedule;
import com.njwenglish.entity.Teacher;
import com.njwenglish.entity.enums.ClassRoomStatus;
import com.njwenglish.entity.enums.UserRole;
import com.njwenglish.repository.ClassRoomRepository;
import com.njwenglish.repository.EnrollmentRepository;
import com.njwenglish.repository.TeacherRepository;
import com.njwenglish.support.Fixtures;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
            new ClassRoomCreateRequest("고2 심화반", null, null));

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
            new ClassRoomCreateRequest("고2 심화반", null, null)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_RESOURCE);
    }

    @Test
    @DisplayName("요일은 1~7만 받는다")
    void 요일_범위를_검사한다() {
        assertThatThrownBy(() -> classRoomService.create(
            new ClassRoomCreateRequest("고2 심화반", List.of(
                new ClassRoomScheduleDto((short) 8, LocalTime.of(19, 0), null)), null)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("슬롯 여러 개를 저장한다")
    void 슬롯_여러개를_저장한다() {
        teacherFound();
        given(inviteCodeIssuer.issue()).willReturn("HK7F2Q");
        given(classRoomRepository.save(any())).willAnswer(i -> i.getArgument(0));

        classRoomService.create(new ClassRoomCreateRequest("화목반", List.of(
            new ClassRoomScheduleDto((short) 4, LocalTime.of(19, 0), LocalTime.of(21, 0)),
            new ClassRoomScheduleDto((short) 2, LocalTime.of(19, 0), null)), null));

        ArgumentCaptor<ClassRoom> captor = ArgumentCaptor.forClass(ClassRoom.class);
        verify(classRoomRepository).save(captor.capture());

        assertThat(captor.getValue().getSchedules())
            .extracting(ClassRoomSchedule::getDayOfWeek, ClassRoomSchedule::getEndTime)
            .containsExactlyInAnyOrder(
                tuple((short) 4, LocalTime.of(21, 0)),
                tuple((short) 2, null));
    }

    @Test
    @DisplayName("응답의 스케줄은 요일 오름차순이다 — 보낸 순서와 무관하다")
    void 응답은_요일순으로_내린다() {
        ClassRoom classRoom = Fixtures.openClassRoom(3L, "화목반", "HK7F2Q");
        given(classRoomRepository.findById(3L)).willReturn(Optional.of(classRoom));

        ClassRoomResponse response = classRoomService.update(3L, new ClassRoomUpdateRequest(
            null, List.of(
                new ClassRoomScheduleDto((short) 4, LocalTime.of(19, 0), null),
                new ClassRoomScheduleDto((short) 2, LocalTime.of(19, 0), null)), null));

        assertThat(response.schedules()).extracting(ClassRoomScheduleDto::dayOfWeek)
            .containsExactly((short) 2, (short) 4);
    }

    @Test
    @DisplayName("남는 요일은 행을 새로 만들지 않고 시각만 고친다 — 지우고 다시 넣으면 uq_crs_day에 걸린다")
    void 남는_요일은_행을_유지한다() {
        ClassRoom classRoom = Fixtures.openClassRoom(3L, "목요일반", "HK7F2Q");
        classRoom.replaceSchedules(List.of(
            new ClassRoom.Slot((short) 4, LocalTime.of(19, 0), null)));
        ClassRoomSchedule before = classRoom.getSchedules().get(0);

        classRoom.replaceSchedules(List.of(
            new ClassRoom.Slot((short) 4, LocalTime.of(20, 0), LocalTime.of(22, 0)),
            new ClassRoom.Slot((short) 2, LocalTime.of(19, 0), null)));

        assertThat(classRoom.getSchedules()).hasSize(2);
        // 목요일 행이 그대로여야 한다. 새 객체면 DELETE+INSERT가 되어 제약에 걸린다
        assertThat(classRoom.getSchedules()).contains(before);
        assertThat(before.getStartTime()).isEqualTo(LocalTime.of(20, 0));
        assertThat(before.getEndTime()).isEqualTo(LocalTime.of(22, 0));
    }

    @Test
    @DisplayName("같은 요일 슬롯이 두 개면 400이다 — lessons가 날짜당 1행이라 하나가 조용히 사라진다")
    void 같은_요일_중복은_거부한다() {
        assertThatThrownBy(() -> classRoomService.create(
            new ClassRoomCreateRequest("토요반", List.of(
                new ClassRoomScheduleDto((short) 6, LocalTime.of(10, 0), null),
                new ClassRoomScheduleDto((short) 6, LocalTime.of(14, 0), null)), null)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_FAILED);

        verify(classRoomRepository, never()).save(any());
    }

    @Test
    @DisplayName("종료시각이 시작시각보다 이르거나 같으면 400이다")
    void 종료가_시작보다_이르면_거부한다() {
        assertThatThrownBy(() -> classRoomService.create(
            new ClassRoomCreateRequest("이상한반", List.of(
                new ClassRoomScheduleDto((short) 2, LocalTime.of(19, 0), LocalTime.of(19, 0))),
                null)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_FAILED);

        verify(classRoomRepository, never()).save(any());
    }

    @Test
    @DisplayName("update에 빈 배열을 보내면 스케줄을 전부 지운다")
    void 수정시_빈배열이면_전부_지운다() {
        ClassRoom classRoom = Fixtures.openClassRoom(3L, "고2 심화반", "HK7F2Q");
        classRoom.replaceSchedules(List.of(
            new ClassRoom.Slot((short) 4, LocalTime.of(19, 0), null)));
        given(classRoomRepository.findById(3L)).willReturn(Optional.of(classRoom));

        classRoomService.update(3L, new ClassRoomUpdateRequest(null, List.of(), null));

        assertThat(classRoom.getSchedules()).isEmpty();
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
        ClassRoom classRoom = ClassRoom.create(null, "고2 심화반", "HK7F2Q", "메모");
        classRoom.replaceSchedules(List.of(
            new ClassRoom.Slot((short) 3, LocalTime.of(19, 0), LocalTime.of(21, 0))));
        ReflectionTestUtils.setField(classRoom, "id", 3L);
        given(classRoomRepository.findById(3L)).willReturn(Optional.of(classRoom));

        classRoomService.update(3L, new ClassRoomUpdateRequest("고2 심화반 (수)", null, null));

        assertThat(classRoom.getName()).isEqualTo("고2 심화반 (수)");
        // schedules를 안 보냈으니 그대로 남아 있어야 한다
        assertThat(classRoom.getSchedules()).hasSize(1);
        assertThat(classRoom.getSchedules().get(0).getDayOfWeek()).isEqualTo((short) 3);
        assertThat(classRoom.getSchedules().get(0).getStartTime()).isEqualTo(LocalTime.of(19, 0));
        assertThat(classRoom.getMemo()).isEqualTo("메모");
    }

    @Test
    @DisplayName("같은 이름으로 수정하면 중복 검사에 걸리지 않는다")
    void 자기_이름은_중복이_아니다() {
        ClassRoom classRoom = Fixtures.openClassRoom(3L, "고2 심화반", "HK7F2Q");
        given(classRoomRepository.findById(3L)).willReturn(Optional.of(classRoom));

        classRoomService.update(3L, new ClassRoomUpdateRequest("고2 심화반", null, null));

        verify(classRoomRepository, never()).existsByNameAndStatus(any(), any());
    }

    @Test
    @DisplayName("로그인한 선생님이 teachers에 없으면 404다")
    void 선생님이_없으면_404다() {
        Fixtures.login(new AuthUser(99L, UserRole.TEACHER, false));
        given(teacherRepository.findByUserId(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> classRoomService.create(
            new ClassRoomCreateRequest("새 반", null, null)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }
}
