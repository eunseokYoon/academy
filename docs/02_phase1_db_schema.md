# Phase 1 — 데이터베이스 스키마

**선행 조건:** Phase 0
**목표:** 전체 테이블이 생성되고, 엔티티가 스키마와 일치해 `ddl-auto: validate`로 부팅이 성공한다.

---

## 1. 작업 방식

로컬 PostgreSQL은 Phase 0에서 만든 컨테이너입니다. 먼저 띄워 두세요.

```bash
docker compose up -d
docker compose ps          # healthy 확인 후 진행
```

`backend/src/main/resources/db/migration/` 아래에 두 파일을 만듭니다.

| 파일 | 내용 |
|---|---|
| `V1__init.sql` | 전체 테이블 + 인덱스 |
| `V2__seed.sql` | 학교 2개, 선생님 계정 1개 |

### 주차(週次) 모델 — 먼저 읽으세요

자료·영상·시험 결과를 **주차 단위로 관리**합니다. `6월 1주차`, `7월 2주차` 형태입니다.

```sql
year   SMALLINT NOT NULL,   -- 2026
month  SMALLINT NOT NULL,   -- 1~12
week   SMALLINT NOT NULL    -- 1~5, 해당 '월'의 몇 번째 주
```

`lessons`, `materials`, `scores` **세 테이블이 이 세 컬럼을 공유**합니다. 선생님 화면이
"2026년 6월 2주차"를 고르면 그 주의 영상·자료·시험 결과를 한 화면에서 입력합니다.

**주차 경계** — 그 달의 1일이 포함된 주(월요일 시작)가 1주차입니다.

```
2026년 6월 1일이 월요일이면  6/1~6/7 = 1주차, 6/8~6/14 = 2주차 ...
2026년 6월 1일이 목요일이면  6/1~6/7 이 아니라 6/1~6/7이 속한 월~일 주가 1주차
```

**값은 계산해서 저장하지 말고, 선생님이 화면에서 고른 값을 그대로 저장하세요.**
서버는 날짜로부터 기본값만 채워 주고, 선생님이 바꿀 수 있게 둡니다. 달 경계에 걸친 주는
학원마다 세는 방식이 달라서, 자동 계산을 강제하면 반드시 어긋납니다.

**반별로 나누지 않습니다.** `6월 2주차`는 달력상의 주라 전 반이 같은 번호를 씁니다.

**기존 마이그레이션 파일은 이후 절대 수정하지 않습니다.** 변경이 필요하면 `V3__...sql`을 추가하세요.

---

## 2. V1__init.sql

### 2-1. 계정

```sql
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

CREATE TABLE schools (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
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
    school_id    BIGINT      NOT NULL REFERENCES schools(id),
    grade        SMALLINT    NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'ENROLLED',
    withdrawn_at DATE,
    memo         TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_students_grade  CHECK (grade BETWEEN 1 AND 3),
    CONSTRAINT ck_students_status CHECK (status IN ('ENROLLED','WITHDRAWN'))
);

CREATE INDEX idx_students_parent       ON students(parent_id);
CREATE INDEX idx_students_school_grade ON students(school_id, grade);

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
```

### 계정은 회원가입 시점에 만들어집니다

`students.user_id`와 `students.parent_id`가 **둘 다 nullable**인 이유입니다.

선생님이 학생을 등록하면 `students` 행과 회원가입 코드 2장(학생용·학부모용)만 생깁니다.
`users` 행은 **당사자가 직접 회원가입할 때** 만들어집니다.

**경로가 두 개입니다.**

```
[주 경로] 반 코드로 학생이 직접 가입
  선생님이 반 생성 → class_rooms.join_code 발급 → 수업에서 반 전체에 구두 전달
    → 학생이 코드 + 이름 + 학교 + 학년 + 본인번호 + 학부모번호 입력
       → students(name) + users(STUDENT) + enrollments(그 반) 생성
       → signup_codes(PARENT) 1장 자동 발급
    → 선생님이 T-2에서 그 코드를 확인해 학부모에게 전달
       → 학부모가 코드 + 본인 번호로 가입 → users(PARENT) + parents, students.parent_id 연결

[보조 경로] 선생님이 직접 등록  — 폰이 없거나 코드를 못 쓰는 학생용
  선생님이 학생 등록 → students(name) 행 + signup_codes 2장 (STUDENT / PARENT)
    ├─ 학생이 코드 + 본인 번호로 가입   → users(STUDENT), students.user_id 연결
    └─ 학부모가 코드 + 본인 번호로 가입 → users(PARENT) + parents, students.parent_id 연결
```

두 경로 모두 `students` 행이 먼저 생기고 `users`가 나중에 붙는 구조는 같습니다.

