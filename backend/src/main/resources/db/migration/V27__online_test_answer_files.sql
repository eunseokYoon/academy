-- 온라인 테스트 해설지를 여러 개 올린다(2026-09-29). 한 칸(answer_s3_key)이던 것을 표로 옮긴다.
--
-- 한 칸을 남겨 두지 않는다 — 「첫 파일은 저기, 나머지는 여기」로 규칙이 갈라진다
-- (lesson_videos 가 lessons.video_url 을 드롭한 V22 와 같은 이유).
-- sort_order 에 UNIQUE 를 걸지 않는다 — 전량 교체라 flush 에서 INSERT 가 DELETE 보다
-- 먼저 나가 같은 순번이 잠깐 두 줄이 된다(9-1 과 같은 함정).
CREATE TABLE online_test_answer_files (
    id              BIGSERIAL PRIMARY KEY,
    online_test_id  BIGINT       NOT NULL REFERENCES online_tests(id) ON DELETE CASCADE,
    s3_key          VARCHAR(500) NOT NULL,
    sort_order      SMALLINT     NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_online_test_answer_files_test ON online_test_answer_files (online_test_id);

INSERT INTO online_test_answer_files (online_test_id, s3_key, sort_order, created_at)
SELECT id, answer_s3_key, 0, now()
FROM online_tests
WHERE answer_s3_key IS NOT NULL;

ALTER TABLE online_tests DROP COLUMN answer_s3_key;
