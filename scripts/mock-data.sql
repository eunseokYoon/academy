-- ============================================================================
-- 목데이터: 반 2개 · 학생 10명 · 학부모 10명 · 8주치 수업/출석/숙제/성적/클리닉
--
-- 대상: 서버 DB. ID를 하드코딩하지 않고 전부 자연키(join_code, login_id)로 찾는다.
-- 기존 데이터는 한 줄도 건드리지 않는다. 충돌이 있으면 맨 앞에서 통째로 중단한다.
--
-- 실행:  psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f scripts/mock-data.sql
-- 되돌리기: scripts/mock-data-rollback.sql
--
-- 기준일 2026-08-13(목). 이 날짜를 바꾸려면 아래 '오늘' 상수를 전부 같이 바꿔야 한다.
--   · lesson_date <  2026-08-13  → 지난 수업: 출석 확정 · 숙제 채점 · 성적 입력
--   · lesson_date =  2026-08-13  → 오늘 수업: PENDING · 숙제 열만 있고 미채점
--   · lesson_date >  2026-08-13  → 예정 수업: 초안(published_at NULL) · 출석/숙제 없음
-- ============================================================================

BEGIN;

-- ---------------------------------------------------------------------------
-- 0. 사전 검사. 하나라도 걸리면 아무것도 안 들어간다
-- ---------------------------------------------------------------------------
DO $guard$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM teachers t JOIN users u ON u.id = t.user_id
        WHERE u.login_id = '01000000000'
    ) THEN
        RAISE EXCEPTION '선생님 계정(login_id=01000000000)이 없다. 실제 강사 번호로 바꿔서 다시 실행해라.';
    END IF;

    IF EXISTS (SELECT 1 FROM class_rooms WHERE join_code IN ('DS0001', 'DI0001')) THEN
        RAISE EXCEPTION '가입 코드 DS0001/DI0001이 이미 쓰이고 있다.';
    END IF;

    IF EXISTS (
        SELECT 1 FROM class_rooms
        WHERE name IN ('동성고 1', '대일외고 1') AND status = 'ACTIVE'
    ) THEN
        RAISE EXCEPTION '같은 이름의 ACTIVE 반이 이미 있다 (uq_class_rooms_name).';
    END IF;

    IF EXISTS (
        SELECT 1 FROM users
        WHERE login_id LIKE '010300000%' OR login_id LIKE '010300010%'
    ) THEN
        RAISE EXCEPTION '전화번호 대역 0103000xxxx가 이미 쓰이고 있다.';
    END IF;
END
$guard$;

-- ---------------------------------------------------------------------------
-- 1. 반 (주 1회 수업)
--      동성고 1   목요일 18:00~20:00
--      대일외고 1 수요일 19:00~21:00
--    day_of_week는 ISO-8601이다. 1=월 ~ 7=일 (java.time.DayOfWeek.getValue()와 동일)
-- ---------------------------------------------------------------------------
INSERT INTO class_rooms (name, teacher_id, join_code, join_code_active, status, memo)
SELECT v.name, t.id, v.code, true, 'ACTIVE', '목데이터'
FROM (VALUES ('동성고 1', 'DS0001'), ('대일외고 1', 'DI0001')) AS v(name, code)
CROSS JOIN (
    SELECT t.id FROM teachers t JOIN users u ON u.id = t.user_id
    WHERE u.login_id = '01000000000'
) AS t;

INSERT INTO class_room_schedules (class_room_id, day_of_week, start_time, end_time)
SELECT c.id, v.dow, v.start_time, v.end_time
FROM (VALUES
    ('DS0001', 4::smallint, TIME '18:00', TIME '20:00'),
    ('DI0001', 3::smallint, TIME '19:00', TIME '21:00')
) AS v(code, dow, start_time, end_time)
JOIN class_rooms c ON c.join_code = v.code;

