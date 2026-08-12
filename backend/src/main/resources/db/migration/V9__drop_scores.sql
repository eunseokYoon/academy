-- ============================================================
-- 기존 성적 체계 제거
--
-- 성적이 주차별 4종(weekly_tests) + 정기고사(regular_exam_scores)로 바뀌었다.
-- scores는 시드·운영 데이터가 없어 이관할 것이 없다.
--
-- exam_schedules는 남긴다. 중간·기말 D-day 표시에 계속 쓰이고 성적과 무관하다.
-- ============================================================
DROP TABLE scores;

-- 온라인 테스트는 클리닉 테스트의 온라인 대체본이다. 성적 자동 반영은 없앤다.
-- 선생님이 결과(문항별 오답·내부/외부 집계)를 보고 성적 기입 탭에 직접 적는다.
ALTER TABLE online_tests DROP CONSTRAINT ck_online_tests_type;
ALTER TABLE online_tests DROP CONSTRAINT ck_online_tests_subject;
ALTER TABLE online_tests DROP COLUMN score_type;
ALTER TABLE online_tests DROP COLUMN subject;
