# 학원 관리 서비스 — 개발 명세

Claude Code가 이 폴더의 문서를 읽고 구현합니다. **반드시 `00_README.md`를 먼저 읽고, Phase 번호 순서대로 진행하세요.**

---

## 1. 문서 구성

| 파일 | 내용 | 선행 조건 |
|---|---|---|
| `00_README.md` | 전체 개요, 공통 규약, 금지 사항 | — |
| `01_phase0_setup.md` | 프로젝트 구조, 공통 응답·에러, 로컬 환경 | — |
| `02_phase1_db_schema.md` | 전체 테이블 DDL, 인덱스, 시드 데이터 | Phase 0 |
| `03_phase2_auth.md` | JWT 인증, 권한 검증, 학부모 연결 | Phase 1 |
| `04_phase3_students_classes_lessons.md` | 학생·반·수업 관리 (선생님) | Phase 2 |
| `05_phase4_attendance.md` | 출석 확정, 캘린더, **클리닉 예약·출석** | Phase 3 |
| `06_phase5_homework.md` | 출제, 사진 제출, 피드백 | Phase 3 |
| `07_phase6_video_scores_exams.md` | 수업 레포트, 주차별 성적, D-day | Phase 3 |
| `08_phase7_materials_notices_home.md` | 자료실(학생), 공지, 홈, 대시보드 | Phase 4~6 |
| `09_phase8_deploy.md` | EC2, Docker, S3, 도메인, HTTPS | Phase 7 |
| `10_api_reference.md` | **전체 API 명세 (128개 엔드포인트)** | 상시 참조 |

`10_api_reference.md`는 Phase가 아니라 상시 참조용입니다. 엔드포인트 경로나 요청·응답이 Phase 문서와 다르면 **API 레퍼런스가 기준**입니다.

Phase 4·5·6은 서로 독립적이므로 순서를 바꿔도 됩니다. Phase 7은 앞 단계 데이터를 조합하므로 마지막에 진행합니다.

---

## 2. 서비스 개요

영어 강사 1인이 운영하는 학원의 학생·학부모 관리 시스템입니다. 선생님이 수업·출석·숙제·성적을 입력하면 학생과 학부모가 각자 화면에서 확인합니다.

### 운영 규모

| 항목 | 값 |
|---|---|
| 강사 | 1명 |
| 학교 | 2개교 |
| 학년 | 각 1~3학년 (총 6개 코호트) |
| 학생 | 약 200명 |
| 반 | 학교 하나 · 학년 하나에 속함. 이름은 선생님이 직접 지정 |

**이 규모를 전제로 설계되어 있습니다.** 200명 기준이므로 캐싱, 샤딩, 읽기 복제본, 비동기 큐 같은 대규모 대응 구조는 넣지 마세요. 단순한 구조가 정답입니다.

### 사용자 역할

| 역할 | 설명 |
|---|---|
| `TEACHER` | 전체 데이터 접근 |
| `STUDENT` | 본인 데이터만 |
| `PARENT` | 자신에게 연결된 자녀 데이터만 |

### 계정 관계

- 학부모 1명 : 자녀 N명 (형제·자매)
- 학생 1명 : 학부모 1명 (`students.parent_id`)
- **코드 없는 자유 가입 불가.** 학생은 반 코드 또는 개인 코드, 학부모는 개인 코드로만 가입합니다

### 회원가입

**주 경로는 반 코드입니다.** 선생님이 반을 만들면 가입 코드가 발급되고, 수업에서 반 전체에
구두로 알려줍니다. 학생이 그 코드로 가입하면 **그 반에 자동 배정**됩니다.

```
[주 경로] 반 코드 — 학생 자가 가입
선생님이 반 생성 (T-3)
  → class_rooms.join_code 발급 ("HK7F2Q") → 수업에서 반 전체에 전달
     → 학생이 /signup: 코드 + 이름 + 본인번호 + 보호자번호
        → students(name, 학교·학년은 반에서 복사) + users(STUDENT) + enrollments(그 반)
        → signup_codes(PARENT) 1장 자동 발급
     → 선생님이 T-2에서 그 코드를 확인해 학부모에게 전달
        → 학부모가 /signup: 코드 + 본인 번호 → users(PARENT) + parents

[보조 경로] 개인 코드 — 폰이 없거나 코드를 못 쓰는 학생
선생님이 학생 등록 (T-2)
  → students(name) + signup_codes 2장 (STUDENT / PARENT)
     ├─ 학생이   /signup: 코드 + 본인 번호 → users(STUDENT)
     └─ 학부모가 /signup: 코드 + 본인 번호 → users(PARENT) + parents
```