**미가입 학생도 출석·숙제·성적 데이터를 가질 수 있습니다.** 모든 도메인 테이블이 `users.id`가 아니라
`students.id`를 참조하기 때문입니다. 선생님은 200명을 먼저 다 입력해 두고 운영을 시작할 수 있고,
계정은 각자 가입하는 대로 붙습니다.

**`student.getUser()`가 `null`일 수 있다는 뜻입니다.** `StudentAccessGuard`를 포함해
`students`에서 `users`로 타고 들어가는 모든 코드에 null 검사를 넣으세요. 빠뜨리면 미가입 학생이
섞인 목록에서 NPE가 납니다.

### 이름은 `students.name`에 있습니다 (`users.name`이 아닙니다)

**학생 이름을 `users`에서 읽지 마세요.** 미가입 학생은 `users` 행이 없어서 이름이 사라집니다.

출석부·숙제 명단·반 명단이 전부 미가입 학생을 포함하므로, 이름은 계정과 무관하게 항상
존재해야 합니다. 그래서 `students.name`이 `NOT NULL`입니다.

| 상황 | 이름 출처 |
|---|---|
| 학생이 반 코드로 가입 | 가입 폼에 입력한 값 → `students.name` |
| 선생님이 직접 등록 | 등록 폼에 입력한 값 → `students.name` |
| 가입 후 | 여전히 `students.name`. `users.name`은 참조하지 않음 |

`users.name`은 **선생님·학부모 계정에만** 의미가 있습니다. 학생 계정에도 값이 들어가지만
(NOT NULL이라) 화면에 쓰는 값은 언제나 `students.name`입니다. 둘이 어긋나도 `students.name`이 기준입니다.

```
❌ ORDER BY e.student.user.name     -- INNER JOIN이 되어 미가입 학생이 통째로 사라진다
✅ ORDER BY e.student.name
```

**이 실수는 에러를 내지 않습니다.** 명단에서 조용히 빠지고, 출석부에 안 뜨고, 숙제도 안 나갑니다.
`targetCount: 20`이 `8`로 줄어도 아무도 모릅니다. JPQL에서 nullable 연관을 경로로 타면
암묵적 INNER JOIN이 된다는 점을 기억하세요.

`signup_codes`는 학생용·학부모용을 `target_role`로 구분합니다. 한 장을 둘이 나눠 쓰면
먼저 쓴 쪽이 상대 계정을 가져가므로 **반드시 역할별로 따로 발급**합니다.

### 로그인 아이디는 전화번호입니다

`users.login_id`에 **하이픈 없는 숫자 전화번호**를 저장합니다 (`01011112222`).
학부모·학생이 `stu0088` 같은 값을 외우지 못해 문의가 반복되기 때문입니다.

**불변식: `login_id`는 항상 `phone`의 정규화된 값입니다.**

```
login_id = phone.replaceAll("[^0-9]", "")
```

두 컬럼을 따로 수정하지 마세요. 번호 변경은 **`User.changePhone()` 한 곳에서만** 처리하고,
거기서 `phone`과 `login_id`를 함께 갱신합니다. 따로 고치면 번호를 바꾼 학부모가 로그인하지 못합니다.

`phone`을 `NOT NULL`로 바꾼 이유가 이것입니다. 번호가 없으면 로그인할 수단이 없습니다.

**학생과 학부모는 서로 다른 번호를 써야 합니다.** `login_id`가 UNIQUE라 같은 번호로 두 계정을
만들 수 없습니다. 학생이 본인 휴대폰이 없어 학부모 번호를 쓰려는 경우가 실제로 생기는데,
서버는 409 `DUPLICATE_RESOURCE`로 막고 선생님이 다른 번호를 받도록 안내합니다.

### 2-2. 반 · 수강

```sql
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
```

### 반은 선생님이 자유롭게 만듭니다

`class_rooms`에 **`school_id`·`grade`·`type`이 없습니다.** 선생님이 T-3 화면에서 이름을 직접 정해
추가·수정·삭제합니다. 정규반/특강반 같은 고정 구분도 두지 않습니다.

학교·학년으로 반을 묶으면 "고2 심화반"처럼 조합이 강제되고, 학교가 섞인 특강이나
수준별 분반을 만들 때마다 예외 처리가 필요합니다. **이름 하나로 두는 편이 실제 운영에 맞습니다.**

- `학교`·`학년`은 **학생(`students`)에만** 남습니다. 시험 D-day와 자료실 공개 범위가 이 값을 씁니다.
- 반 배정 시 학교·학년 일치 검사를 하지 마세요. 비교할 대상이 없습니다.
- `uq_class_rooms_name`은 **활성 반끼리만** 이름 중복을 막습니다. 종료(`CLOSED`)된 반의 이름은
  다음 학기에 다시 쓸 수 있습니다.

**부분 유니크 인덱스(`WHERE left_at IS NULL`)의 이유:** 같은 반을 나갔다가 다시 들어오는 경우를 허용하되, 동시에 두 번 등록되는 것은 막습니다.