-- ---------------------------------------------------------------------------
-- 2. 계정
--    학생   010-3000-0001 ~ 0010   (1~5번 동성고, 6~10번 대일외고)
--    학부모 010-3000-1001 ~ 1010   (학생 n번의 부모가 1000+n번)
--    비밀번호는 전원 0000. must_change_password는 false다 —
--    true면 로그인 직후 비밀번호 변경 화면에 갇혀서 아무것도 못 본다.
-- ---------------------------------------------------------------------------
INSERT INTO users (role, login_id, password_hash, name, phone, status, must_change_password)
SELECT 'STUDENT',
       '0103000' || lpad(n::text, 4, '0'),
       '$2a$10$bpAuBat3V6nK6YLWA6.Jhe1eIFo7zRFht1BFt4ZxUYodFsZ7BPU6y',   -- BCrypt('0000')
       nm,
       '0103000' || lpad(n::text, 4, '0'),
       'ACTIVE', false
FROM unnest(ARRAY[
    '강민준', '김서연', '박지호', '이하은', '정우진',
    '문가온', '배시우', '신예린', '오태경', '한소미'
]) WITH ORDINALITY AS a(nm, n);

INSERT INTO users (role, login_id, password_hash, name, phone, status, must_change_password)
SELECT 'PARENT',
       '0103000' || lpad((1000 + n)::text, 4, '0'),
       '$2a$10$bpAuBat3V6nK6YLWA6.Jhe1eIFo7zRFht1BFt4ZxUYodFsZ7BPU6y',
       nm || ' 학부모',
       '0103000' || lpad((1000 + n)::text, 4, '0'),
       'ACTIVE', false
FROM unnest(ARRAY[
    '강민준', '김서연', '박지호', '이하은', '정우진',
    '문가온', '배시우', '신예린', '오태경', '한소미'
]) WITH ORDINALITY AS a(nm, n);

INSERT INTO parents (user_id)
SELECT id FROM users WHERE login_id LIKE '010300010%';

-- students.name이 정본이다. users.name이 아니다 (미가입 학생은 users 행이 없다)
INSERT INTO students (name, user_id, parent_id, status, memo)
SELECT su.name, su.id, p.id, 'ENROLLED', '목데이터'
FROM users su
JOIN users pu ON pu.login_id = '0103000' || lpad((1000 + right(su.login_id, 4)::int)::text, 4, '0')
JOIN parents p ON p.user_id = pu.id
WHERE su.login_id LIKE '010300000%';

-- 첫 수업(7/1)보다 앞이어야 findActiveStudents에 잡힌다
INSERT INTO enrollments (student_id, class_room_id, joined_at)
SELECT s.id, c.id, DATE '2026-06-22'
FROM students s
JOIN users u ON u.id = s.user_id
JOIN class_rooms c
  ON c.join_code = CASE WHEN right(u.login_id, 4)::int <= 5 THEN 'DS0001' ELSE 'DI0001' END
WHERE u.login_id LIKE '010300000%';

-- ---------------------------------------------------------------------------
-- 3. 수업 — 2026-07-01 ~ 2026-08-21, 반당 8회 (주차 하나당 정확히 한 회)
--    year/month/week는 MonthWeeks 규칙이다: 달 1일부터 7일씩 끊어 1~5주차
-- ---------------------------------------------------------------------------
INSERT INTO lessons (class_room_id, lesson_date, year, month, week,
                     title, content, key_points, next_preview,
                     attendance_status, attendance_confirmed_at, attendance_confirmed_by,
                     published_at)