**세 가지 모두 같은 화면, 같은 엔드포인트(`POST /api/auth/signup`)를 씁니다.**
서버가 코드를 보고 종류를 판별하므로 사용자가 "학생/학부모"를 고르지 않습니다.

**학부모는 반 코드를 쓸 수 없습니다.** 반 코드로 가입하면 어느 학생의 부모인지 알 수 없습니다.

`students.user_id`와 `students.parent_id`가 둘 다 nullable인 이유입니다. **미가입 학생도 출석·숙제·성적
데이터를 가질 수 있습니다.** 도메인 테이블이 전부 `students.id`를 참조하기 때문입니다.
`student.getUser()`가 `null`일 수 있으니 null 검사를 빠뜨리지 마세요.

### 이름은 `students.name`에 있습니다

**학생 이름을 `users.name`에서 읽지 마세요.** 미가입 학생은 `users` 행이 없어 이름이 사라집니다.
출석부·숙제 명단·반 명단이 모두 미가입 학생을 포함하므로, 이름은 계정과 무관하게 존재해야 합니다.

```
❌ ORDER BY e.student.user.name     -- 암묵적 INNER JOIN. 미가입 학생이 통째로 사라진다
✅ ORDER BY e.student.name
```

**이 실수는 에러를 내지 않습니다.** 명단에서 조용히 빠지고, 출석부에 안 뜨고, 숙제도 안 나갑니다.
학기 초에는 미가입 학생이 다수라 출석부가 통째로 비어 보입니다.

### 로그인

**아이디는 전화번호입니다.** `users.login_id`에 하이픈 없는 숫자로 저장하고, `users.phone`과 항상 같은 값을 유지합니다.

| 역할 | 로그인 아이디 | 최초 비밀번호 |
|---|---|---|
| `TEACHER` | 강사 전화번호 (시드) | 시드 값 |
| `STUDENT` | 학생 전화번호 | **`0000`** (가입 시 서버가 설정) |
| `PARENT` | 학부모 전화번호 | **`0000`** (가입 시 서버가 설정) |

학생과 학부모는 **서로 다른 번호**를 써야 합니다. `login_id`가 UNIQUE라 같은 번호로 두 계정을 만들 수 없습니다.

**`0000`은 전원이 아는 값이므로, `must_change_password = true`인 토큰은 비밀번호 변경 외 모든 API를
403 `PASSWORD_CHANGE_REQUIRED`로 막습니다.** 이게 없으면 가입 후 방치된 계정으로 남의 성적이 샙니다.
허용 경로는 `PATCH /api/auth/password`, `GET /api/auth/me`, `POST /api/auth/logout` 셋뿐입니다.

비밀번호 분실 시 자동 재설정 경로는 없습니다. 이메일·SMS가 모두 범위 밖이라, 선생님이 `POST /api/teacher/students/{studentId}/reset-password`로 초기화합니다.

---

## 3. 기술 스택

### 백엔드

| 항목 | 값 |
|---|---|
| 언어 | Java 21 |
| 프레임워크 | Spring Boot 3.3.x |
| 빌드 | Gradle (Kotlin DSL) |
| DB | PostgreSQL 16 |
| ORM | Spring Data JPA |
| 마이그레이션 | Flyway |
| 인증 | Spring Security + JWT (jjwt 0.12.x) |
| 파일 | AWS S3 (presigned URL) |

### 프론트엔드

| 항목 | 값 |
|---|---|
| 프레임워크 | React 18 + TypeScript |
| 빌드 | Vite |
| 라우팅 | React Router v6 |
| 서버 상태 | TanStack Query v5 |
| 스타일 | Tailwind CSS |

**단일 코드베이스, 경로 기반 분리.** 학생·학부모·선생님 화면을 별도 프로젝트로 나누지 마세요.