`day_of_week`는 1=월요일 ~ 7=일요일입니다 (ISO-8601). `java.time.DayOfWeek.getValue()`와 일치합니다.

### `join_code` — 반 가입 코드

**학생 회원가입의 주 경로입니다.** 선생님이 반을 만들면 코드가 자동 발급되고, 그 코드를 수업에서
반 전체에 구두로 알려줍니다. 학생이 가입 시 코드를 넣으면 그 반에 자동 배정됩니다.

```
선생님이 반 생성 (T-3)   →  join_code "HK7F2Q" 발급
                             ↓ 오프라인으로 반 전체에 전달
학생이 /signup           →  코드 + 이름 + 학교 + 학년 + 본인번호 + 학부모번호
                             ↓ 한 트랜잭션
                            students(name) + users(STUDENT) + enrollments(그 반)
                            + signup_codes(PARENT) 1장 자동 발급
```

코드 생성 규칙은 `signup_codes`와 같습니다. 6자리, 혼동 문자(`0 O 1 I L`) 제외.
구두로 전달하고 상대가 입력하는 값이라 문자셋을 통일합니다.

```java
private static final String CHARS = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
```

**`signup_codes.code`와 `class_rooms.join_code`는 서로 다른 테이블이지만 값이 겹치면 안 됩니다.**
가입 화면이 코드 하나만 받아 서버가 종류를 판별하기 때문입니다. 생성 시 양쪽을 모두 조회해
중복이면 재생성하세요.

| | `signup_codes.code` | `class_rooms.join_code` |
|---|---|---|
| 대상 | 특정 학생 1명 (또는 그 학부모) | 반 전체 |
| 수명 | 7일, 1회용 (`used_at`) | 반이 살아 있는 동안. `join_code_active`로 여닫음 |
| 본인 확인 | 발급 시 등록된 전화번호와 대조 | **없음** |
| 가입 시 추가 입력 | 없음 (학부모는 이름) | 이름·학교·학년·본인번호·학부모번호 |

**`join_code_active`가 이 방식의 유일한 방어선입니다.**

반 코드는 20명이 나눠 쓰는 값이라 특정인의 전화번호로 묶을 수 없습니다. 즉 **코드를 아는
사람은 누구나 가입할 수 있고**, 가입하면 그 반의 수업영상·자료실·숙제·공지를 봅니다.
학생이 친구에게 알려주는 일은 실제로 일어납니다.

등록 기간이 끝나면 T-3에서 코드를 **반드시 닫으세요**(`join_code_active = false`).
닫힌 코드로 가입을 시도하면 400 `INVITE_CODE_INVALID`입니다.

`students.name`이 저장되므로 선생님이 T-2 명단에서 모르는 이름을 발견할 수 있습니다.
사후 탐지 수단이지 예방책은 아닙니다.

> 자료실에 기출·교재 PDF를 올릴 계획이면 승인 대기 단계(`students.status = 'PENDING'`,
> 선생님 승인 전에는 조회 차단)를 추가하는 편이 안전합니다. 지금 구조에 나중에 얹어도
> 깨지지 않습니다. 현재 범위에는 넣지 않았습니다.

**학부모는 반 코드를 쓸 수 없습니다.** 반 코드로 학부모가 가입하면 어느 학생의 부모인지
알 수 없습니다. 학부모는 학생 가입 시 입력된 번호로 발급되는 `signup_codes`(PARENT)를 씁니다.

### 2-3. 수업 · 시청 기록

```sql
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
```

`video_url`은 **YouTube 미등록 링크 문자열만** 저장합니다. 영상 파일을 저장하지 않습니다.

`published_at`이 `NULL`이면 선생님만 볼 수 있는 작성 중 상태입니다. 학생·학부모 조회 쿼리에는 `published_at IS NOT NULL` 조건을 넣습니다.

### 2-4. 출석

```sql
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
```

**`uq_attendances`가 핵심입니다.** 선생님이 확정 버튼을 두 번 눌러도 중복 행이 생기지 않습니다. 확정 로직은 이 제약을 믿고 `ON CONFLICT (student_id, lesson_id) DO UPDATE`로 처리합니다.

**`attend_date`는 `lessons.lesson_date`와 중복되는 비정규화 컬럼입니다.** 의도된 것입니다. 월별 캘린더 조회 시 `lessons` 조인을 피하기 위한 것으로, 저장 시 반드시 `lessons.lesson_date` 값을 복사해 넣으세요.

`lesson_id`가 nullable인 것은 보강·자습처럼 정규 수업일이 없는 출석을 대비한 것입니다. 1차에서는 항상 값이 채워집니다.

### 2-5. 숙제

```sql
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
```

