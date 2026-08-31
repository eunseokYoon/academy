package com.njwenglish.repository;

import com.njwenglish.entity.Clinic;
import com.njwenglish.entity.enums.ClinicStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClinicRepository extends JpaRepository<Clinic, Long> {

    /**
     * 정원 체크 전에 반드시 이걸로 잠근다. 잠그지 않고 세면 두 트랜잭션이 같은 count를
     * 읽어 정원 6에 7명이 들어간다. 조건부 삽입 한 방도 같은 버그다 —
     * READ COMMITTED에서 서브쿼리의 count(*)는 세는 행에 락을 걸지 않는다.
     *
     * <p>클리닉 단위로만 직렬화하므로 서로 다른 클리닉은 병렬이다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Clinic c WHERE c.id = :clinicId")
    Optional<Clinic> findByIdForUpdate(@Param("clinicId") Long clinicId);

    /** uq_clinics_slot이 OPEN 부분 인덱스라 닫힌 시간대는 같은 슬롯을 다시 열 수 있다. */
    boolean existsByClinicDateAndStartTimeAndStatus(LocalDate clinicDate, LocalTime startTime,
                                                    ClinicStatus status);

    /**
     * 일괄 개설에서 건너뛸 날짜. 같은 시작 시각으로 이미 열려 있는 날들이다.
     * 하루씩 exists를 부르면 기간만큼 쿼리가 나가서 한 번에 뽑는다.
     */
    @Query("""
        SELECT c.clinicDate FROM Clinic c
        WHERE c.clinicDate BETWEEN :from AND :to
          AND c.startTime = :startTime
          AND c.status = com.njwenglish.entity.enums.ClinicStatus.OPEN
        """)
    List<LocalDate> findOpenDates(@Param("from") LocalDate from,
                                  @Param("to") LocalDate to,
                                  @Param("startTime") LocalTime startTime);

    /** 학생 시리즈 카드. 오늘 이후의 OPEN 클리닉만 묶는다. */
    @Query("""
        SELECT c FROM Clinic c
        WHERE c.clinicDate >= :from
          AND c.status = com.njwenglish.entity.enums.ClinicStatus.OPEN
        ORDER BY c.clinicDate, c.startTime
        """)
    List<Clinic> findOpenFrom(@Param("from") LocalDate from);

    @Query("""
        SELECT c FROM Clinic c
        WHERE c.clinicDate BETWEEN :from AND :to
          AND (:status IS NULL OR c.status = :status)
        ORDER BY c.clinicDate, c.startTime
        """)
    List<Clinic> findInRange(@Param("from") LocalDate from,
                             @Param("to") LocalDate to,
                             @Param("status") ClinicStatus status);
}