### 인프라

AWS EC2 (단일 인스턴스) / Docker + Docker Compose / PostgreSQL(관리형) / S3 / Route 53 + Let's Encrypt

**Docker 사용 범위**

| 환경 | 구성 |
|---|---|
| 로컬 개발 | PostgreSQL만 컨테이너. 백엔드·프론트엔드는 호스트에서 직접 실행 |
| 운영 (EC2) | `web`(nginx) + `backend`(Spring Boot) + `certbot` 3개 컨테이너 |

로컬에서 백엔드까지 컨테이너에 넣으면 코드 한 줄 고칠 때마다 이미지를 다시 빌드해야 해서 개발 루프가 느려집니다. DB는 컨테이너로 띄우지만 **운영 DB는 관리형(Supabase/RDS)을 씁니다.** 같은 EC2에 Postgres 컨테이너를 올리면 백업·복구를 직접 책임져야 합니다.

---

## 4. 프로젝트 구조

```
academy/
├── docker-compose.yml          로컬: PostgreSQL만
├── docker-compose.prod.yml     운영: web + backend + certbot
├── .env                        DOMAIN, TAG (커밋 금지)
├── backend/
│   ├── Dockerfile              멀티스테이지, 비루트, KST
│   ├── .dockerignore
│   ├── build.gradle.kts
│   └── src/main/
│       ├── java/com/njwenglish/
│       │   ├── AcademyApplication.java
│       │   ├── common/              공통 기반 (계층 밖, 전 계층이 사용)
│       │   │   ├── config/          SecurityConfig, CorsConfig, JpaConfig, S3Config
│       │   │   ├── response/        ApiResponse, PageResponse
│       │   │   ├── error/           ErrorCode, BusinessException, GlobalExceptionHandler
│       │   │   ├── entity/          BaseTimeEntity
│       │   │   ├── security/        JwtTokenProvider, JwtAuthenticationFilter,
│       │   │   │                    LoginUser, StudentAccessGuard
│       │   │   └── s3/              S3ClientFactory, PresignedUrlProvider
│       │   ├── controller/          계층 1 — HTTP 요청·응답 (역할별 하위 패키지)
│       │   │   ├── auth/
│       │   │   ├── teacher/
│       │   │   ├── student/
│       │   │   ├── parent/
│       │   │   └── shared/          notices, health
│       │   ├── service/             계층 2 — 비즈니스 로직·트랜잭션 (평면)
│       │   ├── repository/          계층 3 — JPA 리포지토리, 엔티티당 1개 (평면)
│       │   ├── entity/              테이블당 1개 (평면)
│       │   │   └── enums/           UserRole, AttendanceStatus, SubmissionStatus ...
│       │   └── dto/                 도메인별 하위 패키지
│       │       ├── auth/  member/  classroom/  lesson/  attendance/
│       │       └── homework/  score/  material/  notice/  clinic/  onlinetest/  home/
│       └── resources/
│           ├── application.yml
│           └── db/migration/    V1__init.sql, V2__seed.sql ...
└── frontend/
    ├── Dockerfile              빌드 → nginx 서빙
    ├── .dockerignore
    ├── templates/              nginx conf 템플릿 (envsubst)
    ├── package.json
    └── src/
        ├── main.tsx
        ├── router.tsx
        ├── shared/
        │   ├── api/             axios 인스턴스, 토큰 인터셉터
        │   ├── auth/            로그인 상태, 라우트 가드
        │   ├── components/      공용 UI
        │   └── hooks/
        └── routes/
            ├── auth/            로그인, 비밀번호
            ├── student/
            ├── parent/
            └── teacher/
```

### 4-1. 계층 규칙

패키지의 1차 기준은 **계층**입니다. 도메인은 계층 안에서만 나눕니다.

호출은 한 방향으로만 흐릅니다.

```
controller → service → repository → entity
```