**`submissions`는 숙제 출제 시점에 대상 학생 전원에 대해 미리 생성됩니다.** 상태 전이는 `NOT_SUBMITTED → SUBMITTED → CHECKED`입니다. 미제출자 조회는 `idx_submissions_status`를 타는 단순 조건 검색이 됩니다.

`feedbacks.submission_id`가 `UNIQUE`이므로 제출 1건당 피드백은 1개입니다. 수정은 UPDATE로 처리합니다.

### 2-6. 시험 일정 · 성적

```sql
CREATE TABLE exam_schedules (
    id         BIGSERIAL PRIMARY KEY,
    school_id  BIGINT      NOT NULL REFERENCES schools(id),
    grade      SMALLINT    NOT NULL,
    year       SMALLINT    NOT NULL,
    semester   SMALLINT    NOT NULL,
    exam_type  VARCHAR(20) NOT NULL,
    start_date DATE        NOT NULL,
    end_date   DATE        NOT NULL,
    scope_note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_exam_type     CHECK (exam_type IN ('MIDTERM','FINAL')),
    CONSTRAINT ck_exam_semester CHECK (semester IN (1,2)),
    CONSTRAINT ck_exam_grade    CHECK (grade BETWEEN 1 AND 3)
);

CREATE UNIQUE INDEX uq_exam_schedules
    ON exam_schedules(school_id, grade, year, semester, exam_type);
CREATE INDEX idx_exam_schedules_lookup
    ON exam_schedules(school_id, grade, start_date);

CREATE TABLE scores (
    id               BIGSERIAL PRIMARY KEY,
    student_id       BIGINT       NOT NULL REFERENCES students(id),
    score_type       VARCHAR(20)  NOT NULL,
    exam_schedule_id BIGINT       REFERENCES exam_schedules(id),
    exam_name        VARCHAR(100) NOT NULL,
    subject          VARCHAR(50)  NOT NULL,
    raw_score        NUMERIC(5,2),
    grade_level      SMALLINT,
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
```

**`uq_scores`가 이중 입력을 막습니다.** 없으면 선생님이 T-8에서 일괄 저장 버튼을 두 번 누를 때
20명분 성적이 두 벌 들어가고, P-4 그래프의 같은 주차에 점이 두 개 찍힙니다. 학부모가 먼저 발견합니다.

**409를 던지지 말고 upsert로 처리하세요.** 같은 시험의 점수를 다시 저장하는 것은
오타 수정이라는 정상 흐름입니다.

```sql
INSERT INTO scores (student_id, score_type, subject, exam_name, exam_date,
                    exam_schedule_id, raw_score, grade_level, year, month, week, memo)
VALUES (...)
ON CONFLICT (student_id, score_type, subject, exam_name, exam_date) DO UPDATE
SET raw_score = EXCLUDED.raw_score,
    grade_level = EXCLUDED.grade_level,
    memo = EXCLUDED.memo,
    updated_at = now();
```

온라인 테스트 자동 반영도 같은 제약을 지납니다. 재응시가 없어(`uq_online_test_submissions`)
행이 1개만 생기지만, 제약이 있으면 그 가정이 깨져도 그래프는 안전합니다.

`exam_schedules`가 학생·학부모 홈 화면 D-day의 근거 데이터입니다. 학교가 2곳이므로 같은 학년이라도 학교별로 날짜가 다릅니다.

`score_type` 세 가지입니다.

| 값 | 뜻 | `raw_score` | `grade_level` | `exam_schedule_id` |
|---|---|---|---|---|
| `WORD` | **주간 단어 테스트** | **100점 만점 환산 필수** | `null` | `null` |
| `INTERNAL` | 내신 | 원점수 | 1~9등급 | 내신일 때만 연결 |
| `MOCK` | 모의고사 | 있을 수도, 없을 수도 | 1~9등급 | `null` |

**`WORD`는 반드시 100점 만점으로 환산해 저장하세요.** 25문항 중 20개면 `80.00`입니다.
문항 수가 주마다 달라지는데 원점수를 그대로 넣으면 주차별 그래프의 세로축이 무너집니다.
환산은 **입력 화면에서** 처리하고 DB에는 환산값만 들어갑니다.

`year`·`month`·`week`는 세 종류 모두 채웁니다. 학부모 화면이 주차별 그래프라 `WORD`가 주 용도지만,
내신·모의도 같은 축에 얹을 수 있어야 합니다.

### 2-7. 자료실 · 공지

