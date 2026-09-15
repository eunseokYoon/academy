-- ============================================================
-- 공지 대상을 한 컬럼으로. 학부모 전용 공지가 필요해졌다(2026-09-10)
--
-- 불리언을 둘 두지 않는 이유: students_only와 parents_only가 둘 다 켜지면
-- 아무도 못 보는 공지가 만들어진다. 세 값이면 그 상태가 구조적으로 불가능하다
-- ============================================================
ALTER TABLE notices ADD COLUMN audience VARCHAR(20) NOT NULL DEFAULT 'ALL';

UPDATE notices SET audience = 'STUDENT_ONLY' WHERE students_only;

ALTER TABLE notices DROP COLUMN students_only;

ALTER TABLE notices ADD CONSTRAINT ck_notices_audience
    CHECK (audience IN ('ALL','STUDENT_ONLY','PARENT_ONLY'));

-- 자동 발행 공지(수업일·클리닉 변경)는 학생과 학부모가 다 봐야 한다.
-- 한쪽이 못 받으면 변경 알림 자체가 무의미하다 — 화면 검증만 두지 말고 DB에서 막는다
ALTER TABLE notices ADD CONSTRAINT ck_notices_audience_scope
    CHECK (scope <> 'STUDENT' OR audience = 'ALL');
