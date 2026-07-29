package com.njwenglish.repository;

import com.njwenglish.entity.ClinicChangeRequest;
import com.njwenglish.entity.enums.ChangeRequestStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClinicChangeRequestRepository extends JpaRepository<ClinicChangeRequest, Long> {

    /** 같은 예약에 PENDING 요청이 두 개면 둘 다 승인됐을 때 예약이 꼬인다. */
    boolean existsByReservationIdAndStatus(Long reservationId, ChangeRequestStatus status);

    @Query("""
        SELECT r FROM ClinicChangeRequest r
        JOIN FETCH r.student
        JOIN FETCH r.reservation res
        JOIN FETCH res.clinic
        LEFT JOIN FETCH r.targetClinic
        WHERE (:status IS NULL OR r.status = :status)
        ORDER BY r.createdAt
        """)
    List<ClinicChangeRequest> findAllWithDetail(@Param("status") ChangeRequestStatus status);

    @Query("""
        SELECT r FROM ClinicChangeRequest r
        JOIN FETCH r.reservation res
        JOIN FETCH res.clinic
        LEFT JOIN FETCH r.targetClinic
        WHERE r.id = :requestId
        """)
    Optional<ClinicChangeRequest> findWithDetail(@Param("requestId") Long requestId);

    /** S-9·P-2에서 예약 카드에 "변경 요청 중"을 붙이려면 예약별 대기 상태가 필요하다. */
    @Query("""
        SELECT r.reservation.id FROM ClinicChangeRequest r
        WHERE r.reservation.id IN :reservationIds AND r.status = 'PENDING'
        """)
    List<Long> findPendingReservationIds(@Param("reservationIds") Collection<Long> reservationIds);
}