```sql
CREATE TABLE materials (
    id            BIGSERIAL PRIMARY KEY,
    title         VARCHAR(200) NOT NULL,
    category      VARCHAR(20)  NOT NULL,
    s3_key        VARCHAR(500) NOT NULL,
    file_name     VARCHAR(255) NOT NULL,
    bytes         BIGINT,
    school_id     BIGINT       REFERENCES schools(id),
    grade         SMALLINT,
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
    CONSTRAINT ck_materials_grade CHECK (grade BETWEEN 1 AND 3),
    CONSTRAINT ck_materials_month CHECK (month BETWEEN 1 AND 12),
    CONSTRAINT ck_materials_week  CHECK (week  BETWEEN 1 AND 5)
);

CREATE INDEX idx_materials_scope
    ON materials(school_id, grade, category, created_at DESC);
CREATE INDEX idx_materials_week
    ON materials(year, month, week, school_id, grade);

CREATE TABLE notices (
    id            BIGSERIAL PRIMARY KEY,
    title         VARCHAR(200) NOT NULL,
    content       TEXT         NOT NULL,
    scope         VARCHAR(20)  NOT NULL,
    school_id     BIGINT       REFERENCES schools(id),
    grade         SMALLINT,
    class_room_id BIGINT       REFERENCES class_rooms(id),
    pinned        BOOLEAN      NOT NULL DEFAULT false,
    published_at  TIMESTAMPTZ,
    created_by    BIGINT       NOT NULL REFERENCES teachers(id),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_notices_scope
        CHECK (scope IN ('ALL','SCHOOL','GRADE','CLASS'))
);

CREATE INDEX idx_notices_published
    ON notices(published_at DESC) WHERE published_at IS NOT NULL;

```

`materials`의 `school_id + grade`가 내신형 구조의 핵심입니다. A고 2학년 학생은 자기 학교 기출만 보이고 B고 자료는 쿼리에서 제외됩니다.

**자료실은 학생 화면(S-8) 전용입니다.** 학부모에게는 노출하지 않습니다.

**후기(`reviews`) 테이블은 만들지 마세요.** 1차 범위에서 제외되었습니다.

### 2-8. 온라인 테스트

선생님이 반에 **오지선다 문제의 정답**을 등록해 두면, 학생이 웹에서 답을 체크하고
제출하는 순간 **자동 채점**됩니다. 내신·모의(`exam_schedules`·`scores`)와는 별개 도메인입니다.

```sql
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
    subject         VARCHAR(50),                       -- score_type이 있으면 필수. scores.subject로 복사
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
    -- coalesce가 필요합니다. array_length('{}', 1)은 0이 아니라 NULL이라
    -- 그냥 비교하면 빈 배열이 CHECK를 통과합니다
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
```

**정답과 답안을 행이 아니라 배열로 둡니다.** 30문항짜리 시험에 학생 20명이면 행 기반은 600행이
되는데, 항상 전체를 한 번에 읽고 쓰는 데이터라 쪼갤 이유가 없습니다. PostgreSQL 네이티브
`SMALLINT[]`라 타입도 보장됩니다.

문항별 정답률이 필요해지면 `unnest`로 뽑을 수 있습니다. 지금은 요구사항에 없으니 만들지 마세요.

```sql
-- 나중에 필요해지면 (지금 구현하지 말 것)
SELECT question_no, count(*) FILTER (WHERE chosen = correct) * 100.0 / count(*)
FROM online_test_submissions s,
     unnest(s.chosen_choices) WITH ORDINALITY AS a(chosen, question_no),
     unnest((SELECT correct_choices FROM online_tests WHERE id = s.online_test_id))
       WITH ORDINALITY AS k(correct, qno)
WHERE a.question_no = k.qno AND s.online_test_id = :testId
GROUP BY question_no;
```

**배열 길이 CHECK 두 개가 핵심 방어입니다.** 길이가 `question_count`와 어긋나면
채점이 **조용히** 틀립니다. 30문항이라 해놓고 정답을 29개만 넣는 실수가 실제로 납니다.

`correct_choices`와 `points`에 같은 방어가 필요합니다. 배점 배열이 짧으면 뒤쪽 문항의 배점이
없어서 총점이 어긋나는데, 정답 배열이 틀렸을 때와 증상이 똑같아 원인을 찾기 어렵습니다.

**`score_type`이 채워져 있으면 채점 결과가 `scores`에 반영됩니다.** 주간 단어 테스트를 이 기능으로
치르면 P-4 그래프에 자동으로 얹힙니다. `null`이면 연습용이라 성적에 남지 않습니다.
이중 입력을 막는 장치이므로 빠뜨리지 마세요.

**`subject`가 있는 이유가 이 반영 경로입니다.** `scores.subject`가 `NOT NULL`인데
`online_tests`에 과목이 없으면 행을 만들 수 없습니다. `ck_online_tests_subject`가
"성적에 반영할 거면 과목을 정해라"를 강제합니다.

**과목을 코드에서 지어내지 마세요.** 성적 관리 범위(영어만인지 전 과목인지)가
학원장 미확정 사항입니다. 출제 화면(T-14)에서 선생님이 입력한 값을 그대로 복사하세요.

`scores`로 복사되는 값은 다음과 같습니다.

