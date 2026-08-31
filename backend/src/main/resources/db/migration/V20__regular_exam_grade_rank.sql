-- 정기고사에 11월 모의를 더하고, 점수 옆에 등급·등수 칸을 낸다.
--
-- 등급·등수는 "등수·백분위 같은 상대 지표는 만들지 않는다"는 규칙의 예외이고 경계가 좁다 —
-- 학교·평가원이 매긴 값을 선생님이 받아 적는 것만이다. 학원이 자기 학생을 줄 세워
-- 계산하는 것은 여전히 금지다. regular_exam_scores는 선생님 전용이라
-- 이 값들이 학생·학부모 DTO에 흘러 들어가면 안 된다.

-- V8의 제약은 기존 마이그레이션이라 못 고친다. 지우고 8슬롯으로 다시 건다
ALTER TABLE regular_exam_scores DROP CONSTRAINT ck_res_slot;
ALTER TABLE regular_exam_scores ADD CONSTRAINT ck_res_slot CHECK (exam_slot IN
    ('S1_MIDTERM', 'S1_FINAL', 'S2_MIDTERM', 'S2_FINAL',
     'MOCK_MAR', 'MOCK_JUN', 'MOCK_SEP', 'MOCK_NOV'));

-- 등급만 알고 원점수는 모르는 경우가 흔하다(모의고사가 특히 그렇다).
-- NOT NULL로 두면 그런 칸을 아예 기록할 수 없다
ALTER TABLE regular_exam_scores ALTER COLUMN raw_score DROP NOT NULL;

-- rank는 SQL 윈도우 함수 이름이라 쿼리에서 따옴표를 강요하는 자리가 생긴다.
-- school_rank가 "누구 기준 등수인지"도 같이 말해 준다 — 이번 예외의 경계가 거기다
ALTER TABLE regular_exam_scores ADD COLUMN grade       SMALLINT;
ALTER TABLE regular_exam_scores ADD COLUMN school_rank SMALLINT;

-- 내신·모의 둘 다 9등급제다
ALTER TABLE regular_exam_scores ADD CONSTRAINT ck_res_grade
    CHECK (grade IS NULL OR grade BETWEEN 1 AND 9);

ALTER TABLE regular_exam_scores ADD CONSTRAINT ck_res_rank
    CHECK (school_rank IS NULL OR school_rank >= 1);

-- 모의고사에는 등수를 받지 않는다(2026-08-24 확정). 화면에서도 칸을 안 그리지만,
-- 화면은 안내일 뿐이라 API를 직접 치면 뚫린다. DB가 정본이다
ALTER TABLE regular_exam_scores ADD CONSTRAINT ck_res_rank_slot
    CHECK (school_rank IS NULL OR exam_slot IN
        ('S1_MIDTERM', 'S1_FINAL', 'S2_MIDTERM', 'S2_FINAL'));

-- 셋 다 비면 행이 있을 이유가 없다. "빈 칸은 행을 만들지 않는다"의 3칸 버전이다
ALTER TABLE regular_exam_scores ADD CONSTRAINT ck_res_any
    CHECK (raw_score IS NOT NULL OR grade IS NOT NULL OR school_rank IS NOT NULL);
