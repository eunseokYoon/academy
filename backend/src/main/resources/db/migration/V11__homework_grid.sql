-- ============================================================
-- 숙제 오프라인 채점 그리드 + 재제출
-- submissions 한 행이 곧 그리드의 칸이다. 새 테이블을 만들지 않는다
-- ============================================================

-- GRID = 그리드의 열(오프라인 채점), ONLINE = 기존 온라인 출제.
-- DEFAULT 'ONLINE'이라 기존 숙제는 전부 그대로 남는다
ALTER TABLE homeworks ADD COLUMN kind       VARCHAR(10) NOT NULL DEFAULT 'ONLINE';
ALTER TABLE homeworks ADD COLUMN sort_order SMALLINT;
ALTER TABLE homeworks ALTER COLUMN due_at DROP NOT NULL;

ALTER TABLE homeworks ADD CONSTRAINT ck_homeworks_kind CHECK (kind IN ('GRID','ONLINE'));

-- ONLINE은 마감이 반드시 있다. GRID의 due_at은 "재제출 마감"이라 요청 전까지 null이다
ALTER TABLE homeworks ADD CONSTRAINT ck_homeworks_due
    CHECK (kind = 'GRID' OR due_at IS NOT NULL);

-- GRID 열은 수업일에 매달린다. lesson이 없으면 그리드를 그릴 수 없다
ALTER TABLE homeworks ADD CONSTRAINT ck_homeworks_grid_lesson
    CHECK (kind = 'ONLINE' OR (lesson_id IS NOT NULL AND sort_order IS NOT NULL));

CREATE INDEX idx_homeworks_grid ON homeworks(lesson_id, sort_order) WHERE kind = 'GRID';

-- ---------- 칸의 채점 축 ----------

ALTER TABLE submissions ADD COLUMN result                   VARCHAR(10);
ALTER TABLE submissions ADD COLUMN completion_rate          SMALLINT;
ALTER TABLE submissions ADD COLUMN resolved_by_resubmission BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE submissions ADD CONSTRAINT ck_submissions_result
    CHECK (result IN ('DONE','PARTIAL','NOT_DONE'));

-- 퍼센트는 세모에만 붙는다. 0과 100은 X·O가 이미 표현하므로 1~99다.
-- CASE로 쓰는 이유: result가 null일 때 OR 체인은 UNKNOWN이 되어 CHECK를 그냥 통과한다.
-- IS NOT NULL을 빼지 마라. NULL BETWEEN 1 AND 99는 FALSE가 아니라 NULL이고,
-- CHECK는 NULL에서 통과한다 — 퍼센트 없는 PARTIAL이 그냥 들어온다
ALTER TABLE submissions ADD CONSTRAINT ck_submissions_rate CHECK (
    CASE WHEN result = 'PARTIAL'
         THEN completion_rate IS NOT NULL AND completion_rate BETWEEN 1 AND 99
         ELSE completion_rate IS NULL END);

-- "O (재제출)" 표시는 O에만 붙는다. coalesce가 없으면 result가 null일 때 통과해 버린다
ALTER TABLE submissions ADD CONSTRAINT ck_submissions_resolved
    CHECK (resolved_by_resubmission = false OR coalesce(result, '') = 'DONE');

-- 재제출 대상 조회. 열 하나에 대해 PARTIAL·NOT_DONE만 훑는다
CREATE INDEX idx_submissions_result ON submissions(homework_id, result);
