-- ============================================================================
-- mock-data.sql 되돌리기. 목데이터만 역순으로 지운다.
--
-- 실행: psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f scripts/mock-data-rollback.sql
--
-- 식별 기준은 자연키다 — 반은 join_code(DS0001/DI0001), 계정은 전화번호 대역
-- 0103000xxxx, 클리닉은 memo. 이 값들을 바꿔서 넣었다면 여기도 같이 바꿔라.
-- ============================================================================

BEGIN;

-- 클리닉
DELETE FROM clinic_change_logs
WHERE from_clinic_id IN (SELECT id FROM clinics WHERE memo = '주간 클리닉 (목데이터)')
   OR to_clinic_id   IN (SELECT id FROM clinics WHERE memo = '주간 클리닉 (목데이터)')
   OR student_id     IN (SELECT s.id FROM students s JOIN users u ON u.id = s.user_id
                         WHERE u.login_id LIKE '010300000%');

DELETE FROM clinic_reservations
WHERE clinic_id IN (SELECT id FROM clinics WHERE memo = '주간 클리닉 (목데이터)')
   OR student_id IN (SELECT s.id FROM students s JOIN users u ON u.id = s.user_id
                     WHERE u.login_id LIKE '010300000%');

DELETE FROM clinics WHERE memo = '주간 클리닉 (목데이터)';

-- 성적
DELETE FROM weekly_test_scores
WHERE weekly_test_id IN (
    SELECT w.id FROM weekly_tests w JOIN class_rooms c ON c.id = w.class_room_id
    WHERE c.join_code IN ('DS0001', 'DI0001'));

DELETE FROM weekly_tests
WHERE class_room_id IN (SELECT id FROM class_rooms WHERE join_code IN ('DS0001', 'DI0001'));

DELETE FROM regular_exam_scores
WHERE student_id IN (SELECT s.id FROM students s JOIN users u ON u.id = s.user_id
                     WHERE u.login_id LIKE '010300000%');

-- 숙제
DELETE FROM submission_photos
WHERE submission_id IN (
    SELECT s2.id FROM submissions s2 JOIN homeworks h ON h.id = s2.homework_id
    JOIN class_rooms c ON c.id = h.class_room_id WHERE c.join_code IN ('DS0001', 'DI0001'));

DELETE FROM submissions
WHERE homework_id IN (
    SELECT h.id FROM homeworks h JOIN class_rooms c ON c.id = h.class_room_id
    WHERE c.join_code IN ('DS0001', 'DI0001'));

DELETE FROM homeworks
WHERE class_room_id IN (SELECT id FROM class_rooms WHERE join_code IN ('DS0001', 'DI0001'));

-- 출석 · 수업
DELETE FROM attendances
WHERE class_room_id IN (SELECT id FROM class_rooms WHERE join_code IN ('DS0001', 'DI0001'));

DELETE FROM lesson_change_requests
WHERE from_lesson_id IN (
    SELECT l.id FROM lessons l JOIN class_rooms c ON c.id = l.class_room_id
    WHERE c.join_code IN ('DS0001', 'DI0001'))
   OR to_lesson_id IN (
    SELECT l.id FROM lessons l JOIN class_rooms c ON c.id = l.class_room_id
    WHERE c.join_code IN ('DS0001', 'DI0001'));

DELETE FROM lessons
WHERE class_room_id IN (SELECT id FROM class_rooms WHERE join_code IN ('DS0001', 'DI0001'));

-- 반
DELETE FROM notices
WHERE class_room_id IN (SELECT id FROM class_rooms WHERE join_code IN ('DS0001', 'DI0001'))
   OR student_id IN (SELECT s.id FROM students s JOIN users u ON u.id = s.user_id
                     WHERE u.login_id LIKE '010300000%');

DELETE FROM materials
WHERE class_room_id IN (SELECT id FROM class_rooms WHERE join_code IN ('DS0001', 'DI0001'));

DELETE FROM exam_schedules
WHERE class_room_id IN (SELECT id FROM class_rooms WHERE join_code IN ('DS0001', 'DI0001'));

DELETE FROM online_test_submissions
WHERE online_test_id IN (
    SELECT o.id FROM online_tests o JOIN class_rooms c ON c.id = o.class_room_id
    WHERE c.join_code IN ('DS0001', 'DI0001'))
   OR student_id IN (SELECT s.id FROM students s JOIN users u ON u.id = s.user_id
                     WHERE u.login_id LIKE '010300000%');

DELETE FROM online_tests
WHERE class_room_id IN (SELECT id FROM class_rooms WHERE join_code IN ('DS0001', 'DI0001'));

DELETE FROM enrollments
WHERE class_room_id IN (SELECT id FROM class_rooms WHERE join_code IN ('DS0001', 'DI0001'));

DELETE FROM class_room_schedules
WHERE class_room_id IN (SELECT id FROM class_rooms WHERE join_code IN ('DS0001', 'DI0001'));

DELETE FROM class_rooms WHERE join_code IN ('DS0001', 'DI0001');

-- 계정. refresh_tokens는 revoke가 아니라 delete여야 users 삭제가 FK에 안 걸린다
DELETE FROM signup_codes
WHERE student_id IN (SELECT s.id FROM students s JOIN users u ON u.id = s.user_id
                     WHERE u.login_id LIKE '010300000%');

DELETE FROM refresh_tokens
WHERE user_id IN (SELECT id FROM users
                  WHERE login_id LIKE '010300000%' OR login_id LIKE '010300010%');

DELETE FROM students
WHERE user_id IN (SELECT id FROM users WHERE login_id LIKE '010300000%');

DELETE FROM parents
WHERE user_id IN (SELECT id FROM users WHERE login_id LIKE '010300010%');

DELETE FROM users
WHERE login_id LIKE '010300000%' OR login_id LIKE '010300010%';

COMMIT;
