package com.njwenglish.service;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.security.CurrentUser;
import com.njwenglish.common.security.StudentAccessGuard;
import com.njwenglish.dto.clinic.ClinicChangeRequestCreateRequest;
import com.njwenglish.dto.clinic.ClinicChangeRequestCreateResponse;
import com.njwenglish.dto.clinic.ClinicChangeRequestResponse;
import com.njwenglish.dto.clinic.ClinicDecideRequest;
import com.njwenglish.entity.Clinic;
import com.njwenglish.entity.ClinicChangeRequest;
import com.njwenglish.entity.ClinicReservation;
import com.njwenglish.entity.Student;
import com.njwenglish.entity.Teacher;
import com.njwenglish.entity.enums.ChangeRequestStatus;
import com.njwenglish.repository.ClinicChangeRequestRepository;
import com.njwenglish.repository.ClinicRepository;
import com.njwenglish.repository.ClinicReservationRepository;
import com.njwenglish.repository.TeacherRepository;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 클리닉 시간 변경 요청. <b>학생이 직접 시간을 옮기지 못한다.</b>
 * 요청하고 선생님이 승인한다.
 *
 * <p>reason_code는 필수지만 <b>옵션 목록이 미확정</b>이라 문자열로 받아 저장만 한다.
 * enum도 CHECK 제약도 만들지 말고 값을 지어내지도 마라.
 */
@Service
@RequiredArgsConstructor
public class ClinicChangeRequestService {

    private final ClinicChangeRequestRepository changeRequestRepository;
    private final ClinicReservationRepository reservationRepository;
    private final ClinicRepository clinicRepository;
    private final TeacherRepository teacherRepository;
    private final StudentAccessGuard studentAccessGuard;
    private final ClinicReservationService clinicReservationService;

    /** S-9. targetClinicId가 null이면 취소 요청이다. */
    @Transactional
    public ClinicChangeRequestCreateResponse create(ClinicChangeRequestCreateRequest request) {
        Student student = studentAccessGuard.requireSelf();

        ClinicReservation reservation = reservationRepository.findById(request.reservationId())
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        // 남의 예약에 변경 요청을 걸 수 없다
        if (!reservation.getStudent().getId().equals(student.getId())) {
            throw new BusinessException(ErrorCode.STUDENT_NOT_ACCESSIBLE);
        }
        if (!reservation.isReserved()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        // 같은 예약에 PENDING이 둘이면 둘 다 승인됐을 때 예약이 꼬인다
        if (changeRequestRepository.existsByReservationIdAndStatus(
            reservation.getId(), ChangeRequestStatus.PENDING)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }

        Clinic targetClinic = null;
        if (request.targetClinicId() != null) {
            if (request.targetClinicId().equals(reservation.getClinic().getId())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED);
            }
            targetClinic = clinicRepository.findById(request.targetClinicId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        }

        ClinicChangeRequest saved = changeRequestRepository.save(ClinicChangeRequest.create(
            reservation, student, targetClinic,
            request.reasonCode().trim(), request.reasonNote()));
        return ClinicChangeRequestCreateResponse.from(saved);
    }

    /** T-13 대기 목록. 페이징 없음. */
    @Transactional(readOnly = true)
    public List<ClinicChangeRequestResponse> list(ChangeRequestStatus status) {
        return changeRequestRepository.findAllWithDetail(status).stream()
            .map(ClinicChangeRequestResponse::from)
            .toList();
    }

    /**
     * 승인·거절. 승인은 한 트랜잭션이다 —
     * 기존 예약을 MOVED로 바꾸고 목표 클리닉에 새 RESERVED 행을 만든다.
     *
     * <p><b>목표 클리닉 정원을 승인 시점에 다시 확인한다.</b> 요청할 때는 자리가 있었어도
     * 승인할 때는 찼을 수 있다. 초과면 409를 던지고 요청은 PENDING으로 남는다
     * (예외가 트랜잭션을 되돌리므로 status 변경도 함께 취소된다).
     */
    @Transactional
    public ClinicChangeRequestResponse decide(Long requestId, ClinicDecideRequest request) {
        ClinicChangeRequest changeRequest = changeRequestRepository.findWithDetail(requestId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!changeRequest.isPending()) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }

        Teacher teacher = currentTeacher();
        if (Boolean.TRUE.equals(request.approve())) {
            approve(changeRequest);
        }
        changeRequest.decide(Boolean.TRUE.equals(request.approve()), teacher,
            OffsetDateTime.now());
        return ClinicChangeRequestResponse.from(changeRequest);
    }

    // ---------- 내부 ----------

    private void approve(ClinicChangeRequest changeRequest) {
        ClinicReservation reservation = changeRequest.getReservation();
        if (!reservation.isReserved()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        if (changeRequest.isCancelRequest()) {
            reservation.cancel();
            return;
        }

        // 기존 예약을 먼저 비워야 같은 학생이 두 시간대에 RESERVED로 남지 않는다
        reservation.moveOut();
        // 정원 재확인은 학생 신청과 같은 메서드를 쓴다. 세 경로가 같은 경합에 노출된다
        clinicReservationService.reserveLocked(
            changeRequest.getTargetClinic().getId(),
            List.of(changeRequest.getStudent()),
            null, true);
    }

    private Teacher currentTeacher() {
        return teacherRepository.findByUserId(CurrentUser.get().userId())
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