| 계층 | 책임 | 하지 말 것 |
|---|---|---|
| `controller` | 요청 검증, 서비스 호출, `ApiResponse` 포장 | repository 직접 주입, 비즈니스 분기, `@Transactional` |
| `service` | 비즈니스 로직, 트랜잭션 경계, 권한 검증, 응답 DTO 조립 | `HttpServletRequest`·`ResponseEntity` 등 웹 타입 참조 |
| `repository` | `JpaRepository` 상속, 쿼리 | 로직, 다른 repository 주입 |
| `entity` | 상태와 상태 변경 메서드 | setter, DTO 의존 |
| `dto` | 요청·응답 전용 객체 | 엔티티 필드 그대로 노출 |

- 역방향 의존(service → controller, repository → service)은 금지입니다.
- service끼리는 호출할 수 있습니다. 순환이 생기면 공통 부분을 별도 service로 내리세요.
- **엔티티를 컨트롤러 밖으로 내보내지 마세요.** 응답은 항상 `~Response` DTO입니다.
- 클래스 이름에 이미 도메인이 들어가므로(`HomeworkService`) `service/`, `repository/`, `entity/`는 평면으로 둡니다. 도메인으로 한 번 더 나누지 마세요.

### 4-2. 계층별 구성

**controller** — 역할별로 나눕니다. 패키지가 곧 URL 접두사입니다.

| 패키지 | 클래스 |
|---|---|
| `controller/auth` | `AuthController` |
| `controller/teacher` | `TeacherDashboardController`, `TeacherSchoolController`, `TeacherStudentController`, `TeacherClassRoomController`, `TeacherLessonController`, `TeacherAttendanceController`, `TeacherHomeworkController`, `TeacherSubmissionController`, `TeacherScoreController`, `TeacherExamScheduleController`, `TeacherMaterialController`, `TeacherNoticeController`, `TeacherClinicController`, `TeacherOnlineTestController` |
| `controller/student` | `StudentHomeController`, `StudentLessonController`, `StudentAttendanceController`, `StudentHomeworkController`, `StudentScoreController`, `StudentMaterialController`, `StudentClinicController`, `StudentOnlineTestController` |
| `controller/parent` | `ParentChildController`, `ParentMeController` |
| `controller/shared` | `NoticeController`, `HealthController` |

**service** (평면)

```
AuthService  TokenService  ParentLinkService
SchoolService  StudentService  ParentService
ClassRoomService  EnrollmentService
LessonService  LessonViewService  AttendanceService
HomeworkTemplateService  HomeworkService  SubmissionService  FeedbackService
ScoreService  ExamScheduleService
MaterialService  NoticeService
OnlineTestService  OnlineTestSubmissionService
ClinicService  ClinicReservationService  ClinicChangeRequestService
HomeService  DashboardService
```

**repository** (평면, 엔티티당 1개)

```
UserRepository  SchoolRepository  TeacherRepository  ParentRepository
StudentRepository  SignupCodeRepository
ClassRoomRepository  EnrollmentRepository
LessonRepository  LessonViewRepository  AttendanceRepository
HomeworkTemplateRepository  HomeworkRepository  SubmissionRepository
SubmissionPhotoRepository  FeedbackRepository
ExamScheduleRepository  ScoreRepository
MaterialRepository  NoticeRepository
OnlineTestRepository  OnlineTestSubmissionRepository
ClinicRepository  ClinicReservationRepository  ClinicChangeRequestRepository
```

**entity** (평면, 테이블당 1개)

```
User  School  Teacher  Parent  Student  SignupCode
ClassRoom  Enrollment  Lesson  LessonView  Attendance
HomeworkTemplate  Homework  Submission  SubmissionPhoto  Feedback
ExamSchedule  Score  Material  Notice
OnlineTest  OnlineTestSubmission
Clinic  ClinicReservation  ClinicChangeRequest
```

enum은 전부 `entity/enums/`에 둡니다. 목록은 `02_phase1_db_schema.md` 참조.

**dto** — 개수가 많아 도메인별로만 나눕니다. 접미사는 `~Request` / `~Response`로 고정합니다.

```
dto/homework/
├── HomeworkCreateRequest.java
├── HomeworkDetailResponse.java
├── SubmissionListResponse.java
└── FeedbackCreateRequest.java
```

패키지: `auth`, `member`, `classroom`, `lesson`, `attendance`, `clinic`, `homework`, `score`, `material`, `notice`, `onlinetest`, `home`

