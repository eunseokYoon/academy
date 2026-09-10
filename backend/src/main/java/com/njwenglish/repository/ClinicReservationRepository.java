package com.njwenglish.repository;

import com.njwenglish.entity.Clinic;
import com.njwenglish.entity.ClinicReservation;
import com.njwenglish.entity.enums.ReservationStatus;
import java.time.LocalDate;
import java.time.LocalTime;
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

    /**
     * 클리닉 삭제 직전의 잔여 행 정리. <b>CANCELED·MOVED만 지운다</b> —
     * RESERVED가 남아 있으면 호출부가 이미 409로 막았어야 한다.
     *
     * <p>이 행들을 읽는 쿼리는 없다. 전부 status = RESERVED만 본다.
     * 이동 이력은 clinic_change_logs가 따로 갖고 있다.
     */
    void deleteByClinicIdAndStatusNot(Long clinicId, ReservationStatus status);

    /** T-13 명단. 학생 이름은 students.name이라 user를 타지 않는다. */
    @Query("""
        SELECT r FROM ClinicReservation r
        JOIN FETCH r.student s
        WHERE r.clinic.id = :clinicId AND r.status = 'RESERVED'
        ORDER BY s.name
        """)
    List<ClinicReservation> findReservedWithStudent(@Param("clinicId") Long clinicId);

    /**
     * 슬롯 하나의 명단. 출결 확정이 도착 시각 단위라(2026-09-01) 그 시각만 뽑는다.
     * 이름은 students.name이다 — user.name을 쓰면 미가입 학생이 사라진다.
     *
     * <p><b>Clinic.slots() 밖의 시각도 그대로 조회한다.</b> 선생님이 시간대를 좁히면
     * 범위 밖으로 남는 예약(outOfRange)이 생기는데, 그 학생도 출결은 확정해야 한다.
     */
    @Query("""
        SELECT r FROM ClinicReservation r
        JOIN FETCH r.student s
        WHERE r.clinic.id = :clinicId
          AND r.status = 'RESERVED'
          AND r.arrivalTime = :arrivalTime
        ORDER BY s.name
        """)
    List<ClinicReservation> findReservedWithStudentAt(@Param("clinicId") Long clinicId,
                                                      @Param("arrivalTime") LocalTime arrivalTime);

    /** 목록 화면에서 클리닉별 신청 인원을 한 번에 채운다. 클리닉 수만큼 쿼리를 돌리지 않는다. */
    @Query("""
        SELECT r.clinic.id AS clinicId, COUNT(r) AS reservedCount
        FROM ClinicReservation r
        WHERE r.clinic.id IN :clinicIds AND r.status = 'RESERVED'
        GROUP BY r.clinic.id
        """)
    List<ClinicCount> countReservedByClinicIds(@Param("clinicIds") Collection<Long> clinicIds);

    /** 목록 화면에서 클리닉별 슬롯 인원과 미확정 인원을 한 번에 채운다. */
    @Query("""
        SELECT r.clinic.id AS clinicId, r.arrivalTime AS arrivalTime,
               COUNT(r) AS reservedCount,
               SUM(CASE WHEN r.attendStatus IS NULL THEN 1 ELSE 0 END) AS pendingCount
        FROM ClinicReservation r
        WHERE r.clinic.id IN :clinicIds AND r.status = 'RESERVED'
        GROUP BY r.clinic.id, r.arrivalTime
        """)
    List<SlotCount> countBySlot(@Param("clinicIds") Collection<Long> clinicIds);

    /**
     * 출석 확정 여부. clinics에 컬럼이 없어 예약 쪽 attend_status로 판정한다.
     *
     * <p><b>RESERVED 예약이 전부 채워진 클리닉만 반환한다</b>(2026-09-01). 하나라도
     * 있으면 확정으로 보던 예전 방식은 슬롯 단위 확정에서 깨진다 — 17시만 확정해도
     * 클리닉 전체가 확정으로 떠서 선생님이 21시 명단을 놓친다.
     */
    @Query("""
        SELECT r.clinic.id FROM ClinicReservation r
        WHERE r.clinic.id IN :clinicIds AND r.status = 'RESERVED'
        GROUP BY r.clinic.id
        HAVING SUM(CASE WHEN r.attendStatus IS NULL THEN 1 ELSE 0 END) = 0
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

    interface SlotCount {
        Long getClinicId();

        LocalTime getArrivalTime();

        long getReservedCount();

        long getPendingCount();
    }
}