SELECT x.class_room_id,
       x.d,
       2026::smallint,
       extract(month from x.d)::smallint,
       ((extract(day from x.d)::int - 1) / 7 + 1)::smallint,
       (ARRAY['빈칸 추론 집중', '어법 포인트 정리', '주제·요지 파악', '순서 배열 훈련',
              '문장 삽입 훈련', '어휘 추론 훈련', '장문 독해', '실전 모의고사 리뷰'])[x.wk + 1],
       (ARRAY['빈칸 앞뒤 연결어를 먼저 잡고 선택지를 지운다.',
              '분사구문과 관계사 절을 문장에서 분리해 본다.',
              '첫 문장과 마지막 문장만으로 요지를 세운다.',
              '지시어와 연결어로 순서를 고정한다.',
              '주어진 문장의 대명사가 가리키는 것을 찾는다.',
              '문맥상 반대말이 들어간 자리를 표시한다.',
              '단락별 한 줄 요약을 먼저 적는다.',
              '틀린 문항의 오답 근거를 문장으로 쓴다.'])[x.wk + 1],
       (ARRAY['연결어 정리표', '분사구문 3형태', '요지 = 반복되는 명사구', '지시어 체크리스트',
              '대명사 지시 대상', '문맥 반의어 20', '단락 요약 5줄', '오답 근거 쓰기'])[x.wk + 1],
       CASE WHEN x.wk < 7
            THEN (ARRAY['어법 포인트 정리', '주제·요지 파악', '순서 배열 훈련', '문장 삽입 훈련',
                        '어휘 추론 훈련', '장문 독해', '실전 모의고사 리뷰'])[x.wk + 1]
       END,
       CASE WHEN x.d < DATE '2026-08-13' THEN 'CONFIRMED' ELSE 'PENDING' END,
       CASE WHEN x.d < DATE '2026-08-13'
            THEN (x.d + TIME '21:30') AT TIME ZONE 'Asia/Seoul' END,
       CASE WHEN x.d < DATE '2026-08-13' THEN tt.tid END,
       CASE WHEN x.d <= DATE '2026-08-13'
            THEN (x.d + TIME '09:00') AT TIME ZONE 'Asia/Seoul' END
FROM (
    SELECT c.id AS class_room_id,
           g.d::date AS d,
           ((g.d::date - DATE '2026-07-01') / 7)::int AS wk    -- 0~7. 두 반 모두 주차와 1:1
    FROM (VALUES ('DS0001', 4), ('DI0001', 3)) AS v(code, dow)
    JOIN class_rooms c ON c.join_code = v.code
    CROSS JOIN generate_series(DATE '2026-07-01', DATE '2026-08-21', INTERVAL '1 day') AS g(d)
    WHERE extract(isodow from g.d) = v.dow
) x
CROSS JOIN (
    SELECT t.id AS tid FROM teachers t JOIN users u ON u.id = t.user_id
    WHERE u.login_id = '01000000000'
) tt;

-- ---------------------------------------------------------------------------
-- 4. 출석 — 확정된 수업만. PENDING인 날은 행을 만들지 않는다
--    (출석 확정 시 재원생 전원의 attendances를 한 번에 까는 게 정상 흐름이다)
--    분포: 결석 5% · 지각 10% · 병결 5% · 공결 5% · 나머지 출석.
--    해시에 날짜 차이를 그대로 쓰면 안 된다 — 수업이 주 1회라 차이가 전부 7의 배수여서
--    나머지가 몇 개 값으로 뭉치고 SICK·EXCUSED가 한 건도 안 나온다. 주차 번호를 쓴다
-- ---------------------------------------------------------------------------
INSERT INTO attendances (lesson_id, class_room_id, student_id, attend_date, status,
                         checked_by, checked_at)
SELECT l.id, l.class_room_id, s.id, l.lesson_date,
       CASE (right(u.login_id, 4)::int * 13
             + ((l.lesson_date - DATE '2026-07-01') / 7) * 9) % 20
           WHEN 0 THEN 'ABSENT'
           WHEN 1 THEN 'LATE'
           WHEN 2 THEN 'LATE'
           WHEN 3 THEN 'SICK'
           WHEN 4 THEN 'EXCUSED'
           ELSE 'PRESENT'
       END,
       tt.tid,
       l.attendance_confirmed_at
