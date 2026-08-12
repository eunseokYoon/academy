package com.njwenglish.repository;

import com.njwenglish.entity.ClinicChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * <b>쓰기 전용이다.</b> 화면에서 읽는 곳이 없다 — 선생님이 학생의 변경 이력을 보지 않기로
 * 했다(2026-08-11 확정). 그래도 남기는 이유는 "언제 누가 왜 바꿨나"가 사라지면 나중에
 * 물어볼 데가 없기 때문이다. 학생·학부모에게는 변경 시점에 공지가 따로 나간다.
 *
 * <p>조회 메서드를 다시 붙이려면 화면부터 정하고 붙여라 — 쓰는 곳 없는 쿼리가 늘어난다.
 */
public interface ClinicChangeLogRepository extends JpaRepository<ClinicChangeLog, Long> {
}
