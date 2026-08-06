-- ============================================================
-- 수업일 변경 요청 (S-9) + 공지의 개인 대상
--
-- 학생이 "이번 주 우리 반 수업 대신 같은 주 다른 반 수업에 가겠다"고 요청하고
-- 선생님이 승인하면 학생·학부모·선생님에게 공지가 뜬다.
--
-- <b>배정(enrollments)도 수업(lessons)도 바뀌지 않는다.</b> 승인의 결과물은 공지 한 건뿐이다.
-- 실제 반 이동으로 만들면 출석·숙제·성적이 전부 따라 움직여야 하고 되돌릴 방법이 없다.
-- 그래서 원래 반의 출석부에는 그 날이 그대로 남는다 — 선생님이 출석 확정할 때 손으로 처리한다.
-- ============================================================

-- 공지에 개인 대상을 추가한다. 변경 사유가 같은 반 20명에게 보이면 안 되므로
-- CLASS(반 전체)로는 이 알림을 보낼 수 없다.
ALTER TABLE notices ADD COLUMN student_id BIGINT REFERENCES students(id);

ALTER TABLE notices DROP CONSTRAINT ck_notices_scope;
ALTER TABLE notices DROP CONSTRAINT ck_notices_target;

-- scope와 대상 컬럼은 항상 짝이다. 셋 중 정확히 하나만 채워져야 한다 —
-- 둘 다 채우면 조회 쿼리에서 어느 쪽이 이기는지가 코드에 숨는다.
ALTER TABLE notices
    ADD CONSTRAINT ck_notices_scope CHECK (scope IN ('ALL', 'CLASS', 'STUDENT')),
    ADD CONSTRAINT ck_notices_target CHECK (
        (scope = 'ALL'     AND class_room_id IS NULL     AND student_id IS NULL) OR
        (scope = 'CLASS'   AND class_room_id IS NOT NULL AND student_id IS NULL) OR
        (scope = 'STUDENT' AND class_room_id IS NULL     AND student_id IS NOT NULL));

CREATE INDEX idx_notices_student ON notices(student_id) WHERE student_id IS NOT NULL;

CREATE TABLE lesson_change_requests (
    id             BIGSERIAL PRIMARY KEY,
    student_id     BIGINT      NOT NULL REFERENCES students(id),
    -- 못 가는 내 수업 회차
    from_lesson_id BIGINT      NOT NULL REFERENCES lessons(id),
    -- 대신 갈 다른 반 수업 회차. 같은 주(월~일) 안이어야 한다 (서버에서 검증)
    to_lesson_id   BIGINT      NOT NULL REFERENCES lessons(id),
    -- 사유는 필수다. 공지 본문에 그대로 들어간다.
    -- 클리닉과 달리 선택지 목록을 두지 않았다. 옵션이 미확정이라 지어내지 않는다.
    reason         TEXT        NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    decided_by     BIGINT      REFERENCES teachers(id),
    decided_at     TIMESTAMPTZ,
    -- 승인 시 만들어진 공지. 선생님이 그 공지를 지워도 요청 이력은 남아야 하므로 SET NULL이다
    notice_id      BIGINT      REFERENCES notices(id) ON DELETE SET NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_lesson_change_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_lesson_change_diff   CHECK (from_lesson_id <> to_lesson_id)
);

-- 같은 수업에 PENDING이 둘이면 선생님이 둘 다 승인했을 때 서로 다른 반으로 가는
-- 공지가 두 장 나간다. DB에서 막는다.
CREATE UNIQUE INDEX ux_lesson_change_pending
    ON lesson_change_requests(student_id, from_lesson_id) WHERE status = 'PENDING';

CREATE INDEX idx_lesson_change_pending
    ON lesson_change_requests(status, created_at) WHERE status = 'PENDING';

CREATE INDEX idx_lesson_change_student
    ON lesson_change_requests(student_id, created_at DESC);
