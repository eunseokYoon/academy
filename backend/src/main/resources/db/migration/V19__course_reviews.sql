-- 수강 후기. 학생이 선생님을 별점 + 텍스트로 평가한다.
--
-- qna_posts에 얹지 않은 이유: 후기를 세 번째 종류로 넣으면 ck_qna_posts_shape가
-- 3분기가 되고 "parent_id가 null이면 질문"이 "질문이거나 후기"가 된다.
-- 목록 쿼리마다 종류 조건을 달아야 하고, 한 곳이라도 빠지면 후기가 질문 목록에 섞인다.
--
-- student_id UNIQUE가 "학생당 1개"의 정본이다. 서비스 검사만 두면
-- 더블클릭에 두 행이 들어간다.
--
-- rating은 별 개수의 2배다 (0.5 → 1, 5.0 → 10). 정수로 저장하면 반올림 사고가 없다.
--
-- class_room_id는 nullable이다. 선생님 화면의 반 필터용일 뿐이고,
-- 활성 배정이 없는 학생(가입 직후)이 후기를 쓰다 실패하면 안 된다.
CREATE TABLE course_reviews (
    id            BIGSERIAL PRIMARY KEY,
    student_id    BIGINT      NOT NULL UNIQUE REFERENCES students (id) ON DELETE CASCADE,
    class_room_id BIGINT      REFERENCES class_rooms (id),
    rating        SMALLINT    NOT NULL,
    content       TEXT        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_course_reviews_rating CHECK (rating BETWEEN 1 AND 10)
);

CREATE INDEX ix_course_reviews_class_room ON course_reviews (class_room_id, created_at DESC);
