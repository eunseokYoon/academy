-- ============================================================
-- 주차별 테스트 (단어 · 리뷰 · 실전모고 · 클리닉)
-- 반 × 주차 × 종류 = 그리드의 열 하나. 전체 문항 수가 여기 한 번만 산다
-- ============================================================
CREATE TABLE weekly_tests (
    id             BIGSERIAL PRIMARY KEY,
    class_room_id  BIGINT      NOT NULL REFERENCES class_rooms(id),
    test_type      VARCHAR(20) NOT NULL,
    year           SMALLINT    NOT NULL,
    month          SMALLINT    NOT NULL,
    week           SMALLINT    NOT NULL,
    total_count    SMALLINT,
    internal_total SMALLINT,
    external_total SMALLINT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_weekly_tests_type  CHECK (test_type IN ('WORD','REVIEW','PRACTICE','CLINIC')),
    CONSTRAINT ck_weekly_tests_month CHECK (month BETWEEN 1 AND 12),
    CONSTRAINT ck_weekly_tests_week  CHECK (week  BETWEEN 1 AND 5),
    -- 종류마다 채워야 할 헤더값이 다르다. 서비스 검증만 믿지 말고 DB에서도 막는다
    CONSTRAINT ck_weekly_tests_shape CHECK (
        (test_type IN ('WORD','PRACTICE')
             AND total_count IS NOT NULL AND total_count > 0
             AND internal_total IS NULL AND external_total IS NULL)
     OR (test_type = 'REVIEW'
             AND total_count IS NULL AND internal_total IS NULL AND external_total IS NULL)
     OR (test_type = 'CLINIC'
             AND total_count IS NULL
             AND internal_total IS NOT NULL AND internal_total > 0
             AND external_total IS NOT NULL AND external_total > 0)
    )
);

CREATE UNIQUE INDEX uq_weekly_tests
    ON weekly_tests(class_room_id, test_type, year, month, week);
CREATE INDEX idx_weekly_tests_grid
    ON weekly_tests(class_room_id, year, month, week);

-- 학생 한 명의 한 칸. 선생님이 채운 칸만 행이 생긴다
CREATE TABLE weekly_test_scores (
    id               BIGSERIAL PRIMARY KEY,
    weekly_test_id   BIGINT      NOT NULL REFERENCES weekly_tests(id) ON DELETE CASCADE,
    student_id       BIGINT      NOT NULL REFERENCES students(id),
    correct_count    SMALLINT,
    internal_correct SMALLINT,
    external_correct SMALLINT,
    result           VARCHAR(10),
    retest_passed    BOOLEAN     NOT NULL DEFAULT false,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_wts_result CHECK (result IN ('PASS','FAIL')),
    CONSTRAINT ck_wts_counts CHECK (
        (correct_count    IS NULL OR correct_count    >= 0)
    AND (internal_correct IS NULL OR internal_correct >= 0)
    AND (external_correct IS NULL OR external_correct >= 0)
    ),
    -- 재시험 통과는 Fail을 받은 학생에게만 붙는다
    CONSTRAINT ck_wts_retest CHECK (retest_passed = false OR result = 'FAIL'),
    -- 빈 행이 남으면 학부모 화면에 빈 항목으로 샌다
    CONSTRAINT ck_wts_not_empty CHECK (
        correct_count IS NOT NULL OR internal_correct IS NOT NULL
     OR external_correct IS NOT NULL OR result IS NOT NULL
    )
);

CREATE UNIQUE INDEX uq_weekly_test_scores
    ON weekly_test_scores(weekly_test_id, student_id);
CREATE INDEX idx_wts_student ON weekly_test_scores(student_id);

-- ============================================================
-- 정기고사 (학교 내신 · 모의고사). 선생님만 본다
-- ============================================================
CREATE TABLE regular_exam_scores (
    id         BIGSERIAL PRIMARY KEY,
    student_id BIGINT       NOT NULL REFERENCES students(id),
    year       SMALLINT     NOT NULL,
    exam_slot  VARCHAR(20)  NOT NULL,
    raw_score  NUMERIC(5,2) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_res_slot CHECK (exam_slot IN
        ('S1_MIDTERM','S1_FINAL','S2_MIDTERM','S2_FINAL','MOCK_MAR','MOCK_JUN','MOCK_SEP')),
    CONSTRAINT ck_res_score CHECK (raw_score BETWEEN 0 AND 100)
);

CREATE UNIQUE INDEX uq_regular_exam_scores
    ON regular_exam_scores(student_id, year, exam_slot);
CREATE INDEX idx_res_student ON regular_exam_scores(student_id, year DESC);

-- ============================================================
-- 온라인 테스트: 앞 N문항이 내부지문. 결과 화면에서 내부·외부를 집계한다
-- ============================================================
ALTER TABLE online_tests ADD COLUMN internal_question_count SMALLINT;
ALTER TABLE online_tests ADD CONSTRAINT ck_online_tests_internal
    CHECK (internal_question_count IS NULL
        OR internal_question_count BETWEEN 0 AND question_count);
