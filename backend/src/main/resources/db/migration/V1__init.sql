-- 학원 관리 서비스 초기 스키마 (24개 테이블)
-- 이 파일은 한 번 적용된 뒤에는 절대 수정하지 않는다. 변경은 항상 새 V{n} 파일로.

-- ============================================================
-- 1. 계정
-- ============================================================

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    role          VARCHAR(20)  NOT NULL,
    login_id      VARCHAR(20)  NOT NULL UNIQUE,   -- 전화번호(하이픈 없는 숫자)
    password_hash VARCHAR(100) NOT NULL,
    name          VARCHAR(50)  NOT NULL,
    phone         VARCHAR(20)  NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_users_role   CHECK (role IN ('TEACHER','STUDENT','PARENT')),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE','INACTIVE'))
);

CREATE TABLE teachers (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL UNIQUE REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE parents (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL UNIQUE REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE students (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(50) NOT NULL,                      -- 학생 이름. users와 무관하게 항상 존재
    user_id      BIGINT      UNIQUE REFERENCES users(id),   -- 회원가입 전에는 NULL
    parent_id    BIGINT      REFERENCES parents(id),
    status       VARCHAR(20) NOT NULL DEFAULT 'ENROLLED',
    withdrawn_at DATE,
    memo         TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_students_status CHECK (status IN ('ENROLLED','WITHDRAWN'))
);

CREATE INDEX idx_students_parent ON students(parent_id);

CREATE TABLE signup_codes (
    id          BIGSERIAL PRIMARY KEY,
    student_id  BIGINT      NOT NULL REFERENCES students(id),
    target_role VARCHAR(20) NOT NULL,          -- STUDENT | PARENT
    code        VARCHAR(10) NOT NULL UNIQUE,
    phone       VARCHAR(20) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_signup_codes_role CHECK (target_role IN ('STUDENT','PARENT'))
);

CREATE INDEX idx_signup_codes_student ON signup_codes(student_id, target_role);

-- ============================================================
-- 2. 반 · 수강
-- ============================================================

CREATE TABLE class_rooms (
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(100) NOT NULL,
    teacher_id       BIGINT       NOT NULL REFERENCES teachers(id),
    join_code        VARCHAR(10)  NOT NULL UNIQUE,   -- 반 가입 코드. 학생이 회원가입 시 입력
    join_code_active BOOLEAN      NOT NULL DEFAULT true,
    day_of_week      SMALLINT,
    start_time       TIME,
    term_start       DATE,
    term_end         DATE,
    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    memo             TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_class_rooms_status CHECK (status IN ('ACTIVE','CLOSED')),
    CONSTRAINT ck_class_rooms_dow    CHECK (day_of_week BETWEEN 1 AND 7)
);

CREATE UNIQUE INDEX uq_class_rooms_name
    ON class_rooms(name) WHERE status = 'ACTIVE';

CREATE TABLE enrollments (
    id            BIGSERIAL PRIMARY KEY,
    student_id    BIGINT      NOT NULL REFERENCES students(id),
    class_room_id BIGINT      NOT NULL REFERENCES class_rooms(id),
    joined_at     DATE        NOT NULL,
    left_at       DATE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_enrollments_active
    ON enrollments(student_id, class_room_id) WHERE left_at IS NULL;
CREATE INDEX idx_enrollments_class ON enrollments(class_room_id, left_at);
CREATE INDEX idx_enrollments_student ON enrollments(student_id);

-- ============================================================
-- 3. 수업 · 시청 기록
-- ============================================================

CREATE TABLE lessons (
    id                      BIGSERIAL PRIMARY KEY,
    class_room_id           BIGINT      NOT NULL REFERENCES class_rooms(id),
    lesson_date             DATE        NOT NULL,
    year                    SMALLINT    NOT NULL,
    month                   SMALLINT    NOT NULL,
    week                    SMALLINT    NOT NULL,
    title                   VARCHAR(200),
    video_url               VARCHAR(500),
    content                 TEXT,
    key_points              TEXT,
    next_preview            TEXT,
    attendance_status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attendance_confirmed_at TIMESTAMPTZ,
    attendance_confirmed_by BIGINT      REFERENCES teachers(id),
    published_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_lessons_att_status CHECK (attendance_status IN ('PENDING','CONFIRMED')),
    CONSTRAINT ck_lessons_month CHECK (month BETWEEN 1 AND 12),
    CONSTRAINT ck_lessons_week  CHECK (week  BETWEEN 1 AND 5)
);

CREATE UNIQUE INDEX uq_lessons ON lessons(class_room_id, lesson_date);
CREATE INDEX idx_lessons_date ON lessons(lesson_date);
CREATE INDEX idx_lessons_week ON lessons(year, month, week, class_room_id);

CREATE TABLE lesson_views (
    id              BIGSERIAL PRIMARY KEY,
    lesson_id       BIGINT      NOT NULL REFERENCES lessons(id),
    student_id      BIGINT      NOT NULL REFERENCES students(id),
    first_viewed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_viewed_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    watch_seconds   INT         NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_lesson_views ON lesson_views(lesson_id, student_id);

-- ============================================================
-- 4. 출석
-- ============================================================

CREATE TABLE attendances (
    id            BIGSERIAL PRIMARY KEY,
    lesson_id     BIGINT      REFERENCES lessons(id),
    class_room_id BIGINT      NOT NULL REFERENCES class_rooms(id),
    student_id    BIGINT      NOT NULL REFERENCES students(id),
    attend_date   DATE        NOT NULL,
    status        VARCHAR(20) NOT NULL,
    memo          TEXT,
    checked_by    BIGINT      REFERENCES teachers(id),
    checked_at    TIMESTAMPTZ,
    updated_by    BIGINT      REFERENCES teachers(id),
    updated_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_attendances_status
        CHECK (status IN ('PRESENT','LATE','ABSENT','SICK','EXCUSED'))
);

CREATE UNIQUE INDEX uq_attendances ON attendances(student_id, lesson_id);
CREATE INDEX idx_attendances_calendar ON attendances(student_id, attend_date);
CREATE INDEX idx_attendances_lesson ON attendances(lesson_id);

-- ============================================================
-- 5. 숙제
-- ============================================================

CREATE TABLE homework_templates (
    id          BIGSERIAL PRIMARY KEY,
    teacher_id  BIGINT       NOT NULL REFERENCES teachers(id),
    title       VARCHAR(200) NOT NULL,
    description TEXT,
    use_count   INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE homeworks (
    id            BIGSERIAL PRIMARY KEY,
    class_room_id BIGINT       NOT NULL REFERENCES class_rooms(id),
    lesson_id     BIGINT       REFERENCES lessons(id),
    teacher_id    BIGINT       NOT NULL REFERENCES teachers(id),
    title         VARCHAR(200) NOT NULL,
    description   TEXT,
    due_at        TIMESTAMPTZ  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_homeworks_class ON homeworks(class_room_id, due_at DESC);
CREATE INDEX idx_homeworks_lesson ON homeworks(lesson_id);

CREATE TABLE submissions (
    id           BIGSERIAL PRIMARY KEY,
    homework_id  BIGINT      NOT NULL REFERENCES homeworks(id),
    student_id   BIGINT      NOT NULL REFERENCES students(id),
    status       VARCHAR(20) NOT NULL DEFAULT 'NOT_SUBMITTED',
    submitted_at TIMESTAMPTZ,
    is_late      BOOLEAN     NOT NULL DEFAULT false,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_submissions_status
        CHECK (status IN ('NOT_SUBMITTED','SUBMITTED','CHECKED'))
);

CREATE UNIQUE INDEX uq_submissions ON submissions(homework_id, student_id);
CREATE INDEX idx_submissions_status ON submissions(homework_id, status);
CREATE INDEX idx_submissions_student ON submissions(student_id, created_at DESC);

CREATE TABLE submission_photos (
    id            BIGSERIAL PRIMARY KEY,
    submission_id BIGINT       NOT NULL REFERENCES submissions(id) ON DELETE CASCADE,
    s3_key        VARCHAR(500) NOT NULL,
    sort_order    SMALLINT     NOT NULL,
    bytes         INT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_submission_photos ON submission_photos(submission_id, sort_order);

CREATE TABLE feedbacks (
    id            BIGSERIAL PRIMARY KEY,
    submission_id BIGINT      NOT NULL UNIQUE REFERENCES submissions(id) ON DELETE CASCADE,
    teacher_id    BIGINT      NOT NULL REFERENCES teachers(id),
    content       TEXT        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================================
-- 6. 시험 일정 · 성적
-- ============================================================

CREATE TABLE exam_schedules (
    id            BIGSERIAL PRIMARY KEY,
    class_room_id BIGINT      NOT NULL REFERENCES class_rooms(id),
    year          SMALLINT    NOT NULL,
    semester      SMALLINT    NOT NULL,
    exam_type     VARCHAR(20) NOT NULL,
    start_date    DATE        NOT NULL,
    end_date      DATE        NOT NULL,
    scope_note    TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_exam_type     CHECK (exam_type IN ('MIDTERM','FINAL')),
    CONSTRAINT ck_exam_semester CHECK (semester IN (1,2))
);

CREATE UNIQUE INDEX uq_exam_schedules
    ON exam_schedules(class_room_id, year, semester, exam_type);
CREATE INDEX idx_exam_schedules_lookup
    ON exam_schedules(class_room_id, start_date);

CREATE TABLE scores (
    id               BIGSERIAL PRIMARY KEY,
    student_id       BIGINT       NOT NULL REFERENCES students(id),
    score_type       VARCHAR(20)  NOT NULL,
    exam_schedule_id BIGINT       REFERENCES exam_schedules(id),
    exam_name        VARCHAR(100) NOT NULL,
    subject          VARCHAR(50)  NOT NULL,
    raw_score        NUMERIC(5,2),
    grade_level      SMALLINT,                     -- 성적 등급 1~9. 학년이 아니다
    exam_date        DATE         NOT NULL,
    year             SMALLINT     NOT NULL,
    month            SMALLINT     NOT NULL,
    week             SMALLINT     NOT NULL,
    memo             TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_scores_type  CHECK (score_type IN ('WORD','INTERNAL','MOCK')),
    CONSTRAINT ck_scores_grade CHECK (grade_level BETWEEN 1 AND 9),
    CONSTRAINT ck_scores_month CHECK (month BETWEEN 1 AND 12),
    CONSTRAINT ck_scores_week  CHECK (week  BETWEEN 1 AND 5)
);

CREATE UNIQUE INDEX uq_scores
    ON scores(student_id, score_type, subject, exam_name, exam_date);
CREATE INDEX idx_scores_student ON scores(student_id, exam_date DESC);
CREATE INDEX idx_scores_week ON scores(student_id, score_type, year, month, week);

-- ============================================================
-- 7. 자료실 · 공지
-- ============================================================

CREATE TABLE materials (
    id            BIGSERIAL PRIMARY KEY,
    title         VARCHAR(200) NOT NULL,
    category      VARCHAR(20)  NOT NULL,
    s3_key        VARCHAR(500) NOT NULL,
    file_name     VARCHAR(255) NOT NULL,
    bytes         BIGINT,
    class_room_id BIGINT       REFERENCES class_rooms(id),
    visibility    VARCHAR(20)  NOT NULL DEFAULT 'CLASS',
    year          SMALLINT     NOT NULL,
    month         SMALLINT     NOT NULL,
    week          SMALLINT     NOT NULL,
    uploaded_by   BIGINT       NOT NULL REFERENCES teachers(id),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_materials_category
        CHECK (category IN ('LESSON','TEXTBOOK','PAST_EXAM','ETC')),
    CONSTRAINT ck_materials_visibility
        CHECK (visibility IN ('PUBLIC','CLASS')),
    CONSTRAINT ck_materials_month CHECK (month BETWEEN 1 AND 12),
    CONSTRAINT ck_materials_week  CHECK (week  BETWEEN 1 AND 5),
    -- CLASS면 반이 있어야 하고, PUBLIC이면 없어야 한다
    CONSTRAINT ck_materials_scope CHECK (
        (visibility = 'CLASS'  AND class_room_id IS NOT NULL) OR
        (visibility = 'PUBLIC' AND class_room_id IS NULL))
);

CREATE INDEX idx_materials_scope
    ON materials(class_room_id, category, created_at DESC);
CREATE INDEX idx_materials_week
    ON materials(year, month, week, class_room_id);

CREATE TABLE notices (
    id            BIGSERIAL PRIMARY KEY,
    title         VARCHAR(200) NOT NULL,
    content       TEXT         NOT NULL,
    scope         VARCHAR(20)  NOT NULL,
    class_room_id BIGINT       REFERENCES class_rooms(id),
    pinned        BOOLEAN      NOT NULL DEFAULT false,
    published_at  TIMESTAMPTZ,
    created_by    BIGINT       NOT NULL REFERENCES teachers(id),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_notices_scope CHECK (scope IN ('ALL','CLASS')),
    CONSTRAINT ck_notices_target CHECK (
        (scope = 'CLASS' AND class_room_id IS NOT NULL) OR
        (scope = 'ALL'   AND class_room_id IS NULL))
);

CREATE INDEX idx_notices_published
    ON notices(published_at DESC) WHERE published_at IS NOT NULL;

-- ============================================================
-- 8. 온라인 테스트
-- ============================================================

CREATE TABLE online_tests (
    id              BIGSERIAL PRIMARY KEY,
    class_room_id   BIGINT       NOT NULL REFERENCES class_rooms(id),
    teacher_id      BIGINT       NOT NULL REFERENCES teachers(id),
    title           VARCHAR(200) NOT NULL,
    question_count  SMALLINT     NOT NULL,
    choice_count    SMALLINT     NOT NULL DEFAULT 5,   -- 오지선다
    correct_choices SMALLINT[]   NOT NULL,             -- 정답. {3,5,1,2,4,...}
    points          SMALLINT[],                        -- 문항별 배점. NULL이면 균등 배점
    answer_s3_key   VARCHAR(500),                      -- 해설·정답지 파일 (채점 후에만 공개)
    score_type      VARCHAR(20),                       -- WORD면 채점 결과가 scores로 반영
    subject         VARCHAR(50),                       -- score_type이 있으면 필수
    year            SMALLINT     NOT NULL,
    month           SMALLINT     NOT NULL,
    week            SMALLINT     NOT NULL,
    opens_at        TIMESTAMPTZ,
    closes_at       TIMESTAMPTZ,
    published_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_online_tests_qcount CHECK (question_count BETWEEN 1 AND 100),
    CONSTRAINT ck_online_tests_ccount CHECK (choice_count   BETWEEN 2 AND 10),
    CONSTRAINT ck_online_tests_type   CHECK (score_type IN ('WORD','INTERNAL','MOCK')),
    CONSTRAINT ck_online_tests_month  CHECK (month BETWEEN 1 AND 12),
    CONSTRAINT ck_online_tests_week   CHECK (week  BETWEEN 1 AND 5),
    -- coalesce가 필요하다. array_length('{}', 1)은 0이 아니라 NULL이라
    -- 그냥 비교하면 빈 배열이 CHECK를 통과한다
    CONSTRAINT ck_online_tests_keylen CHECK (
        coalesce(array_length(correct_choices, 1), 0) = question_count),
    CONSTRAINT ck_online_tests_points CHECK (points IS NULL
        OR coalesce(array_length(points, 1), 0) = question_count),
    -- score_type이 있으면 scores로 반영되는데 scores.subject가 NOT NULL이다
    CONSTRAINT ck_online_tests_subject CHECK (score_type IS NULL OR subject IS NOT NULL)
);

CREATE INDEX idx_online_tests_class ON online_tests(class_room_id, published_at DESC);
CREATE INDEX idx_online_tests_week  ON online_tests(year, month, week);

CREATE TABLE online_test_submissions (
    id             BIGSERIAL PRIMARY KEY,
    online_test_id BIGINT      NOT NULL REFERENCES online_tests(id),
    student_id     BIGINT      NOT NULL REFERENCES students(id),
    chosen_choices SMALLINT[]  NOT NULL,          -- 학생 답. 미체크는 NULL 요소
    status         VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    submitted_at   TIMESTAMPTZ,
    score          NUMERIC(5,2),                  -- 100점 환산
    correct_count  SMALLINT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_online_test_sub_status
        CHECK (status IN ('IN_PROGRESS','SUBMITTED'))
);

CREATE UNIQUE INDEX uq_online_test_submissions
    ON online_test_submissions(online_test_id, student_id);
CREATE INDEX idx_online_test_sub_student
    ON online_test_submissions(student_id, created_at DESC);

-- ============================================================
-- 9. 클리닉
-- ============================================================

-- 클리닉 시간대. 선생님이 미리 열어 둔다
CREATE TABLE clinics (
    id          BIGSERIAL PRIMARY KEY,
    teacher_id  BIGINT      NOT NULL REFERENCES teachers(id),
    clinic_date DATE        NOT NULL,
    start_time  TIME        NOT NULL,
    end_time    TIME        NOT NULL,
    capacity    SMALLINT,                              -- NULL이면 인원 제한 없음
    status      VARCHAR(20) NOT NULL DEFAULT 'OPEN',   -- OPEN이면 신청 가능
    memo        TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_clinics_status CHECK (status IN ('OPEN','CLOSED')),
    CONSTRAINT ck_clinics_time   CHECK (end_time > start_time)
);

-- 반 이름과 같은 이유로 부분 인덱스다. 닫은 시간대가 그 슬롯을 영구히 점유하면 안 된다
CREATE UNIQUE INDEX uq_clinics_slot
    ON clinics(clinic_date, start_time) WHERE status = 'OPEN';
CREATE INDEX idx_clinics_date ON clinics(clinic_date DESC, status);

-- 학생별 신청·배정. 클리닉 1개에 학생 N명
CREATE TABLE clinic_reservations (
    id            BIGSERIAL PRIMARY KEY,
    clinic_id     BIGINT      NOT NULL REFERENCES clinics(id),
    student_id    BIGINT      NOT NULL REFERENCES students(id),
    assigned_by   BIGINT      REFERENCES teachers(id),   -- NULL이면 학생 본인 신청
    status        VARCHAR(20) NOT NULL DEFAULT 'RESERVED',
    attend_status VARCHAR(20),                           -- 확정 전에는 NULL
    memo          TEXT,
    checked_by    BIGINT      REFERENCES teachers(id),
    checked_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_clinic_res_status
        CHECK (status IN ('RESERVED','CANCELED','MOVED')),
    CONSTRAINT ck_clinic_res_attend
        CHECK (attend_status IN ('PRESENT','LATE','ABSENT','SICK','EXCUSED'))
);

CREATE UNIQUE INDEX uq_clinic_reservations
    ON clinic_reservations(clinic_id, student_id) WHERE status = 'RESERVED';
CREATE INDEX idx_clinic_res_student ON clinic_reservations(student_id, created_at DESC);
CREATE INDEX idx_clinic_res_clinic  ON clinic_reservations(clinic_id, status);

-- 학생의 시간 변경 요청 → 선생님 승인·거절
CREATE TABLE clinic_change_requests (
    id               BIGSERIAL PRIMARY KEY,
    reservation_id   BIGINT      NOT NULL REFERENCES clinic_reservations(id),
    student_id       BIGINT      NOT NULL REFERENCES students(id),
    target_clinic_id BIGINT      REFERENCES clinics(id),   -- NULL이면 취소 요청
    reason_code      VARCHAR(30) NOT NULL,                 -- 사유 선택값. 목록 미확정이라 CHECK 없음
    reason_note      TEXT,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    decided_by       BIGINT      REFERENCES teachers(id),
    decided_at       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_clinic_req_status
        CHECK (status IN ('PENDING','APPROVED','REJECTED'))
);

CREATE INDEX idx_clinic_requests_pending
    ON clinic_change_requests(status, created_at) WHERE status = 'PENDING';