| `scores` 컬럼 | 출처 |
|---|---|
| `raw_score` | 채점 점수 (100점 환산값) |
| `score_type` | `online_tests.score_type` |
| `subject` | `online_tests.subject` |
| `exam_name` | `online_tests.title` |
| `exam_date` | 제출일 (`submitted_at`의 날짜) |
| `year` `month` `week` | `online_tests`에서 그대로 복사 |

**시험지는 종이입니다.** 학생이 수업에서 받은 종이 시험지를 풀고, **답만 웹에 입력**합니다.
문제지 파일을 저장하지 않는 이유입니다. 서버가 가진 것은 정답 배열과 해설지 파일뿐입니다.

`answer_s3_key`는 **채점 후에만** 내려주는 해설·정답지입니다. 파일 자체에 정답이 담겨 있으므로
목록·응시 화면 응답에서는 **필드 자체를 빼세요.** `null`로 두는 것도 안 됩니다.

### 2-9. 클리닉

정규 수업과 별개로 진행하는 **보충 수업**입니다. 선생님이 정해 둔 시간대에
**여러 학생이 함께** 참여합니다.

```sql
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

-- 반 이름과 같은 이유로 부분 인덱스입니다. 닫은 시간대는 그 슬롯을 영구히 점유하면 안 됩니다
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
    reason_code      VARCHAR(30) NOT NULL,                 -- 사유 선택값. 목록 미확정
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
```

**테이블이 3개인 이유** — 한 클리닉 시간에 학생이 여러 명이라 `clinics`에 `student_id`를 둘 수 없습니다.
`clinic_reservations`가 그 연결을 담고, 출석도 학생별이므로 여기 기록합니다.

**신청 경로가 두 가지입니다.**

| 경로 | `assigned_by` |
|---|---|
| 학생이 직접 신청 (S-9) | `NULL` |
| 선생님이 배정 (T-13) | 배정한 선생님 id |

`uq_clinic_reservations`가 **같은 클리닉 중복 신청을 막습니다.** `WHERE status = 'RESERVED'` 부분
인덱스라, 취소했다가 다시 신청하는 것은 허용됩니다.

**정원 체크는 클리닉 행을 먼저 잠그고 하세요.** 동시 신청이 몰리면 정원을 넘깁니다.

```sql
-- 1) 클리닉 행에 락을 건다. 같은 클리닉 신청을 직렬화하는 것이 목적
SELECT capacity FROM clinics WHERE id = :clinicId FOR UPDATE;

-- 2) 락을 쥔 상태에서 센다
SELECT count(*) FROM clinic_reservations
WHERE clinic_id = :clinicId AND status = 'RESERVED';

-- 3) capacity가 NULL이거나 count < capacity면 삽입, 아니면 409
INSERT INTO clinic_reservations (clinic_id, student_id) VALUES (:clinicId, :studentId);
```

세 문장이 **하나의 트랜잭션**이어야 합니다. 커밋 시점에 락이 풀리면서 다음 신청자가 갱신된
수를 봅니다.

> ⚠️ **"조건부 삽입 한 방"으로 풀려고 하지 마세요.** 아래는 안전해 보이지만 아닙니다.
>
> ```sql
> INSERT INTO clinic_reservations (clinic_id, student_id)
> SELECT :clinicId, :studentId
> WHERE (SELECT count(*) FROM clinic_reservations
>        WHERE clinic_id = :clinicId AND status = 'RESERVED')
>     < (SELECT capacity FROM clinics WHERE id = :clinicId);   -- ❌
> ```
>
> PostgreSQL 기본 격리수준은 READ COMMITTED이고, **서브쿼리의 `count(*)`는 세는 행에
> 락을 걸지 않습니다.** 두 트랜잭션이 동시에 `count = 5`를 읽으면 둘 다 조건을 통과해
> 정원 6에 7행이 들어갑니다. "세어 보고 넣기"를 SQL 한 문장에 넣었을 뿐 같은 버그입니다.
>
> 저부하에서는 통과하다가 신청이 몰리는 순간 깨지므로, 테스트로 잡기도 어렵습니다.

`FOR UPDATE`가 클리닉 단위로만 직렬화하고 서로 다른 클리닉은 병렬로 처리됩니다.
학생 200명 규모에서 대기 비용은 사실상 0입니다.

같은 방식이 **변경 요청 승인**(목표 클리닉 정원 재확인)과 **선생님 일괄 배정**에도
그대로 적용됩니다. 세 곳 모두 같은 메서드를 쓰세요.

**클리닉 출석은 `attendances`에 넣지 않습니다.** `attendances.class_room_id`가 `NOT NULL`인데
클리닉은 반이 없습니다. `clinic_reservations.attend_status`에 기록하세요.
출석 확정 방식은 T-5와 동일하게 **안 온 학생만 체크**합니다.

