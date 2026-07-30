-- 피드백 미확인 수 (S-1 홈의 unreadFeedbackCount)
--
-- 읽음 상태를 담을 곳이 없어서 컬럼을 하나 붙인다. 없으면 "미확인"을 계산할 근거가
-- 아예 없고, "최근 7일" 같은 대체 정의는 학생이 이미 본 피드백도 계속 세어서
-- 숫자가 줄지 않는다 — 줄지 않는 배지는 아무도 안 본다.
--
-- 별도 테이블(feedback_reads)로 만들지 않은 이유: 제출 1건당 피드백 1개이고
-- (feedbacks.submission_id UNIQUE), 읽는 사람도 그 제출물의 학생 1명뿐이다.
-- 조인 대상이 늘지 않는 쪽이 낫다.
--
-- 공지 읽음 표시(notice_reads)는 여전히 범위 밖이다. 이건 피드백 전용이다.
--
-- 비교는 f.updated_at과 한다. 선생님이 피드백을 고치면 다시 미확인이 되어야 한다.
--   WHERE s.feedback_read_at IS NULL OR s.feedback_read_at < f.updated_at

ALTER TABLE submissions
    ADD COLUMN feedback_read_at TIMESTAMPTZ;