---

## 5. 공통 규약

### 5-1. API 경로

역할별로 접두사를 분리합니다.

```
/api/auth/**       인증 (비로그인 접근 포함)
/api/teacher/**    TEACHER 전용
/api/student/**    STUDENT 전용  (자료실·클리닉 포함)
/api/parent/**     PARENT 전용
/api/notices/**    공통 (내부에서 역할별 분기)
```

### 5-2. 응답 포맷

모든 응답을 `ApiResponse<T>`로 감쌉니다.

```java
// 성공
{ "success": true, "data": { ... } }

// 실패
{ "success": false, "error": { "code": "STUDENT_NOT_ACCESSIBLE", "message": "..." } }

// 목록
{ "success": true, "data": { "items": [...], "page": 0, "size": 20,
                             "totalElements": 137, "totalPages": 7 } }
```

### 5-3. 시간

- DB 컬럼은 전부 `TIMESTAMPTZ`
- 애플리케이션 타임존 `Asia/Seoul`
- API 입출력은 ISO 8601 (`2026-05-20T14:30:00+09:00`)
- 날짜만 필요한 필드는 `LocalDate` / `DATE`

### 5-4. Enum

DB에는 **문자열로 저장**합니다. `@Enumerated(EnumType.STRING)`을 반드시 붙이세요. ordinal 저장은 순서가 바뀌면 데이터가 깨집니다.

### 5-5. 네이밍

| 대상 | 규칙 | 예 |
|---|---|---|
| DB 테이블·컬럼 | snake_case, 테이블은 복수형 | `submission_photos`, `attend_date` |
| Java 클래스 | PascalCase | `SubmissionPhoto` |
| Java 필드 | camelCase | `attendDate` |
| API 필드 | camelCase | `attendDate` |
| 프론트 파일 | 컴포넌트 PascalCase, 그 외 camelCase | `AttendanceCalendar.tsx` |

---

## 6. 반드시 지킬 것

### 6-1. 권한 검증을 한 곳으로 모으기

`studentId`를 받는 모든 API는 **공통 컴포넌트에서** 접근 권한을 검증합니다. 컨트롤러마다 개별 구현하면 반드시 누락이 발생하고, 학부모가 URL의 숫자만 바꿔 남의 자녀 성적을 보는 사고로 이어집니다.

Phase 2에서 `StudentAccessGuard`를 만들고, 이후 모든 Phase에서 이것만 사용합니다. 자세한 내용은 `03_phase2_auth.md` 참조.

### 6-2. 스키마 변경은 Flyway로만

`spring.jpa.hibernate.ddl-auto`는 `validate`로 고정합니다. `update`나 `create`를 쓰지 마세요. 모든 스키마 변경은 새 마이그레이션 파일(`V{n}__{설명}.sql`)을 추가하는 방식으로 합니다. **기존 마이그레이션 파일은 절대 수정하지 마세요.**

### 6-3. 재원생 판별

학생 목록을 뽑을 때 `students`를 직접 조회하지 말고, 항상 `enrollments`에서 기간 조건을 함께 봅니다.

```sql
WHERE e.class_room_id = :classRoomId
  AND e.joined_at <= :targetDate
  AND (e.left_at IS NULL OR e.left_at > :targetDate)
```

이 조건이 빠지면 퇴원생이 출석부와 숙제 대상자에 계속 남습니다.

**정렬은 `ORDER BY e.student.name`입니다.** `e.student.user.name`으로 쓰면 암묵적 INNER JOIN이
생겨 미가입 학생이 명단에서 통째로 사라집니다. 에러가 나지 않아 발견이 늦습니다.

### 6-3-1. 반 가입 코드는 등록 기간이 끝나면 닫는다

`class_rooms.join_code`는 반 전체가 나눠 쓰는 값이라 **전화번호 대조로 본인을 묶을 수 없습니다.**
개인 코드(`signup_codes`)와 결정적으로 다른 점입니다.

열려 있는 동안은 코드를 아는 사람 누구나 그 반 학생으로 가입해 수업영상·자료실·숙제·공지를
봅니다. 학생이 친구에게 알려주는 일은 실제로 일어납니다.

