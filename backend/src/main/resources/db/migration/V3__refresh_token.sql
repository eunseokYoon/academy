-- Phase 2 인증. V1·V2는 건드리지 않는다.

-- 초기 비밀번호(0000) 상태 표시.
-- true인 토큰은 비밀번호 변경 외 모든 API가 403 PASSWORD_CHANGE_REQUIRED다.
-- 시드 선생님 계정은 DEFAULT false로 남는다(로그인 후 바로 쓸 수 있어야 한다).
ALTER TABLE users ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT false;

-- Refresh 토큰은 폐기 가능해야 해서 DB에 남긴다.
-- 원문이 아니라 해시를 저장한다. DB가 유출돼도 토큰을 재사용할 수 없다.
CREATE TABLE refresh_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users(id),
    token_hash VARCHAR(100) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ  NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
