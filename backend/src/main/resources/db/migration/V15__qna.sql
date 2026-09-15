-- 질의응답 게시판. 질문과 답글이 한 테이블에 살고 parent_id로 구분한다.
--
-- ck_qna_posts_shape가 이 구조의 안전장치다. 한 테이블에 두 종류가 살면
-- 답글 행에 들어간 is_public이 조용히 무시되는 사고가 나는데, 이 제약이 저장 자체를 막는다.
-- 질문은 항상 학생이 쓰고(선생님은 답글만 쓴다), 답글 작성자는 학생이거나 선생님 중 하나다.
CREATE TABLE qna_posts (
    id            BIGSERIAL PRIMARY KEY,
    parent_id     BIGINT REFERENCES qna_posts (id) ON DELETE CASCADE,
    class_room_id BIGINT REFERENCES class_rooms (id),
    student_id    BIGINT REFERENCES students (id),
    teacher_id    BIGINT REFERENCES teachers (id),
    title         VARCHAR(200),
    content       TEXT        NOT NULL,
    is_public     BOOLEAN,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_qna_posts_shape CHECK (
        (parent_id IS NULL
            AND class_room_id IS NOT NULL AND title IS NOT NULL AND is_public IS NOT NULL
            AND student_id IS NOT NULL AND teacher_id IS NULL)
            OR
        (parent_id IS NOT NULL
            AND class_room_id IS NULL AND title IS NULL AND is_public IS NULL
            AND (student_id IS NULL) <> (teacher_id IS NULL))
        )
);

-- 목록은 반별 최신순이다. 부분 인덱스라 답글 행이 들어오지 않는다.
CREATE INDEX ix_qna_posts_class_room
    ON qna_posts (class_room_id, created_at DESC) WHERE parent_id IS NULL;
CREATE INDEX ix_qna_posts_parent ON qna_posts (parent_id, created_at);

-- 질문·답글 사진을 한 테이블이 받는다. 같은 테이블을 가리키므로 nullable FK도 CHECK도 없다.
CREATE TABLE qna_photos (
    id          BIGSERIAL PRIMARY KEY,
    qna_post_id BIGINT       NOT NULL REFERENCES qna_posts (id) ON DELETE CASCADE,
    s3_key      VARCHAR(500) NOT NULL,
    sort_order  SMALLINT     NOT NULL,
    bytes       INTEGER,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_qna_photos_post ON qna_photos (qna_post_id, sort_order);
