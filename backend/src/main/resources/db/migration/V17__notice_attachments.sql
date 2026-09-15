-- 공지 첨부. 자료실이 1행 = 1파일이었던 것과 달리 공지 하나에 여러 개가 붙는다.
--
-- s3_key는 materials/ 프리픽스를 그대로 쓴다. 자료실이 쓰던 MaterialKeys를 재사용하기
-- 때문이고, 프리픽스를 바꾸면 "materials/에는 수명주기를 걸지 않는다"는 규칙이 붕 뜬다.
--
-- ON DELETE CASCADE는 행만 지운다. S3 객체는 NoticeService가 마지막 참조일 때만 지운다.
CREATE TABLE notice_attachments (
    id         BIGSERIAL PRIMARY KEY,
    notice_id  BIGINT       NOT NULL REFERENCES notices (id) ON DELETE CASCADE,
    s3_key     VARCHAR(500) NOT NULL,
    file_name  VARCHAR(255) NOT NULL,
    bytes      BIGINT,
    sort_order SMALLINT     NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_notice_attachments_notice ON notice_attachments (notice_id, sort_order);