**승인 대기 단계는 두지 않습니다(확정).** 막는 대신 **선생님이 발견해서 지웁니다.**
그래서 세 가지가 짝으로 있어야 하고, 하나라도 빠지면 나머지가 무의미합니다.

| | 수단 | 어디 |
|---|---|---|
| 차단 | `join_code_active` — 등록 기간이 끝나면 닫는다 | T-3, 가입 처리에서 검사 |
| 탐지 | `sort=recent` 목록 + `recentSignupCount` | T-2, T-1 |
| 제거 | `DELETE /api/teacher/students/{studentId}` | T-2 |

등록 기간에는 매일 최근 가입 상단만 훑고, 모르는 이름이 있으면 지웁니다.
등록 기간이 끝나면 코드를 닫고, 그 뒤로는 신규 가입 자체가 없습니다.

**삭제는 운영 기록이 없을 때만 됩니다.** 출석·제출물·성적·클리닉·시청기록이 하나라도 있거나
학부모가 연결돼 있으면 409 `STUDENT_HAS_RECORDS`입니다. 실제로 다닌 학생은 언제나 `withdraw`입니다.

### 6-4. 파일은 S3에 직접 업로드

숙제 사진과 자료실 파일은 서버를 경유하지 않습니다. presigned URL을 발급해 클라이언트가 S3로 직접 PUT합니다. 서버 경유 방식은 EC2 대역폭을 낭비하고 사진 10장 업로드 시 타임아웃이 발생합니다.

사진은 **업로드 전 브라우저에서 리사이즈**합니다 (장변 1600px, WebP). 원본을 그대로 올리면 연 200GB 이상 누적됩니다.

### 6-5. 미리 행을 만들어 두는 두 곳

| 시점 | 생성 대상 | 이유 |
|---|---|---|
| 숙제 출제 시 | 대상 학생 전원의 `submissions` (`NOT_SUBMITTED`) | 미제출자 조회가 단순 조건 검색이 됨 |
| 출석 확정 시 | 재원생 전원의 `attendances` | 캘린더 조회가 단순해지고 확정 이력이 남음 |

둘 다 나중에 역산하는 방식보다 쿼리가 단순하고, 200명 규모에서 저장 부담은 없습니다.

### 6-6. 출석의 미확정 상태

`lessons.attendance_status`가 `PENDING`인 날은 **아직 확정되지 않은 날**입니다. 출석 집계에 넣지 마세요.

미래 수업일도 `PENDING`이라 이 구분이 없으면 아직 오지 않은 날이 출석으로 잡힙니다. 캘린더에는 상태 없이 수업일만 표시하고, `CONFIRMED`된 날만 색을 입힙니다.

### 6-7. 주차 단위 관리

자료 · 영상 링크 · 시험 결과는 **`2026년 6월 2주차` 형태의 주차 단위**로 관리합니다.

`lessons` · `materials` · `scores` 세 테이블이 `year` · `month` · `week` 컬럼을 공유합니다. 선생님 화면(T-4 · T-8 · T-9)이 주차를 먼저 고르고 그 주의 항목을 입력하는 구조입니다.

**주차는 계산하지 말고 선생님이 고른 값을 저장하세요.** 서버는 날짜에서 기본값만 채웁니다. 달 경계에 걸친 주는 세는 방식이 갈려서 자동 계산을 강제하면 어긋납니다. 주차는 달력 기준이라 **전 반이 같은 번호**를 씁니다.

### 6-8. 온라인 테스트 — 정답을 절대 흘리지 마세요

**시험은 종이로 봅니다.** 학생이 수업에서 받은 종이 시험지를 풀고 답만 웹에 입력하면 자동 채점됩니다.
문제지 파일은 저장하지 않습니다. 서버가 가진 것은 정답 배열과 해설지뿐입니다.

**응시 화면 응답에 정답과 해설지 URL을 넣지 마세요.** `null`로 비우는 것도 안 됩니다.
**필드 자체를 빼세요.** 응답에 들어가는 순간 브라우저 개발자 도구에서 그대로 보입니다.

| DTO | 정답 `correctChoices` | 해설지 URL |
|---|---|---|
| 응시 중 (`OnlineTestTakeResponse`) | ❌ 필드 없음 | ❌ 필드 없음 |
| 제출 후 (`OnlineTestResultResponse`) | ✅ | ✅ |

