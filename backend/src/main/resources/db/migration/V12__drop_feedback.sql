-- 확인·피드백 단계 제거 (2026-08-09 확정)
--
-- GRID 재제출은 이제 학생이 제출하는 순간 result가 DONE이 된다. 선생님이 확인해서
-- ⭕로 올려 주던 단계가 통째로 없어졌고, 그 단계에 붙어 있던 피드백도 같이 없어진다.
-- 선생님은 T-7에서 사진·영상을 보기만 한다.
--
-- 되돌릴 수 없다. 작성된 피드백 본문이 사라지고 CHECKED 표시가 SUBMITTED로 접힌다.

DROP TABLE feedbacks;

-- 피드백 읽음 표시. 엔티티에 매핑되지 않은 채 남아 있던 컬럼이고, 이제 읽을 피드백 자체가 없다
ALTER TABLE submissions
    DROP COLUMN feedback_read_at;

-- CHECKED를 만드는 코드가 사라졌다. 값을 남겨 두면 기존 행만 특별 취급하는 분기가
-- 집계·정렬·화면 라벨에 영구히 남는다. submitted_at은 그대로라 "언제 냈는지"는 보존된다
UPDATE submissions
SET status = 'SUBMITTED'
WHERE status = 'CHECKED';

ALTER TABLE submissions
    DROP CONSTRAINT ck_submissions_status;

ALTER TABLE submissions
    ADD CONSTRAINT ck_submissions_status
        CHECK (status IN ('NOT_SUBMITTED', 'SUBMITTED'));
