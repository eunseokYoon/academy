-- 수업 출결 [온라인]과 수업 영상 시청 기록(2026-09-29 사용자 결정).
--
-- 1) ONLINE — 수업에 못 오고 영상으로 들은 날. 출석으로 친다(MAKEUP 과 같은 쪽).
--    CHECK 제약이 두 곳이다(CLAUDE.md 5-1). 클리닉 화면은 ONLINE 을 고르지 않지만 enum 이 공유라 둘 다 넓힌다.
ALTER TABLE attendances DROP CONSTRAINT ck_attendances_status;
ALTER TABLE attendances ADD CONSTRAINT ck_attendances_status
    CHECK (status IN ('PRESENT','LATE','ABSENT','SICK','EXCUSED','MAKEUP','ONLINE'));

ALTER TABLE clinic_reservations DROP CONSTRAINT ck_clinic_res_attend;
ALTER TABLE clinic_reservations ADD CONSTRAINT ck_clinic_res_attend
    CHECK (attend_status IN ('PRESENT','LATE','ABSENT','SICK','EXCUSED','MAKEUP','ONLINE'));

-- 2) 결석인 학생이 영상을 80% 이상 보면 자동으로 ONLINE 이 된다. 선생님이 ONLINE 을 다른 값으로
--    되돌리면 이 칸이 true 가 되어 다시 자동으로 바뀌지 않는다 — 안 그러면 다음 재생 보고에 되돌아간다.
ALTER TABLE attendances ADD COLUMN online_auto_blocked BOOLEAN NOT NULL DEFAULT false;

-- 3) 시청 기록. V14 에서 없앤 lesson_views(30초 타이머 자기 신고)와 다르다 — 실제로 재생 위치가
--    지나간 10초 칸만 칠한다. 건너뛴 구간·멈춰 둔 시간은 안 쌓이고 다시 봐도 두 번 세지 않는다.
--
--    lesson_video_id 가 아니라 (수업, 학생, 영상 URL) 로 묶는다 — 수업을 저장할 때마다 lesson_videos
--    행을 지우고 다시 넣어서(Lesson.replaceVideos) id 로 묶으면 선생님이 수업을 고칠 때마다 기록이 사라진다.
--    watched 는 칸 하나가 1비트인 비트맵이다. 6시간(2160칸)까지 받는다.
CREATE TABLE lesson_video_watches (
    id            BIGSERIAL PRIMARY KEY,
    lesson_id     BIGINT       NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,
    student_id    BIGINT       NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    video_url     VARCHAR(500) NOT NULL,
    bucket_count  SMALLINT     NOT NULL,
    watched       BYTEA        NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_lesson_video_watches UNIQUE (lesson_id, student_id, video_url),
    CONSTRAINT ck_lesson_video_watches_buckets CHECK (bucket_count BETWEEN 1 AND 2160)
);
