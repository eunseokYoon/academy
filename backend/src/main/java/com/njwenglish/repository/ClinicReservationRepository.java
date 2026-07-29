package com.njwenglish.repository;

import com.njwenglish.entity.ClinicReservation;
import com.njwenglish.entity.enums.ReservationStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClinicReservationRepository extends JpaRepository<ClinicReservation, Long> {

    /** 정원 판정용. 반드시 findByIdForUpdate로 클리닉을 잠근 뒤에 부른다. */
    long countByClinicIdAndStatus(Long clinicId, ReservationStatus status);

    boolean existsByClinicIdAndStudentIdAndStatus(Long clinicId, Long studentId,
                                                  ReservationStatus status);

    Optional<ClinicReservation> findByClinicIdAndStudentIdAndStatus(Long clinicId, Long studentId,
                                                                    ReservationStatus status);

    /** T-13 명단. 학생 이름은 students.name이라 user를 타지 않는다. */
    @Query("""
        SELECT r FROM ClinicReservation r
        JOIN FETCH r.student s
        WHERE r.clinic.id = :clinicId AND r.status = 'RESERVED'
        ORDER BY s.name
        """)
    List<ClinicReservation> findReservedWithStudent(@Param("clinicId") Long clinicId);

    /** 목록 화면에서 클리닉별 신청 인원을 한 번에 채운다. 클리닉 수만큼 쿼리를 돌리지 않는다. */
    @Query("""
        SELECT r.clinic.id AS clinicId, COUNT(r) AS reservedCount
        FROM ClinicReservation r
        WHERE r.clinic.id IN :clinicIds AND r.status = 'RESERVED'
        GROUP BY r.clinic.id
        """)
    List<ClinicCount> countReservedByClinicIds(@Param("clinicIds") Collection<Long> clinicIds);

    /** 출석 확정 여부. clinics에 컬럼이 없어 예약 쪽 attend_status로 판정한다. */
    @Query("""
        SELECT r.clinic.id FROM ClinicReservation r
        WHERE r.clinic.id IN :clinicIds AND r.attendStatus IS NOT NULL
        GROUP BY r.clinic.id
        """)
    List<Long> findAttendanceConfirmedClinicIds(@Param("clinicIds") Collection<Long> clinicIds);

    /** S-9·P-2에서 본인(자녀) 예약을 한 번에 가져온다. */
    @Query("""
        SELECT r FROM ClinicReservation r
        JOIN FETCH r.clinic c
        WHERE r.student.id = :studentId
          AND c.clinicDate BETWEEN :from AND :to
          AND r.status = 'RESERVED'
        ORDER BY c.clinicDate, c.startTime
        """)
    List<ClinicReservation> findReservedByStudentInRange(@Param("studentId") Long studentId,
                                                         @Param("from") LocalDate from,
                                                         @Param("to") LocalDate to);

    interface ClinicCount {
        Long getClinicId();

        long getReservedCount();
    }
}
