-- 클리닉 도착 시간 + 변경 승인 폐지 (2026-08-10 확정)
--
-- 학생이 클리닉 시간대 안에서 1시간 단위로 도착 시각을 고른다. 마지막 슬롯은 종료 1시간 전이다
-- (17:00~22:00이면 17·18·19·20·21시). 슬롯마다 clinics 행을 만들지 않는다 —
-- 정원·출석 확정·uq_clinics_slot이 전부 클리닉 단위라 다섯 배로 쪼개진다.
--
-- 그리고 시간 변경·이동·취소에서 선생님 승인을 없앴다. 강사가 1명이라 승인이 병목이다.
-- 대신 학생이 사유를 적고, 그 기록이 선생님·학생·학부모 화면에 남는다.
--
-- 되돌릴 수 없다. 기존 변경 요청의 승인·거절 이력이 사라진다.

-- 1) 도착 시간. 기존 예약은 클리닉 시작 시각으로 채운다
ALTER TABLE clinic_reservations
    ADD COLUMN arrival_time TIME;

UPDATE clinic_reservations r
SET arrival_time = c.start_time
FROM clinics c
WHERE c.id = r.clinic_id;

ALTER TABLE clinic_reservations
    ALTER COLUMN arrival_time SET NOT NULL;

-- arrival_time이 클리닉 시간 범위 안인지는 CHECK로 막을 수 없다(clinics 조인이 필요하다).
-- 앱의 Clinic.hasSlot이 유일한 방어선이다.

-- 2) 승인 절차 폐지. 요청이 아니라 "일어난 일의 기록"으로 바뀐다
DROP TABLE clinic_change_requests;

-- from_*을 남기는 이유: 예약 행은 이미 바뀐 뒤라 원래 시각을 복원할 수 없다.
-- 선생님이 봐야 하는 건 "누가 언제서 언제로 옮겼는가"다.
--
-- reason_code는 없앴다. 옵션 목록이 미확정인 채 남아 있던 값인데, 학생이 직접 적기로
-- 확정되어 자유 텍스트 한 칸이면 된다. 다시 코드 컬럼을 만들지 마라.
CREATE TABLE clinic_change_logs (
    id                BIGSERIAL PRIMARY KEY,
    student_id        BIGINT      NOT NULL REFERENCES students(id),
    from_clinic_id    BIGINT      NOT NULL REFERENCES clinics(id),
    from_arrival_time TIME        NOT NULL,
    to_clinic_id      BIGINT      REFERENCES clinics(id),   -- NULL이면 취소
    to_arrival_time   TIME,                                 -- NULL이면 취소
    reason            TEXT        NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- 이동/시간변경이면 둘 다 있고, 취소면 둘 다 없다. 한쪽만 있는 행은 읽을 수 없다
    CONSTRAINT ck_clinic_change_logs_target
        CHECK ((to_clinic_id IS NULL) = (to_arrival_time IS NULL))
);

-- 선생님 화면은 최근 7일만 본다. 학생·학부모 화면은 본인 것만 본다
CREATE INDEX idx_clinic_change_logs_recent  ON clinic_change_logs(created_at DESC);
CREATE INDEX idx_clinic_change_logs_student ON clinic_change_logs(student_id, created_at DESC);