FROM lessons l
JOIN class_rooms c ON c.id = l.class_room_id AND c.join_code IN ('DS0001', 'DI0001')
JOIN enrollments e ON e.class_room_id = l.class_room_id
                  AND e.joined_at <= l.lesson_date
                  AND (e.left_at IS NULL OR e.left_at > l.lesson_date)
JOIN students s ON s.id = e.student_id AND s.status = 'ENROLLED'
JOIN users u ON u.id = s.user_id
CROSS JOIN (
    SELECT t.id AS tid FROM teachers t JOIN users u2 ON u2.id = t.user_id
    WHERE u2.login_id = '01000000000'
) tt
WHERE l.attendance_status = 'CONFIRMED';

-- ---------------------------------------------------------------------------
-- 5. 숙제 — GRID 열. 수업 1회당 2열, 지난 수업 + 오늘 수업까지만 만든다
--    GRID의 due_at은 "재제출 마감"이다. 열을 만들 때는 NULL이고,
--    선생님이 재제출을 요청한 열에만 값이 들어간다 → 가장 최근 채점 회차 2열에만 채운다
-- ---------------------------------------------------------------------------
WITH lsn AS (
    SELECT l.id, l.class_room_id, l.lesson_date,
           ((l.lesson_date - DATE '2026-07-01') / 7)::int AS wk,
           l.lesson_date = max(l.lesson_date) FILTER (WHERE l.lesson_date < DATE '2026-08-13')
                                              OVER (PARTITION BY l.class_room_id) AS is_latest_graded
    FROM lessons l
    JOIN class_rooms c ON c.id = l.class_room_id AND c.join_code IN ('DS0001', 'DI0001')
)
INSERT INTO homeworks (class_room_id, lesson_id, teacher_id, title, description,
                       kind, sort_order, due_at)
SELECT lsn.class_room_id, lsn.id, tt.tid,
       CASE col.ord
           WHEN 1 THEN '단어 암기 Day ' || (5 * lsn.wk + 1) || '-' || (5 * lsn.wk + 5)
           ELSE '워크북 p.' || (8 * lsn.wk + 10) || '-' || (8 * lsn.wk + 17)
       END,
       CASE col.ord
           WHEN 1 THEN '다음 수업 시작 전 단어 시험을 본다.'
           ELSE '틀린 문항은 오답 근거를 여백에 적어 온다.'
       END,
       'GRID',
       col.ord::smallint,
       CASE WHEN lsn.is_latest_graded
            THEN ((lsn.lesson_date + 14) + TIME '23:59') AT TIME ZONE 'Asia/Seoul' END
FROM lsn
CROSS JOIN (VALUES (1), (2)) AS col(ord)
CROSS JOIN (
    SELECT t.id AS tid FROM teachers t JOIN users u ON u.id = t.user_id
    WHERE u.login_id = '01000000000'
) tt
WHERE lsn.lesson_date <= DATE '2026-08-13';

-- 대상 전원의 칸을 미리 깐다. 칸 = submissions 한 행이고 축이 둘이다:
--   result = 오프라인 채점(⭕🔺❌), status = 온라인 제출.
--   ⭕ 받은 학생은 온라인 제출을 안 하므로 status가 NOT_SUBMITTED로 남는 게 정상이다.
-- 분포: ⭕ 70% · 🔺 20%(1~99%) · ❌ 10%. 오늘 수업 열은 전부 미채점(NULL)
INSERT INTO submissions (homework_id, student_id, status, result, completion_rate)
SELECT h.id, s.id, 'NOT_SUBMITTED',
       CASE WHEN l.lesson_date >= DATE '2026-08-13' THEN NULL
            WHEN hh.v = 0      THEN 'NOT_DONE'
            WHEN hh.v IN (1,2) THEN 'PARTIAL'
            ELSE 'DONE'
       END,
       CASE WHEN l.lesson_date < DATE '2026-08-13' AND hh.v IN (1,2)
            THEN (30 + (right(u.login_id, 4)::int * 7 + hh.v * 13) % 60)::smallint
       END