**변경 요청 승인은 한 트랜잭션입니다.** 기존 예약을 `MOVED`로 바꾸고, 목표 클리닉에
새 `RESERVED` 행을 만듭니다. `target_clinic_id`가 `NULL`이면 취소 요청이라 기존 예약만 `CANCELED`로 바꿉니다.
목표 클리닉의 정원도 다시 확인해야 합니다.

> ⚠️ **`reason_code` 옵션 목록이 미확정입니다.** 학원장 확인 후 채웁니다.
> 확정 전에는 CHECK 제약을 걸지 말고 값을 임의로 만들지도 마세요. enum도 만들지 마세요.
> 확정되면 `V4__clinic_reason.sql`로 CHECK를 추가합니다.

---

## 3. V2__seed.sql

```sql
INSERT INTO schools (name) VALUES ('A고등학교'), ('B고등학교');

-- 선생님 계정 (비밀번호는 최초 로그인 후 변경)
-- login_id는 전화번호입니다. 실제 강사 번호로 교체하세요.
INSERT INTO users (role, login_id, password_hash, name, phone)
VALUES ('TEACHER', '01000000000', '$2a$10$REPLACE_WITH_BCRYPT_HASH', '이관우', '01000000000');

INSERT INTO teachers (user_id)
SELECT id FROM users WHERE login_id = '01000000000';
```

**학교 이름은 실제 학교명으로 교체해야 합니다.** 값이 확정되지 않았으므로 임시로 두고, 실제 운영 전에 UPDATE하거나 시드를 수정하세요.

**선생님 계정의 `login_id`·`phone`도 실제 강사 번호로 교체하세요.** `01000000000`으로 두면 운영에서 로그인할 수 없습니다.

`password_hash`는 BCrypt로 생성한 값을 넣습니다. 개발 편의를 위해 별도 `V3__dev_seed.sql`을 만들어 테스트용 학생·학부모·반 데이터를 추가하는 것은 괜찮습니다. 단, 운영 배포 시에는 제외되도록 프로파일을 분리하세요.

---

## 4. 엔티티 작성 규칙

DDL과 1:1로 대응하는 JPA 엔티티를 만듭니다.

```java
@Entity
@Table(name = "attendances")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Attendance {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id")
    private Lesson lesson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "attend_date", nullable = false)
    private LocalDate attendDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttendanceStatus status;

    // ...
}
```

**반드시 지킬 것**

- 모든 연관관계는 `FetchType.LAZY`. EAGER는 N+1의 주범입니다.
- 모든 enum에 `@Enumerated(EnumType.STRING)`. 빠뜨리면 ordinal로 저장되어 순서 변경 시 데이터가 깨집니다.
- `@NoArgsConstructor(access = PROTECTED)` + 정적 팩토리 메서드. 무분별한 `new`를 막습니다.
- setter를 만들지 마세요. 상태 변경은 의미 있는 이름의 메서드로 (`submit()`, `confirmAttendance()`).
- 양방향 연관관계는 꼭 필요할 때만. 대부분 단방향 `@ManyToOne`으로 충분합니다.
- `@ManyToOne`에 `cascade`를 걸지 마세요. 삭제가 연쇄되면 복구가 불가능합니다.
- **상위 클래스 선택은 `01_phase0_setup.md` 4-1의 표를 그대로 따르세요.** 테이블마다 감사 컬럼 구성이
  다릅니다. `updated_at`이 없는 테이블에 `BaseTimeEntity`를 붙이면 `ddl-auto: validate`가 부팅을 막습니다.
  특히 `Attendance`는 `updated_at`이 감사값이 아니라 **정정 시각**이라 자동 갱신 대상이 아닙니다.

### CASCADE가 걸린 곳 (이 두 곳뿐)

```
submission_photos.submission_id → submissions   ON DELETE CASCADE
feedbacks.submission_id         → submissions   ON DELETE CASCADE
```

`submissions.homework_id`에는 **없습니다.** 숙제를 지우려면 `submissions`를 먼저 지워야 하며,
그때 사진과 피드백이 위 두 제약으로 따라 지워집니다. 삭제 조건은 `06_phase5_homework.md` 3절 참조.

### Enum 목록

```java
UserRole            { TEACHER, STUDENT, PARENT }
UserStatus          { ACTIVE, INACTIVE }
StudentStatus       { ENROLLED, WITHDRAWN }
ClassRoomStatus     { ACTIVE, CLOSED }
LessonAttendanceStatus { PENDING, CONFIRMED }
AttendanceStatus    { PRESENT, LATE, ABSENT, SICK, EXCUSED }
SubmissionStatus    { NOT_SUBMITTED, SUBMITTED, CHECKED }
ExamType            { MIDTERM, FINAL }
ScoreType           { WORD, INTERNAL, MOCK }
MaterialCategory    { LESSON, TEXTBOOK, PAST_EXAM, ETC }
MaterialVisibility  { PUBLIC, CLASS }
NoticeScope         { ALL, SCHOOL, GRADE, CLASS }
OnlineTestStatus    { IN_PROGRESS, SUBMITTED }
ClinicStatus        { OPEN, CLOSED }
ReservationStatus   { RESERVED, CANCELED, MOVED }
ChangeRequestStatus { PENDING, APPROVED, REJECTED }
```