**채점은 비율로 계산합니다.** `100 / questionCount`를 문항마다 더하면 30문항 만점이 99.99가 됩니다.

```
score = 획득 배점 / 전체 배점 × 100   (소수 둘째 자리 반올림)
```

미체크(`null`)는 오답이고 감점은 없습니다.

**임시 저장은 서버에 합니다.** 출석(6-6)과 반대입니다. 25문항 푸는 데 20~30분이 걸려서
브라우저가 닫히면 처음부터 다시 해야 합니다.

**`score_type`이 채워져 있으면 채점 결과가 `scores`에 자동 반영**되어 P-4 그래프에 얹힙니다.
주간 단어시험을 이 기능으로 치르면 선생님이 성적을 따로 입력할 필요가 없습니다.

**공개 후에는 정답을 수정할 수 없습니다.** 이미 응시한 학생의 점수가 소급 변경됩니다.

### 6-9. 클리닉

정규 수업과 별개인 **보충 수업**입니다. 선생님이 정해 둔 시간대에 **여러 학생이 함께** 참여합니다.

테이블이 3개입니다. 한 시간에 학생이 여러 명이라 `clinics`에 `student_id`를 둘 수 없습니다.

| 테이블 | 담는 것 |
|---|---|
| `clinics` | 시간대 (날짜·시작·종료·정원) |
| `clinic_reservations` | 학생별 신청·배정 + **출석** |
| `clinic_change_requests` | 학생의 변경 요청 → 선생님 승인·거절 |

**신청 경로가 두 가지입니다.** 학생 본인 신청(S-9)은 `assigned_by = NULL`, 선생님 배정(T-13)은 배정자 id가 들어갑니다.

**정원 체크는 조건부 삽입으로 하세요.** "세어 보고 넣기"는 동시 신청 시 정원을 넘깁니다. 삽입 행 수가 0이면 409입니다.

클리닉 출석은 `attendances`가 아니라 `clinic_reservations.attend_status`에 기록합니다. `attendances.class_room_id`가 `NOT NULL`인데 클리닉은 반이 없습니다. 확정 방식은 T-5와 같은 **안 온 학생만 체크**입니다.

**변경 요청 승인은 한 트랜잭션**입니다. 기존 예약을 `MOVED`로 바꾸고 목표 클리닉에 새 `RESERVED` 행을 만듭니다. 목표 클리닉의 정원도 다시 확인하세요.

---

## 7. 구현하지 말 것

아래는 **1차 웹 범위에서 제외**된 항목입니다. 관련 테이블, 엔드포인트, 화면, 라이브러리를 만들지 마세요.

| 제외 항목 | 사유 |
|---|---|
| 공부 시간 자동 기록 | 웹 브라우저는 화면이 꺼지면 측정이 중단됨. 앱 단계에서 재검토 |
| 반 친구 익명 랭킹 | 공부 시간 기록에 종속 |
| 연속 학습일 · 학습 잔디 | 공부 시간 기록에 종속 |
| SMS · 카카오 알림톡 발송 | 건당 과금. 학원장 미승인 |
| 숙제 미제출 자동 독려 | 발송 수단 없음. 앱 푸시로 구현 |
| 결제 · 수강료 관리 | **학원장 확정: 미포함** |
| 이메일 발송 | 요구사항 없음 |
| 소셜 로그인 | 학원 발급 계정만 사용 |
| 관리자(학원장) 별도 계정 | 강사 1인 운영. `TEACHER` 역할로 통합 |
| **수강 후기** | 학원장 요청으로 제외. `reviews` 테이블·API·화면 전부 만들지 마세요 |

### 학부모에게 노출하지 않는 것 (확정)

학부모는 **자녀가 했는지 여부만** 봅니다. 아래를 학부모 API·화면에 넣지 마세요.