FROM homeworks h
JOIN lessons l ON l.id = h.lesson_id
JOIN class_rooms c ON c.id = h.class_room_id AND c.join_code IN ('DS0001', 'DI0001')
JOIN enrollments e ON e.class_room_id = h.class_room_id
                  AND e.joined_at <= l.lesson_date
                  AND (e.left_at IS NULL OR e.left_at > l.lesson_date)
JOIN students s ON s.id = e.student_id AND s.status = 'ENROLLED'
JOIN users u ON u.id = s.user_id
CROSS JOIN LATERAL (
    SELECT (right(u.login_id, 4)::int * 17
            + ((l.lesson_date - DATE '2026-07-01') / 7) * 5
            + h.sort_order * 3) % 10 AS v
) hh
WHERE h.kind = 'GRID';

-- 재제출을 실제로 낸 학생 1명씩. 학생이 내는 순간 ⭕가 되고 재제출 대상에서 빠진다
-- (선생님 확인 단계는 없다). status는 SUBMITTED로 둬야 선생님이 T-7에서 볼 수 있다.
UPDATE submissions sub
SET status                   = 'SUBMITTED',
    submitted_at             = TIMESTAMPTZ '2026-08-12 20:10+09',
    result                   = 'DONE',
    completion_rate          = NULL,
    resolved_by_resubmission = true,
    updated_at               = TIMESTAMPTZ '2026-08-12 20:10+09'
WHERE sub.id IN (
    SELECT DISTINCT ON (h.class_room_id) s2.id
    FROM submissions s2
    JOIN homeworks h ON h.id = s2.homework_id
    JOIN class_rooms c ON c.id = h.class_room_id AND c.join_code IN ('DS0001', 'DI0001')
    WHERE h.kind = 'GRID'
      AND h.due_at IS NOT NULL
      AND s2.result IN ('PARTIAL', 'NOT_DONE')
    ORDER BY h.class_room_id, s2.id
);

-- ---------------------------------------------------------------------------
-- 6. 성적 — 주차별 4종. 헤더(weekly_tests)는 8주차 전부, 셀은 지난 수업 주차만.
--    REVIEW는 헤더에 채울 값이 없어서 셀이 있는 주차에만 만든다(서비스 규칙과 동일).
--    → 동성고 1은 8월 2주(오늘 수업)·3주가 빈 그리드, 대일외고 1은 8월 3주가 빈 그리드
-- ---------------------------------------------------------------------------
INSERT INTO weekly_tests (class_room_id, test_type, year, month, week,
                          total_count, internal_total, external_total)
SELECT l.class_room_id, tt.test_type, l.year, l.month, l.week,
       CASE tt.test_type WHEN 'WORD' THEN 30::smallint
                         WHEN 'PRACTICE' THEN 20::smallint END,
       CASE tt.test_type WHEN 'CLINIC' THEN 15::smallint END,
       CASE tt.test_type WHEN 'CLINIC' THEN 10::smallint END
FROM lessons l
JOIN class_rooms c ON c.id = l.class_room_id AND c.join_code IN ('DS0001', 'DI0001')
CROSS JOIN (VALUES ('WORD'), ('PRACTICE'), ('CLINIC')) AS tt(test_type)
UNION ALL
SELECT l.class_room_id, 'REVIEW', l.year, l.month, l.week, NULL, NULL, NULL
FROM lessons l
JOIN class_rooms c ON c.id = l.class_room_id AND c.join_code IN ('DS0001', 'DI0001')
WHERE l.lesson_date < DATE '2026-08-13';

-- 셀은 선생님이 채운 것만 행이 생긴다. 미리 깔지 않는다(숙제·출석과 반대 방향).
-- 환산 점수는 저장하지 않는다 — 맞힌 개수만 두고 정답률은 조회 시점에 계산한다.
INSERT INTO weekly_test_scores (weekly_test_id, student_id,
                                correct_count, internal_correct, external_correct,
                                result, retest_passed)