클리닉 출석은 별도 enum을 만들지 말고 `AttendanceStatus`를 그대로 씁니다.

**`clinic_change_requests.reason_code`는 enum을 만들지 마세요.** 옵션 목록이 미확정입니다.
확정 전까지는 `String`으로 두고, 값이 정해지면 그때 enum과 CHECK 제약을 함께 추가합니다.

`signup_codes.target_role`은 별도 enum을 만들지 말고 `UserRole`을 그대로 쓰세요.
`STUDENT`·`PARENT`만 유효하며, `TEACHER`가 들어오지 못하도록 CHECK 제약이 걸려 있습니다.

---

## 5. 완료 조건 (DoD)

- [ ] `V1__init.sql` 실행으로 **25개** 테이블이 생성된다
- [ ] `reviews` 테이블이 존재하지 않는다 (1차 제외)
- [ ] **`students.name`이 `NOT NULL`로 존재한다** (미가입 학생도 이름을 가짐)
- [ ] **`class_rooms.join_code`가 `NOT NULL UNIQUE`이고 `join_code_active`가 있다**
- [ ] `lessons`·`materials`·`scores`에 `year`·`month`·`week`가 모두 `NOT NULL`로 있다
- [ ] `clinics`에 `UNIQUE (clinic_date, start_time) WHERE status = 'OPEN'` 부분 유니크 인덱스가 존재한다
- [ ] 닫은(`CLOSED`) 클리닉과 같은 날짜·시각으로 새 클리닉을 열 수 있다
- [ ] `clinic_reservations`에 `WHERE status = 'RESERVED'` 부분 유니크 인덱스가 존재한다
- [ ] `clinic_change_requests.reason_code`에 CHECK 제약이 **없다** (옵션 미확정)
- [ ] `online_tests`에 정답 배열 길이 검증 CHECK가 존재한다 (`ck_online_tests_keylen`)
- [ ] **`online_tests`에 배점 배열 길이 CHECK가 존재한다** (`ck_online_tests_points`)
- [ ] **`correct_choices`에 빈 배열 `{}`을 넣으면 CHECK에 걸린다** (coalesce 확인)
- [ ] **`score_type`만 넣고 `subject`를 비우면 CHECK에 걸린다** (`ck_online_tests_subject`)
- [ ] **`scores`에 `UNIQUE (student_id, score_type, subject, exam_name, exam_date)`가 존재한다**
- [ ] `V2__seed.sql`로 학교 2건, 선생님 1건이 입력된다
- [ ] `ddl-auto: validate` 상태로 애플리케이션이 정상 부팅된다
- [ ] 모든 엔티티의 enum에 `@Enumerated(EnumType.STRING)`이 있다
- [ ] 모든 `@ManyToOne`이 `FetchType.LAZY`다
- [ ] `attendances`에 `UNIQUE (student_id, lesson_id)`가 존재한다
- [ ] `submissions`에 `UNIQUE (homework_id, student_id)`가 존재한다
- [ ] `enrollments`에 `WHERE left_at IS NULL` 부분 유니크 인덱스가 존재한다
- [ ] DB를 비운 뒤 마이그레이션을 처음부터 재실행해도 성공한다
      (`docker compose down -v && docker compose up -d` 후 애플리케이션 기동)

### 검증 쿼리

```sql
-- 테이블 수 확인 (flyway_schema_history 제외)
--   V1까지: 25개
--   V3(refresh_tokens, Phase 2)까지: 26개
SELECT count(*) FROM information_schema.tables
WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history';

-- 제약 확인
SELECT conname, conrelid::regclass FROM pg_constraint
WHERE conname LIKE 'uq_%' OR conname LIKE 'ck_%' ORDER BY conrelid::regclass::text;
```

---

## 6. 하지 말 것

- 비즈니스 로직, 서비스, 컨트롤러를 만들지 마세요. 이 Phase는 스키마와 엔티티만입니다.
- 제외된 기능의 테이블을 만들지 마세요: `study_sessions`, `streaks`, `rankings`, `notification_logs`, `payments`, `orders`
- `V1__init.sql`을 나중에 수정하지 마세요. 변경은 항상 새 파일로.
- 파티셔닝, 트리거, 저장 프로시저를 넣지 마세요. 200명 규모에서 불필요하고 유지보수만 어려워집니다.
- `ON DELETE CASCADE`를 명세에 없는 곳에 추가하지 마세요. 학생·수업 데이터가 연쇄 삭제되면 복구할 수 없습니다.