| 항목 | 학부모 | 학생 |
|---|---|---|
| 수업 영상 · 수업 레포트(내용·중점사항·다음수업) | ❌ | ✅ S-5 |
| 숙제 사진 · 선생님 피드백 · 숙제 내용 | ❌ | ✅ S-4 |
| 수업 자료실 | ❌ | ✅ S-8 |
| 숙제 제출 **여부** | ✅ P-3 | ✅ |
| 수업 · 클리닉 일정, 출석 | ✅ P-2 | ✅ S-6 |
| 테스트 결과 (주차별 그래프) | ✅ P-4 | ✅ S-7 |

**`GET /api/parent/children/{studentId}/lessons`와 `.../homeworks/{homeworkId}`를 만들지 마세요.**
피드백은 이제 학생에게만 노출됩니다.

### 영상 호스팅 (확정)

**YouTube 미등록(unlisted) 링크를 사용합니다.** 서버는 URL 문자열만 저장하고, 프론트는 iframe으로 임베드합니다.

- 영상 파일 업로드 기능을 만들지 마세요.
- 트랜스코딩, HLS, 스트리밍 서버, 시청 구간 추적을 구현하지 마세요.
- 시청 기록은 "봤는지 여부와 누적 시간"만 기록합니다 (`lesson_views`).

---

## 8. 화면 목록 (총 33개)

Phase별 상세는 각 문서에 있습니다. 여기는 전체 목록입니다.

### 공통 (4)

| ID | 화면 | Phase |
|---|---|---|
| C-1 | 로그인 | 2 |
| C-2 | 비밀번호 변경 | 2 |
| C-3 | 약관 · 개인정보처리방침 | 2 |
| C-4 | 회원가입 (반 코드 / 개인 코드) | 2 |

### 학부모 (5)

**학부모는 "했는지 여부"만 봅니다.** 수업 영상·레포트, 숙제 사진·피드백, 자료실은 노출하지 않습니다.

| ID | 화면 | Phase |
|---|---|---|
| P-1 | 포털 홈 | 7 |
| P-2 | 수업 · 클리닉 일정 (+출석) | 4 |
| P-3 | 숙제 제출 현황 (여부만) | 5 |
| P-4 | 테스트 결과 (주차별 그래프) | 6 |
| P-5 | 내 정보 (자녀 목록, 연락처 변경) | 2 |

### 학생 (10)

| ID | 화면 | Phase |
|---|---|---|
| S-1 | 홈 | 7 |
| S-2 | 숙제 목록 | 5 |
| S-3 | 숙제 제출 | 5 |
| S-4 | 숙제 상세 · 피드백 | 5 |
| S-5 | 수업영상 및 레포트 | 6 |
| S-6 | 출석 현황 | 4 |
| S-7 | 내 정보 · 성적 | 6 |
| S-8 | 수업 자료실 | 7 |
| S-9 | 클리닉 신청 | 4 |
| S-10 | 온라인 테스트 응시 | 6 |

### 선생님 (14)

| ID | 화면 | Phase |
|---|---|---|
| T-1 | 대시보드 | 7 |
| T-2 | 학생 관리 | 3 |
| T-3 | 반 관리 | 3 |
| T-4 | 주차별 수업 관리 | 3 |
| T-5 | 출석 확정 | 4 |
| T-6 | 숙제 출제 | 5 |
| T-7 | 숙제 확인 및 피드백 | 5 |
| T-8 | 주차별 성적 입력 | 6 |
| T-9 | 주차별 자료실 관리 | 7 |
| T-10 | 공지 관리 | 7 |
| T-11 | 시험 일정 관리 | 6 |
| T-12 | 학년 일괄 진급 | 3 |
| T-13 | 클리닉 관리 | 4 |
| T-14 | 온라인 테스트 관리 | 6 |

---

## 9. 진행 방식

각 Phase 문서는 다음 구조입니다.

1. **목표** — 이 Phase가 끝나면 무엇이 동작하는지
2. **구현 항목** — 백엔드 / 프론트엔드
3. **API 명세** — 요청·응답 예시
4. **주의사항** — 이 Phase에서 자주 틀리는 지점
5. **완료 조건 (DoD)** — 체크리스트
6. **하지 말 것** — 범위 밖 항목

**완료 조건을 모두 충족한 뒤 다음 Phase로 넘어가세요.** 여러 Phase를 동시에 진행하면 스키마와 권한 검증이 어긋납니다.
