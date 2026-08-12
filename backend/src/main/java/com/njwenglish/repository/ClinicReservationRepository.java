package com.njwenglish.repository;

import com.njwenglish.entity.Clinic;
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

    /**
     * T-5 미확정 클리닉. 수업과 같은 자리에서 확정하려고 뽑는다.
     *
     * <p>판정 기준이 수업과 다르다. lessons에는 attendance_status가 있지만 clinics에는
     * 없어서 <b>예약 쪽 attend_status가 비었는지로 본다.</b>
     *
     * <p>예약이 하나도 없는 시간대는 나오지 않는다 — 조인이 예약에서 시작하기 때문이고,
     * 그게 맞다. 아무도 신청 안 한 시간대는 확정할 것이 없는데 목록에 뜨면 매주 쌓인다.
     *
     * <p>미래 클리닉도 제외한다(clinicDate <= today). 아직 오지도 않은 날이
     * "확정해야 할 것"으로 뜨면 목록이 신호 역할을 못 한다.
     */
    @Query("""
        SELECT DISTINCT c FROM ClinicReservation r JOIN r.clinic c
        WHERE c.clinicDate <= :today
          AND r.status = 'RESERVED'
          AND r.attendStatus IS NULL
        ORDER BY c.clinicDate DESC, c.startTime DESC
        """)
    List<Clinic> findPendingUntil(@Param("today") LocalDate today);

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

    /**
     * P-1 홈의 다음 클리닉 하나. 오늘 포함이다 — 오늘 17시 클리닉이 아침에 사라지면 안 된다.
     * 없으면 Optional.empty()이고 프론트가 카드를 숨긴다.
     */
    @Query("""
        SELECT r FROM ClinicReservation r
        JOIN FETCH r.clinic c
        WHERE r.student.id = :studentId
          AND r.status = 'RESERVED'
          AND c.clinicDate >= :from
        ORDER BY c.clinicDate, c.startTime
        LIMIT 1
        """)
    Optional<ClinicReservation> findNextReserved(@Param("studentId") Long studentId,
                                                @Param("from") LocalDate from);

    interface ClinicCount {
        Long getClinicId();

        long getReservedCount();
    }
}
