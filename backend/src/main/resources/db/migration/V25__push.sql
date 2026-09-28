-- ============================================================
-- 푸시 알림(D 단계, 2026-09-27). 설계: docs/superpowers/specs/2026-09-16-flutter-app-and-push-design.md 2부
--
-- 선생님은 알림을 받지 않는다 — device_tokens 에 TEACHER 행이 생기지 않게 등록 시점에 막는다.
-- ============================================================

-- token UNIQUE: 한 기기를 다른 계정으로 로그인하면 행이 옮겨간다(UPSERT 로 user_id 갱신).
-- 없으면 형제가 폰 하나를 같이 쓸 때 한 기기가 두 계정의 알림을 받는다.
-- ON DELETE CASCADE: 제3자 학생 삭제로 users 가 지워질 때 같이 지워져야 한다.
-- refresh_tokens 처럼 FK 가 삭제를 막으면 안 된다.
CREATE TABLE device_tokens (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token        TEXT        NOT NULL UNIQUE,
    platform     VARCHAR(16) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_device_tokens_platform CHECK (platform IN ('ANDROID','IOS'))
);

CREATE INDEX idx_device_tokens_user ON device_tokens (user_id);

-- 일괄 저장 트리거(숙제 채점·주차별 성적·출석 확정)를 학생당 하루 1건으로 묶는다.
-- INSERT 가 성공하면 보내고 충돌하면 건너뛴다 — 잠금 없이 경합에 안전하다.
-- 묶기 전용이다. 읽는 화면을 붙이지 마라(보관 기간·개인정보 문제가 따라온다).
CREATE TABLE push_daily_sends (
    id       BIGSERIAL PRIMARY KEY,
    user_id  BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    kind     VARCHAR(32) NOT NULL,
    sent_on  DATE        NOT NULL,
    CONSTRAINT uq_push_daily UNIQUE (user_id, kind, sent_on),
    CONSTRAINT ck_push_daily_kind CHECK (kind IN
        ('HOMEWORK_GRADED','WEEKLY_SCORE','ATTENDANCE_LESSON','ATTENDANCE_CLINIC'))
);

-- 알림 끄기는 한 칸이다. 종류별 토글을 만들지 마라.
-- 기본값이 TRUE 여야 한다 — FALSE 로 깔면 앱을 깐 전원이 알림을 못 받고 아무도 원인을 모른다.
ALTER TABLE users ADD COLUMN push_enabled BOOLEAN NOT NULL DEFAULT TRUE;