SELECT wt.id, s.id,
       CASE wt.test_type
           WHEN 'WORD'     THEN floor(30 * m.pct  / 100.0)::smallint
           WHEN 'PRACTICE' THEN floor(20 * m.pct  / 100.0)::smallint
       END,
       CASE wt.test_type WHEN 'CLINIC' THEN floor(15 * m.pct  / 100.0)::smallint END,
       CASE wt.test_type WHEN 'CLINIC' THEN floor(10 * m.pct2 / 100.0)::smallint END,
       -- Pass/Fail은 맞힌 개수로 자동 판정되는 값이 아니다. 선생님이 오프라인 채점 후
       -- 직접 고른다. 여기서는 그럴듯한 값을 만들려고 점수와 느슨하게 묶어 둘 뿐이다
       CASE wt.test_type
           WHEN 'WORD'   THEN CASE WHEN floor(30 * m.pct / 100.0) >= 21 THEN 'PASS' ELSE 'FAIL' END
           WHEN 'REVIEW' THEN CASE WHEN m.pct >= 65 THEN 'PASS' ELSE 'FAIL' END
       END,
       CASE wt.test_type
           WHEN 'WORD'   THEN floor(30 * m.pct / 100.0) < 21 AND (m.seq + m.widx) % 2 = 0
           WHEN 'REVIEW' THEN m.pct < 65                     AND (m.seq + m.widx) % 3 = 0
           ELSE false
       END
FROM weekly_tests wt
JOIN class_rooms c ON c.id = wt.class_room_id AND c.join_code IN ('DS0001', 'DI0001')
JOIN lessons l ON l.class_room_id = wt.class_room_id
              AND l.year = wt.year AND l.month = wt.month AND l.week = wt.week
JOIN enrollments e ON e.class_room_id = wt.class_room_id
                  AND e.joined_at <= l.lesson_date
                  AND (e.left_at IS NULL OR e.left_at > l.lesson_date)
JOIN students s ON s.id = e.student_id AND s.status = 'ENROLLED'
JOIN users u ON u.id = s.user_id
CROSS JOIN LATERAL (
    SELECT right(u.login_id, 4)::int AS seq,
           CASE wt.month WHEN 7 THEN wt.week ELSE 5 + wt.week END AS widx
) k
CROSS JOIN LATERAL (
    SELECT k.seq, k.widx,
           -- 학생별 기본 실력(68~92, 10명 전부 다른 값) + 주차별 흔들림(-10~+10).
           -- 곱수와 모듈러가 서로소여야 한다. gcd가 1이 아니면 값이 몇 개로 뭉친다
           greatest(50, least(100, (66 + (k.seq * 11) % 31) + (((k.seq * 11 + k.widx * 13) % 21) - 10))) AS pct,
           greatest(50, least(100, (66 + (k.seq * 11) % 31) - (((k.seq * 11 + k.widx * 13) % 21) - 10))) AS pct2
) m
WHERE l.lesson_date < DATE '2026-08-13';

-- ---------------------------------------------------------------------------
-- 7. 클리닉 — 매주 금요일 17:00~22:00 한 타임. 정원은 미확정이라 NULL(제한 없음).
--    도착 시각 슬롯은 Clinic.slots() 규칙이다: 17·18·19·20·21시 (마지막은 종료 1시간 전)
--    clinics는 반과 무관하다. uq_clinics_slot이 (날짜, 시작시각) 전역 유니크다
-- ---------------------------------------------------------------------------
INSERT INTO clinics (teacher_id, clinic_date, start_time, end_time, capacity, status, memo)
SELECT tt.tid, g.d::date, TIME '17:00', TIME '22:00', NULL::smallint, 'OPEN', '주간 클리닉 (목데이터)'
FROM generate_series(DATE '2026-07-03', DATE '2026-08-21', INTERVAL '7 day') AS g(d)
CROSS JOIN (
    SELECT t.id AS tid FROM teachers t JOIN users u ON u.id = t.user_id
    WHERE u.login_id = '01000000000'
) tt;

