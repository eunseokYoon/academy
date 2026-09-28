package com.njwenglish.repository;

import com.njwenglish.entity.PushDailySend;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PushDailySendRepository extends JpaRepository<PushDailySend, Long> {

    /**
     * 그날 그 종류의 첫 발송 자리를 차지한다. <b>1 이면 보내고 0 이면 건너뛴다.</b>
     * 찾아보고 넣는 방식은 선생님이 저장을 연달아 누르면 둘 다 "없음"을 읽어 두 번 간다.
     */
    @Modifying
    @Query(value = """
        INSERT INTO push_daily_sends (user_id, kind, sent_on)
        VALUES (:userId, :kind, :sentOn)
        ON CONFLICT ON CONSTRAINT uq_push_daily DO NOTHING
        """, nativeQuery = true)
    int claim(@Param("userId") Long userId,
              @Param("kind") String kind,
              @Param("sentOn") LocalDate sentOn);
}
