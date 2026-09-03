-- 수업 영상을 여러 개 담는다 (2026-09-04 확정).
--
-- 지금까지는 lessons.video_url 한 칸이라 수업당 링크가 하나였다. 선생님은 그날 찍은
-- 영상 여러 개를 재생목록으로 묶어 올리는데, 유튜브 재생목록 임베드가 「일부 공개」
-- 목록에서 재생되지 않아 학생이 못 봤다. 링크를 여러 개 받으면 재생목록을 거치지
-- 않으므로 그 제약에 아예 안 걸린다.
--
-- sort_order에 UNIQUE를 걸지 않는다. 저장이 "전량 교체"라 Hibernate가 flush에서
-- INSERT를 DELETE보다 먼저 실행하면 같은 순번이 잠깐 두 줄이 되어 제약에 걸린다 —
-- class_room_schedules에서 겪은 것과 같은 함정이다(CLAUDE.md 9-1). 순번은 정렬용일
-- 뿐이라 중복돼도 해가 없고, 동점은 id로 갈린다.

CREATE TABLE lesson_videos (
    id         BIGSERIAL PRIMARY KEY,
    lesson_id  BIGINT       NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,
    url        VARCHAR(500) NOT NULL,
    -- 비워도 된다. 화면이 "영상 1"로 채운다
    title      VARCHAR(100),
    sort_order SMALLINT     NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_lesson_videos_lesson ON lesson_videos(lesson_id, sort_order);

-- 기존 단일 링크를 첫 번째 영상으로 옮긴다. 빈 문자열은 링크가 없는 것으로 본다.
INSERT INTO lesson_videos (lesson_id, url, sort_order)
SELECT id, video_url, 0
FROM lessons
WHERE video_url IS NOT NULL AND btrim(video_url) <> '';

-- 컬럼을 남기지 않는다. 남기면 "첫 영상은 저기, 나머지는 여기"가 되어 규칙이 갈라진다.
ALTER TABLE lessons DROP COLUMN video_url;