-- 학생당 주 1회. 10명이 슬롯 5개에 2명씩 흩어지고, 주마다 슬롯이 한 칸씩 밀린다.
-- 지난 클리닉은 출결 확정, 8/14·8/21은 예약만 있고 attend_status가 NULL이다
INSERT INTO clinic_reservations (clinic_id, student_id, assigned_by, status, arrival_time,
                                 attend_status, checked_by, checked_at)
SELECT cl.id, s.id, NULL, 'RESERVED',
       (TIME '17:00' + (((k.seq + k.widx) % 5) * INTERVAL '1 hour'))::time,
       CASE WHEN cl.clinic_date < DATE '2026-08-13'
            THEN CASE (k.seq * 5 + k.widx * 3) % 12
                     WHEN 0 THEN 'ABSENT'
                     WHEN 1 THEN 'LATE'
                     ELSE 'PRESENT'
                 END
       END,
       CASE WHEN cl.clinic_date < DATE '2026-08-13' THEN tt.tid END,
       CASE WHEN cl.clinic_date < DATE '2026-08-13'
            THEN (cl.clinic_date + TIME '22:10') AT TIME ZONE 'Asia/Seoul' END
FROM clinics cl
CROSS JOIN students s
JOIN users u ON u.id = s.user_id
CROSS JOIN LATERAL (
    SELECT right(u.login_id, 4)::int AS seq,
           ((cl.clinic_date - DATE '2026-07-03') / 7)::int + 1 AS widx
) k
CROSS JOIN (
    SELECT t.id AS tid FROM teachers t JOIN users u2 ON u2.id = t.user_id
    WHERE u2.login_id = '01000000000'
) tt
WHERE cl.memo = '주간 클리닉 (목데이터)'
  AND u.login_id LIKE '010300000%';

COMMIT;

-- ---------------------------------------------------------------------------
-- 확인
-- ---------------------------------------------------------------------------
SELECT '반' AS 항목, count(*) FROM class_rooms WHERE join_code IN ('DS0001','DI0001')
UNION ALL SELECT '학생', count(*) FROM users WHERE login_id LIKE '010300000%'
UNION ALL SELECT '학부모', count(*) FROM users WHERE login_id LIKE '010300010%'
UNION ALL SELECT '수업', count(*) FROM lessons l JOIN class_rooms c ON c.id=l.class_room_id
          WHERE c.join_code IN ('DS0001','DI0001')
UNION ALL SELECT '출석', count(*) FROM attendances a JOIN class_rooms c ON c.id=a.class_room_id
          WHERE c.join_code IN ('DS0001','DI0001')
UNION ALL SELECT '숙제 열', count(*) FROM homeworks h JOIN class_rooms c ON c.id=h.class_room_id
          WHERE c.join_code IN ('DS0001','DI0001')
UNION ALL SELECT '숙제 칸', count(*) FROM submissions s2 JOIN homeworks h ON h.id=s2.homework_id
          JOIN class_rooms c ON c.id=h.class_room_id WHERE c.join_code IN ('DS0001','DI0001')
UNION ALL SELECT '성적 열', count(*) FROM weekly_tests w JOIN class_rooms c ON c.id=w.class_room_id
          WHERE c.join_code IN ('DS0001','DI0001')
UNION ALL SELECT '성적 칸', count(*) FROM weekly_test_scores ws JOIN weekly_tests w ON w.id=ws.weekly_test_id
          JOIN class_rooms c ON c.id=w.class_room_id WHERE c.join_code IN ('DS0001','DI0001')
UNION ALL SELECT '클리닉', count(*) FROM clinics WHERE memo = '주간 클리닉 (목데이터)'
UNION ALL SELECT '클리닉 예약', count(*) FROM clinic_reservations r
          JOIN clinics cl ON cl.id=r.clinic_id WHERE cl.memo = '주간 클리닉 (목데이터)';
