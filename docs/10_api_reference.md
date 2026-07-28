# API 명세서 (전체)

이 문서는 프로젝트 전체 엔드포인트의 **단일 레퍼런스**입니다. 각 Phase 문서에 흩어져 있는 API를 한곳에 모았습니다.

- 구현 순서와 화면 맥락 → 해당 Phase 문서
- 엔드포인트·요청·응답 확인 → 이 문서

경로가 두 곳에서 다르면 **이 문서가 기준**입니다.

**총 129개 엔드포인트.** 인증 6 / 선생님 86 / 학생 25 / 학부모 9 / 공통 3

선생님 86개 내역: 대시보드·학교 3 / 학생 **10** / 반 **10** / 수업 8 / 출석 4 / **온라인 테스트 8** / **클리닉 10** / 숙제 14 / 성적·시험 9 / 자료·공지 10

반 코드 가입으로 두 개가 늘었습니다.

| 추가 | 이유 |
|---|---|
| `POST /teacher/class-rooms/{classRoomId}/join-code` | 가입 코드 재발급·여닫기 |
| `DELETE /teacher/students/{studentId}` | 반 코드로 들어온 제3자 삭제 (운영 기록 없을 때만) |

**코드 유효성만 미리 확인하는 엔드포인트는 만들지 않습니다** — 무작위 대입의 정답 판별기가 됩니다.

**학부모는 "했는지 여부"만 봅니다.** 수업 영상·레포트, 숙제 사진·피드백, 자료실 엔드포인트를
`/api/parent/**`에 만들지 마세요. 후기 기능은 1차 범위에서 제외되었습니다.

---

## 1. 공통 규약

### 1-1. 기본

| 항목 | 값 |
|---|---|
| Base URL | `https://{domain}/api` |
| 인증 | `Authorization: Bearer {accessToken}` |
| 로그인 아이디 | **전화번호** (하이픈 없는 숫자, `01011112222`) |
| Content-Type | `application/json; charset=utf-8` |
| 시간 | ISO 8601, `Asia/Seoul` (`2026-05-20T14:30:00+09:00`) |
| 날짜 | `2026-05-20` |
| 시각 | `19:00` |

### 1-2. 응답 포맷

```jsonc
// 성공
{ "success": true, "data": { } }

// 성공 (본문 없음)
{ "success": true, "data": null }

// 실패
{ "success": false, "error": { "code": "STUDENT_NOT_ACCESSIBLE", "message": "해당 학생 정보에 접근할 수 없습니다." } }

// 목록
{ "success": true, "data": {
    "items": [], "page": 0, "size": 20, "totalElements": 137, "totalPages": 7 } }
```

### 1-3. 페이징

| 파라미터 | 기본값 | 비고 |
|---|---|---|
| `page` | 0 | 0-based |
| `size` | 20 | 최대 100 |

**페이징이 없는 목록 API (15개)** — 건수가 구조적으로 작아 전체를 반환합니다.
`PageResponse`로 감싸지 말고 배열을 그대로 `data`에 담으세요.

| 엔드포인트 | 상한 |
|---|---|
| `GET /teacher/homeworks/{homeworkId}/submissions` | 반 인원 30명 내외 |
| `GET /teacher/lessons/{lessonId}/attendance` | 반 인원 30명 내외 |
| `GET /teacher/lessons/{lessonId}/views` | 반 인원 30명 내외 |
| `GET /teacher/class-rooms/{classRoomId}/students` | 반 인원 30명 내외 |
| `GET /parent/children` | 자녀 수 (보통 1~2) |
| `GET /teacher/schools` | 2개 |
| `GET /teacher/attendance/pending` | 미확정 수업 수 |
| `GET /teacher/homeworks/pending` | 미확인 숙제 수 |
| `GET /student/exam-schedules` | 학년당 연 4건 |
| `GET /parent/children/{studentId}/exam-schedules` | 학년당 연 4건 |
| `GET /teacher/clinics` | 기간 조회. 주 단위로 봄 |
| `GET /teacher/clinics/{clinicId}/reservations` | 클리닉 정원만큼 |
| `GET /teacher/clinic-change-requests` | 대기 중인 요청만 |
| `GET /student/clinics` | 기간 조회 |
| `GET /parent/children/{studentId}/clinics` | 기간 조회 |

이 목록에 없는 목록 API는 **전부 `PageResponse`** 입니다. 새 목록 API를 만들 때
페이징을 뺄 생각이면 여기에 상한 근거와 함께 먼저 추가하세요.

### 1-4. 에러 코드

| HTTP | code | 상황 |
|---|---|---|
| 400 | `VALIDATION_FAILED` | 필수값 누락, 형식 오류 |
| 400 | `INVITE_CODE_INVALID` | 초대코드 없음·만료·전화번호 불일치 |
| 400 | `INVITE_CODE_USED` | 이미 사용된 초대코드 |
| 400 | `UNSUPPORTED_FILE_TYPE` | 허용되지 않은 형식 |
| 401 | `INVALID_CREDENTIALS` | 로그인 실패 |
| 401 | `TOKEN_EXPIRED` | 액세스 토큰 만료 |
| 401 | `TOKEN_INVALID` | 토큰 위조·손상 |
| 403 | `ROLE_NOT_ALLOWED` | 역할에 없는 API 호출 |
| 403 | `STUDENT_NOT_ACCESSIBLE` | 본인·자녀가 아닌 학생 조회 |
| 403 | `PASSWORD_CHANGE_REQUIRED` | 초기 비밀번호(`0000`) 상태에서 다른 API 호출 |
| 404 | `RESOURCE_NOT_FOUND` | 대상 없음 |
| 409 | `DUPLICATE_RESOURCE` | 중복 (전화번호=`login_id`, 수업일, 시험일정, 클리닉 중복 신청) |
| 409 | `CLINIC_CAPACITY_EXCEEDED` | 클리닉 정원 초과 |
| 409 | `SUBMISSION_EXISTS` | 제출물이 있는 숙제·테스트 삭제 시도 |
| 409 | `STUDENT_HAS_RECORDS` | 운영 기록이 있는 학생 삭제 시도 |
| 409 | `ALREADY_CONFIRMED` | 정의만 존재, 현재 미사용 |
| 409 | `DUE_DATE_PASSED` | 정의만 존재, 현재 미사용 |
| 409 | `PHOTO_LIMIT_EXCEEDED` | 사진 10장 초과 |
| 413 | `FILE_TOO_LARGE` | 용량 초과 |
| 500 | `INTERNAL_ERROR` | 서버 오류 |

`ALREADY_CONFIRMED`와 `DUE_DATE_PASSED`는 enum에 정의만 하고 던지지 않습니다. 출석 재확정과 지각 제출은 정상 흐름입니다.

**409를 전부 `DUPLICATE_RESOURCE`로 뭉치지 마세요.** 프론트가 `error.code`로 문구를 고릅니다.
"정원이 모두 찼습니다"와 "이미 신청하셨습니다"는 같은 409지만 다른 화면입니다.

### 1-5. 권한 모델

| 경로 | 역할 |
|---|---|
| `/api/auth/**` | 일부 비로그인 허용 |
| `/api/teacher/**` | `TEACHER` |
| `/api/student/**` | `STUDENT` |
| `/api/parent/**` | `PARENT` |
| `/api/notices/**` | 로그인 사용자 (내부 분기) |

**`studentId`를 받는 모든 API는 `StudentAccessGuard.requireAccessible(studentId)`를 통과합니다.**

| 역할 | 접근 범위 |
|---|---|
| `TEACHER` | 전체 |
| `STUDENT` | 본인만 |
| `PARENT` | `students.parent_id`가 자신인 자녀만 |

`submissionId`, `lessonId`, `scoreId` 같은 간접 참조도 해당 리소스가 속한 학생을 찾아 동일하게 검증합니다.

### 1-6. 경로 변수 명명

축약하지 않고 리소스명을 붙입니다. `{id}` 금지.

```
{studentId} {classRoomId} {lessonId} {homeworkId} {submissionId}
{photoId} {attendanceId} {scoreId} {examScheduleId} {materialId}
{noticeId} {templateId} {clinicId} {requestId}
```

---

## 2. 엔드포인트 색인

### 인증 (6)

| Method | Path | 인증 |
|---|---|---|
| POST | `/auth/login` | — |
| POST | `/auth/refresh` | 쿠키 |
| POST | `/auth/logout` | 필요 |
| GET | `/auth/me` | 필요 |
| PATCH | `/auth/password` | 필요 |
| POST | `/auth/signup` | — |

**회원가입은 `/auth/signup` 하나입니다.** 반 코드(학생 자가 가입, 주 경로)와
개인 코드(`target_role`로 학생/학부모 판별) 세 가지를 서버가 코드로 구분합니다.
사용자가 역할을 고르지 않습니다.

**비밀번호 찾기 엔드포인트는 없습니다.** 이메일·SMS 발송 수단이 모두 범위 밖이라
자동 재설정 경로를 만들 수 없습니다. 분실 시 선생님이
`POST /teacher/students/{studentId}/reset-password`로 초기화합니다.

### 공통 (3)

| Method | Path |
|---|---|
| GET | `/health` |
| GET | `/notices` |
| GET | `/notices/{noticeId}` |

자료실은 공통이 아니라 **학생 전용**(`/student/materials`)으로 옮겼습니다. 후기(`/reviews`)는 삭제되었습니다.

### 학부모 (9)

| Method | Path |
|---|---|
| GET | `/parent/children` |
| GET | `/parent/children/{studentId}/home` |
| GET | `/parent/children/{studentId}/attendances` |
| GET | `/parent/children/{studentId}/clinics` |
| GET | `/parent/children/{studentId}/homeworks` |
| GET | `/parent/children/{studentId}/scores` |
| GET | `/parent/children/{studentId}/exam-schedules` |
| GET | `/parent/me` |
| PATCH | `/parent/me` |

**아래 4개를 만들지 마세요.** 학부모 범위에서 제외된 것들입니다.

| 만들지 않는 경로 | 이유 |
|---|---|
| `/parent/children/{studentId}/lessons` | 수업 영상·레포트는 학생만 |
| `/parent/children/{studentId}/lessons/{lessonId}` | 위와 동일 |
| `/parent/children/{studentId}/homeworks/{homeworkId}` | 사진·피드백 노출 금지. 목록의 제출 여부까지만 |
| `/parent/reviews` | 후기 기능 삭제 |

### 학생 (25)

| Method | Path |
|---|---|
| GET | `/student/home` |
| GET | `/student/me` |
| GET | `/student/lessons` |
| GET | `/student/lessons/{lessonId}` |
| POST | `/student/lessons/{lessonId}/view` |
| GET | `/student/attendances` |
| GET | `/student/scores` |
| GET | `/student/exam-schedules` |
| GET | `/student/homeworks` |
| GET | `/student/homeworks/{homeworkId}` |
| POST | `/student/homeworks/{homeworkId}/photos/upload-url` |
| POST | `/student/homeworks/{homeworkId}/photos` |
| DELETE | `/student/homeworks/{homeworkId}/photos/{photoId}` |
| POST | `/student/homeworks/{homeworkId}/submit` |
| GET | `/student/materials` |
| GET | `/student/materials/{materialId}/download-url` |
| GET | `/student/clinics` |
| POST | `/student/clinics/{clinicId}/reservation` |
| DELETE | `/student/clinics/{clinicId}/reservation` |
| POST | `/student/clinic-change-requests` |
| GET | `/student/online-tests` |
| GET | `/student/online-tests/{testId}` |
| PUT | `/student/online-tests/{testId}/answers` |
| POST | `/student/online-tests/{testId}/submit` |
| GET | `/student/online-tests/{testId}/result` |

`POST /student/reviews`는 **만들지 않습니다.** 후기 기능이 1차 범위에서 제외되었습니다.

### 선생님 (84)

**대시보드·학교 (3)**

| Method | Path |
|---|---|
| GET | `/teacher/dashboard` |
| GET | `/teacher/schools` |
| POST | `/teacher/schools` |

**학생 (10)**

| Method | Path |
|---|---|
| GET | `/teacher/students` |
| POST | `/teacher/students` |
| GET | `/teacher/students/{studentId}` |
| PATCH | `/teacher/students/{studentId}` |
| POST | `/teacher/students/{studentId}/withdraw` |
| POST | `/teacher/students/{studentId}/restore` |
| POST | `/teacher/students/{studentId}/signup-code` |
| POST | `/teacher/students/{studentId}/reset-password` |
| DELETE | `/teacher/students/{studentId}` |
| POST | `/teacher/students/promote` |

**반 (10)**

| Method | Path |
|---|---|
| GET | `/teacher/class-rooms` |
| POST | `/teacher/class-rooms` |
| GET | `/teacher/class-rooms/{classRoomId}` |
| PATCH | `/teacher/class-rooms/{classRoomId}` |
| DELETE | `/teacher/class-rooms/{classRoomId}` |
| POST | `/teacher/class-rooms/{classRoomId}/close` |
| POST | `/teacher/class-rooms/{classRoomId}/join-code` |
| GET | `/teacher/class-rooms/{classRoomId}/students` |
| POST | `/teacher/class-rooms/{classRoomId}/students` |
| DELETE | `/teacher/class-rooms/{classRoomId}/students/{studentId}` |

**수업 (8)**

| Method | Path |
|---|---|
| GET | `/teacher/lessons` |
| POST | `/teacher/lessons` |
| POST | `/teacher/lessons/bulk` |
| GET | `/teacher/lessons/{lessonId}` |
| PATCH | `/teacher/lessons/{lessonId}` |
| DELETE | `/teacher/lessons/{lessonId}` |
| POST | `/teacher/lessons/{lessonId}/publish` |
| GET | `/teacher/lessons/{lessonId}/views` |

**출석 (4)**

| Method | Path |
|---|---|
| GET | `/teacher/attendance/pending` |
| GET | `/teacher/lessons/{lessonId}/attendance` |
| POST | `/teacher/lessons/{lessonId}/attendance/confirm` |
| PATCH | `/teacher/attendances/{attendanceId}` |

**임시 저장 엔드포인트는 없습니다.** 확정 전 중간 상태는 프론트 로컬 상태로만 유지합니다.
서버에 쓰면 `attendances` 행이 미확정 상태로 생겨 캘린더의 `PENDING` 판정이 깨집니다.

**온라인 테스트 (8)**

| Method | Path |
|---|---|
| GET | `/teacher/online-tests` |
| POST | `/teacher/online-tests` |
| GET | `/teacher/online-tests/{testId}` |
| PATCH | `/teacher/online-tests/{testId}` |
| DELETE | `/teacher/online-tests/{testId}` |
| POST | `/teacher/online-tests/upload-url` |
| POST | `/teacher/online-tests/{testId}/publish` |
| GET | `/teacher/online-tests/{testId}/results` |

**클리닉 (10)**

| Method | Path |
|---|---|
| GET | `/teacher/clinics` |
| POST | `/teacher/clinics` |
| PATCH | `/teacher/clinics/{clinicId}` |
| DELETE | `/teacher/clinics/{clinicId}` |
| GET | `/teacher/clinics/{clinicId}/reservations` |
| POST | `/teacher/clinics/{clinicId}/students` |
| DELETE | `/teacher/clinics/{clinicId}/students/{studentId}` |
| POST | `/teacher/clinics/{clinicId}/attendance/confirm` |
| GET | `/teacher/clinic-change-requests` |
| POST | `/teacher/clinic-change-requests/{requestId}/decide` |

**숙제 (14)**

| Method | Path |
|---|---|
| GET | `/teacher/homework-templates` |
| POST | `/teacher/homework-templates` |
| DELETE | `/teacher/homework-templates/{templateId}` |
| GET | `/teacher/homeworks` |
| POST | `/teacher/homeworks` |
| GET | `/teacher/homeworks/pending` |
| GET | `/teacher/homeworks/{homeworkId}` |
| PATCH | `/teacher/homeworks/{homeworkId}` |
| DELETE | `/teacher/homeworks/{homeworkId}` |
| GET | `/teacher/homeworks/{homeworkId}/submissions` |
| GET | `/teacher/submissions/{submissionId}` |
| POST | `/teacher/submissions/{submissionId}/feedback` |
| PATCH | `/teacher/submissions/{submissionId}/feedback` |
| POST | `/teacher/submissions/{submissionId}/check` |

**성적·시험 (9)**

| Method | Path |
|---|---|
| GET | `/teacher/students/{studentId}/scores` |
| POST | `/teacher/students/{studentId}/scores` |
| POST | `/teacher/scores/bulk` |
| PATCH | `/teacher/scores/{scoreId}` |
| DELETE | `/teacher/scores/{scoreId}` |
| GET | `/teacher/exam-schedules` |
| POST | `/teacher/exam-schedules` |
| PATCH | `/teacher/exam-schedules/{examScheduleId}` |
| DELETE | `/teacher/exam-schedules/{examScheduleId}` |

**자료·공지 (10)**

| Method | Path |
|---|---|
| GET | `/teacher/materials` |
| POST | `/teacher/materials/upload-url` |
| POST | `/teacher/materials` |
| PATCH | `/teacher/materials/{materialId}` |
| DELETE | `/teacher/materials/{materialId}` |
| GET | `/teacher/notices` |
| POST | `/teacher/notices` |
| PATCH | `/teacher/notices/{noticeId}` |
| POST | `/teacher/notices/{noticeId}/publish` |
| DELETE | `/teacher/notices/{noticeId}` |

후기 엔드포인트(`/teacher/reviews` 3개)는 삭제되었습니다.

---

## 3. 인증

### POST `/auth/login`

**`loginId`는 전화번호입니다** (하이픈 없는 숫자). 프론트·서버 양쪽에서 정규화하세요.

```jsonc
// Req
{ "loginId": "01012345678", "password": "********" }

// Res 200
{ "success": true, "data": {
    "accessToken": "eyJ...",
    "user": { "id": 12, "name": "홍길동", "role": "PARENT", "mustChangePassword": false } } }
```

Refresh 토큰은 본문이 아니라 쿠키로 내려갑니다.

```
Set-Cookie: refreshToken=eyJ...; HttpOnly; Secure; SameSite=Lax; Path=/api/auth; Max-Age=1209600
```

실패는 401 `INVALID_CREDENTIALS`. **아이디 없음과 비밀번호 틀림을 구분하지 마세요.** 계정 존재 여부가 노출됩니다.

### POST `/auth/refresh`

본문 없음. 쿠키의 refresh 토큰으로 재발급.

```jsonc
{ "success": true, "data": { "accessToken": "eyJ..." } }
```

만료·폐기 시 401 `TOKEN_EXPIRED`.

### POST `/auth/logout`

`refresh_tokens.revoked_at` 기록 + 쿠키 만료.

### GET `/auth/me`

```jsonc
{ "success": true, "data": {
    "id": 12, "name": "홍길동", "role": "PARENT",
    "phone": "010-****-1234", "mustChangePassword": false } }
```

전화번호는 **서버에서 마스킹**해 내려줍니다.

### PATCH `/auth/password`

```jsonc
{ "currentPassword": "********", "newPassword": "********" }
```

성공 시 해당 사용자의 모든 refresh 토큰을 폐기합니다. 정책은 8자 이상만.

### POST `/auth/signup`

비로그인 호출. **세 가지 가입이 이 엔드포인트 하나를 씁니다.**

| 코드 | 누가 | 출처 |
|---|---|---|
| **반 코드** (`class_rooms.join_code`) | 학생 | 선생님이 수업에서 반 전체에 구두 전달 |
| 개인 코드 `target_role=STUDENT` | 학생 | 선생님이 T-2에서 개별 발급 (보조 경로) |
| 개인 코드 `target_role=PARENT` | 학부모 | 자녀 가입 시 자동 발급, 선생님이 전달 |

**서버가 코드를 보고 판별합니다.** `signup_codes.code`를 먼저 찾고, 없으면
`class_rooms.join_code`를 찾습니다. 두 값은 겹치지 않게 발급됩니다.

`loginId`와 비밀번호를 받지 않습니다. **`phone`이 로그인 아이디**가 되고,
비밀번호는 **`0000`으로 생성**됩니다.

#### 반 코드 (학생 자가 가입, 주 경로)

```jsonc
// Req
{ "code": "HK7F2Q", "name": "서동환",
  "phone": "01011112222", "parentPhone": "01098765432" }
// 학교·학년은 받지 않는다. 반 코드가 이미 알고 있어 서버가 반에서 복사한다.

// Res 200
{ "success": true, "data": {
    "role": "STUDENT", "loginId": "01011112222",
    "studentName": "서동환", "classRoomName": "고2 심화반",
    "initialPassword": "0000" } }
```

**처리 (하나의 트랜잭션)**

1. `class_rooms`에서 `join_code` 조회 → 없으면 400 `INVITE_CODE_INVALID`
2. `join_code_active = false`거나 `status = 'CLOSED'` → 400 `INVITE_CODE_INVALID`
3. 두 번호 정규화. `phone == parentPhone`이면 409 `DUPLICATE_RESOURCE`
4. `phone`이 이미 `users.login_id`면 409 `DUPLICATE_RESOURCE`
5. `students` 생성 (`name`, `school_id`, `grade`)
6. `users`(STUDENT, `login_id = phone`, `BCrypt("0000")`, `must_change_password = true`) → `students.user_id` 연결
7. `enrollments` 생성 (그 반, `joined_at = 오늘`)
8. `signup_codes`(PARENT, `phone = parentPhone`, 7일) **1장 자동 발급**

**8번이 학부모 연결의 시작점입니다.** 선생님이 T-2에서 확인해 학부모에게 전달합니다.
**학부모는 반 코드를 쓸 수 없습니다** — 어느 학생의 부모인지 알 수 없기 때문입니다.

`name`은 **`students.name`**에 저장합니다. 학교·학년은 학생이 고릅니다(반에는 없는 정보).

> ⚠️ **반 코드에는 전화번호 대조가 없습니다.** 20명이 나눠 쓰는 값이라 특정인에게 묶을 수
> 없습니다. **코드를 아는 사람은 누구나 가입합니다.** 2번 검사(`join_code_active`)가
> 유일한 방어선이므로 빠뜨리지 마세요. 등록 기간이 끝나면 선생님이 T-3에서 닫습니다.

#### 개인 코드 (기존 방식, 보조 경로)

```jsonc
// Req   (name은 PARENT만 필수. STUDENT는 선생님이 등록한 이름 사용)
{ "code": "K7F2QX", "phone": "01012345678", "name": "홍길동" }

// Res 200
{ "success": true, "data": {
    "role": "PARENT", "loginId": "01012345678",
    "studentName": "서동환", "initialPassword": "0000" } }
```

**공통 처리 (1~4)**

1. `signup_codes`에서 `code` 조회 → 없으면 `INVITE_CODE_INVALID`
2. `used_at` 있으면 `INVITE_CODE_USED`
3. `expires_at` 경과 시 `INVITE_CODE_INVALID`
4. `phone` 불일치 시 `INVITE_CODE_INVALID`

**`target_role = STUDENT`**

5. `phone`이 이미 `login_id`면 409 `DUPLICATE_RESOURCE`
6. `users`(STUDENT, `login_id = phone`, `BCrypt("0000")`, `must_change_password = true`) 생성
7. `students.user_id` 연결, `used_at` 기록

**`target_role = PARENT`**

5. **같은 phone의 `PARENT` 계정이 있으면 새로 만들지 않고 자녀만 추가 연결** (다자녀)
6. 같은 phone이 `STUDENT`·`TEACHER` 역할이면 409 `DUPLICATE_RESOURCE`
7. 없으면 `users`(PARENT, `login_id = phone`, `BCrypt("0000")`, `must_change_password = true`) + `parents` 생성
8. `students.parent_id` 설정, `used_at` 기록

PARENT 5번이 다자녀 처리의 핵심입니다. 빠지면 학부모가 아이마다 계정을 따로 만듭니다.
**다자녀 추가 연결 시 기존 비밀번호를 `0000`으로 되돌리지 마세요.**

실패 사유를 세분화하지 마세요. 4번을 "전화번호가 다릅니다"로 알려주면 코드만 가진 사람이 번호를 추측할 수 있습니다.
**반 코드 실패도 같은 `INVITE_CODE_INVALID`입니다.** 어느 종류의 코드가 틀렸는지 알려주지 마세요.

**코드 유효성만 확인해 주는 엔드포인트를 만들지 마세요.** 코드 하나로 유효 여부를 알려주면
무작위 대입의 정답 판별기가 됩니다. 지금은 코드와 전화번호를 함께 제출해야 결과를 알 수 있습니다.
C-4 화면은 반 코드 폼과 개인 코드 폼을 나눠 두는 것으로 충분합니다.

**`0000` 상태에서는 비밀번호 변경 외 모든 API가 403 `PASSWORD_CHANGE_REQUIRED`입니다.**
전원이 아는 초기값이라, 가입 후 방치된 계정으로 남의 성적·피드백이 새는 것을 막는 장치입니다.
허용 경로는 `PATCH /auth/password`, `GET /auth/me`, `POST /auth/logout` 셋뿐입니다.

---

## 4. 선생님 API

### 4-1. GET `/teacher/dashboard`

```jsonc
{ "success": true, "data": {
  "today": { "date": "2026-05-20", "lessons": [
    { "lessonId": 501, "classRoomName": "고2 심화반", "startTime": "19:00",
      "studentCount": 20, "attendanceStatus": "PENDING", "contentWritten": false } ] },
  "todo": {
    "pendingAttendanceCount": 3, "awaitingCheckCount": 12,
    "unwrittenLessonCount": 2, "unsignedStudentCount": 12,
    "unlinkedParentCount": 7, "pendingClinicRequestCount": 2 },
  "stats": { "totalStudents": 197, "activeClassRooms": 9 } } }
```

### 4-2. 학생

**GET `/teacher/students`** — `schoolId` `grade` `classRoomId` `status` `keyword` `sort` `page` `size`

`sort`: `name`(기본) | `recent`(`students.created_at DESC`). 응답에 `createdAt` 포함.

**`recent` 정렬이 반 코드 제3자 탐지 경로입니다.** 등록 기간에는 매일 상단 20명만 훑고,
모르는 이름이 있으면 `DELETE`로 지웁니다. 200명을 이름순으로 놓고 찾는 것은 불가능합니다.

```jsonc
{ "items": [ {
  "studentId": 88, "name": "서동환",
  "schoolName": "A고등학교", "grade": 2,
  "classRooms": ["고2 심화반", "썸머 집중반"],
  "studentPhone": "01011112222", "studentSignedUp": true,
  "parentPhone": "01098765432", "parentLinked": false,
  "status": "ENROLLED" } ] }
```

`studentSignedUp`(= `user_id IS NOT NULL`)과 `parentLinked`(= `parent_id IS NOT NULL`)가
`false`인 학생을 목록에서 강조합니다. 미가입자에게 코드를 다시 알려주는 것이 선생님의 실제 업무입니다.

**POST `/teacher/students`**

```jsonc
// Req
{ "name": "서동환", "schoolId": 1, "grade": 2,
  "studentPhone": "01011112222", "parentPhone": "01098765432",
  "memo": null, "classRoomIds": [3, 7] }

// Res 200
{ "studentId": 88,
  "signupCodes": {
    "student": { "code": "K7F2QX", "phone": "01011112222",
                 "expiresAt": "2026-08-03T23:59:59+09:00" },
    "parent":  { "code": "M4T8BW", "phone": "01098765432",
                 "expiresAt": "2026-08-03T23:59:59+09:00" } } }
```

한 트랜잭션: `students`(`user_id=null`, `parent_id=null`) → `enrollments`
→ `signup_codes` **2행**(`STUDENT` / `PARENT`).

**`users` 행을 만들지 않습니다.** 계정은 당사자가 `/auth/signup`으로 가입할 때 생깁니다.
선생님은 200명을 먼저 다 입력해 두고 출석·숙제를 운영할 수 있습니다.

**전화번호 칸이 두 개입니다.** 각각 해당 코드의 대조 번호이자, 가입 후 그 사람의 로그인 아이디가 됩니다.
두 번호가 같거나 이미 쓰이고 있으면 409 `DUPLICATE_RESOURCE`.

`loginId`·`initialPassword`는 요청에 없습니다. 가입 시 입력한 번호가 아이디가 되고,
비밀번호는 서버가 `0000`으로 설정합니다.

**POST `/teacher/students/{studentId}/withdraw`**

```jsonc
{ "withdrawnAt": "2026-07-31" }
```

`students.status=WITHDRAWN` + `users.status=INACTIVE`(미가입이면 생략) + 진행 중 `enrollments.left_at` 기록
+ 미사용 `signup_codes` 폐기. 다른 재원 자녀가 없으면 학부모도 `INACTIVE`. **데이터는 삭제하지 않습니다.**

**POST `/teacher/students/{studentId}/signup-code`**

회원가입 코드 재발급. 미사용 코드가 있으면 폐기 후 재발급.

```jsonc
// Req  (phone 생략 시 기존 번호 유지)
{ "target": "STUDENT", "phone": "01011112222" }

// Res
{ "target": "STUDENT", "code": "K7F2QX",
  "phone": "01011112222", "expiresAt": "2026-08-03T23:59:59+09:00" }
```

`target`: `STUDENT` | `PARENT`. **이미 가입을 마친 대상이면 400** — 그 상황에 필요한 것은
코드가 아니라 `reset-password`입니다.

6자리, 유효 7일. **혼동 문자 제외: `0` `O` `1` `I` `L`** — 구두 전달 후 입력하는 값입니다.

```java
private static final String CHARS = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
```

**POST `/teacher/students/{studentId}/reset-password`**

비밀번호 분실 시 유일한 복구 경로입니다. 발송 수단이 없으므로 선생님이 직접 초기화합니다.

```jsonc
// Req — newPassword를 주면 그 값으로, 생략(null)하면 서버가 8자리 임시 비밀번호 생성
{ "target": "STUDENT", "newPassword": null }

// Res
{ "target": "STUDENT", "loginId": "01011112222", "temporaryPassword": "7K2M9QXF" }
```

`target`: `STUDENT`(기본) | `PARENT`. `PARENT`면 해당 학생에 연결된 학부모 계정을 초기화합니다.

**아직 가입하지 않은 대상이면 400 `VALIDATION_FAILED`.** 계정이 없어 초기화할 것이 없습니다.
그 경우 필요한 것은 `signup-code` 재발급입니다.

**처리**

1. `users.password_hash` 갱신 (BCrypt)
2. `users.must_change_password = true` — 다음 로그인 시 변경 화면으로 강제 이동
3. 해당 사용자의 refresh 토큰 **전부 폐기** (분실이 곧 유출일 수 있음)

`temporaryPassword`는 **이 응답에서 한 번만** 내려갑니다. 저장하거나 다시 조회할 수 없습니다.
초대코드와 같은 문자셋을 씁니다 (`0 O 1 I L` 제외) — 구두로 전달하는 값이기 때문입니다.

**DELETE `/teacher/students/{studentId}`** — 반 코드로 들어온 제3자 삭제

```jsonc
{ "deletedStudentId": 91, "deletedUser": true, "deletedEnrollments": 1 }
```

**운영 기록이 하나라도 있으면 409 `STUDENT_HAS_RECORDS`** → `withdraw`를 안내하세요.
차단 대상: `attendances`, `status <> 'NOT_SUBMITTED'`인 `submissions`, `scores`,
`online_test_submissions`, `clinic_reservations`, `lesson_views`, 그리고 `parent_id IS NOT NULL`.

**`NOT_SUBMITTED`만 있는 `submissions`는 막지 않습니다.** 출제 시 전원의 행이 미리 생기므로
"행이 있으면 409"로 구현하면 아무도 지울 수 없습니다. 숙제 삭제와 같은 함정입니다.

삭제 순서(한 트랜잭션): `submissions` → `enrollments` → `signup_codes` → `students` → `users`.
FK 순서를 지키세요.

| 상황 | 써야 할 것 |
|---|---|
| 반 코드로 들어온 모르는 사람 | `DELETE` |
| 실제로 다녔던 학생이 그만둠 | `POST /withdraw` |

**실제 학생에게 쓰지 마세요.** 되돌릴 수 없고 과거 통계가 사라집니다.

**POST `/teacher/students/{studentId}/restore`** — 퇴원 취소

```jsonc
// Req 없음
// Res
{ "studentId": 88, "status": "ENROLLED", "restoredEnrollments": 0 }
```

`withdraw`의 역연산이지만 **완전한 대칭은 아닙니다.** 되돌리는 것과 되돌리지 않는 것을
구분하세요.

| `withdraw`가 한 것 | `restore`가 되돌리는가 |
|---|---|
| `students.status = 'WITHDRAWN'`, `withdrawn_at` 기록 | ✅ `ENROLLED`로, `withdrawn_at = null` |
| `users.status = 'INACTIVE'` | ✅ `ACTIVE`로 (미가입이면 건너뜀) |
| 학부모 계정 `INACTIVE` | ✅ 자녀가 하나라도 재원이면 `ACTIVE`로 |
| `enrollments.left_at` 기록 | ❌ **되돌리지 않습니다** |
| 미사용 `signup_codes` 폐기 | ❌ **되돌리지 않습니다** |

**반 배정을 자동 복구하지 마세요.** 퇴원 후 시간이 지나 그 반이 끝났거나 다른 반으로
갈 수도 있습니다. 선생님이 T-3에서 다시 배정합니다. `restoredEnrollments`는 항상 `0`이며,
화면에 "반 배정을 다시 해 주세요"를 띄우세요.

**코드도 되살리지 마세요.** 만료됐을 가능성이 높습니다. 필요하면 `signup-code`로 재발급합니다.

이미 `ENROLLED`인 학생에게 호출하면 400 `VALIDATION_FAILED`.

**POST `/teacher/students/promote`**

```jsonc
// Req
{ "schoolId": 1, "dryRun": true }

// Res
{ "dryRun": true, "grade1to2": 31, "grade2to3": 28, "grade3Graduating": 25,
  "classRoomsPromoted": [
    { "classRoomId": 3, "name": "고1 기초반", "gradeFrom": 1, "gradeTo": 2 },
    { "classRoomId": 5, "name": "고2 심화반", "gradeFrom": 2, "gradeTo": 3 }
  ] }
```

3학년은 **자동 퇴원시키지 않고** 대상 목록만 반환합니다. `dryRun`은 반드시 구현하세요.

**학생과 반의 학년을 같은 트랜잭션에서 함께 올립니다.** 학생만 올리면 다음 날부터
모든 학생이 자기 반과 학년이 어긋나 배정 검증(4-3)에 걸립니다.

| 대상 | 1학년 | 2학년 | 3학년 |
|---|---|---|---|
| `students.grade` | → 2 | → 3 | 그대로 (졸업 대상 집계만) |
| `class_rooms.grade` (`status='ACTIVE'`) | → 2 | → 3 | **그대로** |

**3학년 반을 4로 올리지 마세요.** `ck_class_rooms_grade CHECK (grade BETWEEN 1 AND 3)`에
걸려 진급 전체가 롤백됩니다. 3학년은 진급이 아니라 졸업이라 반도 그대로 두고,
학생을 개별 퇴원시킨 뒤 선생님이 반을 `close`합니다.

**반 이름은 서버가 건드리지 않습니다.** `고1 기초반`이 2학년 반이 되어도 이름은 그대로라
선생님이 T-3에서 직접 고칩니다. 이름에서 학년을 파싱해 바꾸려 하지 마세요 —
`목요일반`처럼 학년이 안 들어간 이름이 대부분입니다.
응답의 `classRoomsPromoted`를 화면에 띄워 이름 수정을 유도하세요.

### 4-2-1. 학교

**GET `/teacher/schools`** — 페이징 없음

```jsonc
[ { "schoolId": 1, "name": "A고등학교", "studentCount": 104 },
  { "schoolId": 2, "name": "B고등학교", "studentCount": 93 } ]
```

**POST `/teacher/schools`**

```jsonc
// Req
{ "name": "C고등학교" }

// Res
{ "schoolId": 3, "name": "C고등학교" }
```

`schools.name`이 UNIQUE라 중복 시 409 `DUPLICATE_RESOURCE`.

**시드에 학교가 없습니다.** 운영 시작 시 선생님이 이 API로 직접 등록합니다.
`class_rooms.school_id`가 필수라 **학교가 없으면 반을 만들 수 없습니다.**

삭제는 없습니다 — `students.school_id`·`class_rooms.school_id`가 참조하고 있어
지우면 데이터가 끊깁니다. 이름 수정이 필요하면 `PATCH`를 추가하지 말고 DB에서
직접 고치세요. 연 1회도 안 쓰는 기능입니다.

### 4-3. 반

**POST `/teacher/class-rooms`**

```jsonc
{ "name": "고2 심화반", "schoolId": 1, "grade": 2,
  "dayOfWeek": 3, "startTime": "19:00",
  "termStart": "2026-03-02", "termEnd": "2027-02-28", "memo": null }
```

**반은 학교 하나·학년 하나에 속합니다.** 유형(정규/특강) 구분은 없고 이름은 자유입니다.
필수는 `name`·`schoolId`·`grade` 셋이고 나머지는 선택입니다.
이 두 값이 가입 시 학생에게 복사되므로 **배정된 학생이 있으면 수정을 막으세요.**

활성 반끼리 이름이 겹치면 409 `DUPLICATE_RESOURCE` (`uq_class_rooms_name`,
`WHERE status = 'ACTIVE'` 부분 인덱스라 종료된 반 이름은 재사용 가능).

`dayOfWeek`: 1=월 ~ 7=일 (ISO-8601). 비워 두면 `POST /lessons/bulk`를 쓸 수 없습니다.

**DELETE `/teacher/class-rooms/{classRoomId}`** — 잘못 만든 반 삭제용입니다.
`lessons`·`enrollments`·`homeworks`가 하나라도 있으면 **409**를 반환하고 `close`를 안내하세요.
운영이 시작된 반을 지우면 학생의 과거 기록이 함께 사라집니다.

**PATCH** — 이름 변경은 과거 기록에도 소급 적용됩니다. `lessons`·`attendances`가
`class_room_id`를 참조하므로 지난 수업의 반 이름 표기도 바뀝니다. 의도된 동작입니다.

**POST `/teacher/class-rooms/{classRoomId}/students`**

```jsonc
{ "studentIds": [88, 91, 97], "joinedAt": "2026-03-02" }
```

이미 배정된 학생은 무시하고 나머지만 추가 (멱등).
**학교·학년이 반과 다른 학생은 400 `VALIDATION_FAILED`로 차단합니다.** 어느 학생이
왜 걸렸는지 응답에 담으세요. 통과시키면 그 학생만 다른 학교 기출과 시험일정을 받습니다.

**DELETE `.../students/{studentId}`** — 행 삭제가 아니라 `left_at = 오늘` 기록.

**GET `/teacher/class-rooms/{classRoomId}/students`** — `asOf=2026-05-20`으로 과거 시점 명단 조회 가능. 기본은 현재 재원생.

명단은 `findActiveStudents(classRoomId, targetDate)`이고 **`ORDER BY e.student.name`**입니다.
`e.student.user.name`으로 쓰면 암묵적 INNER JOIN이 생겨 미가입 학생이 통째로 사라집니다.

**POST `/teacher/class-rooms`** 응답에 `joinCode`가 포함됩니다.

```jsonc
// Req
{ "name": "고2 심화반", "dayOfWeek": 3, "startTime": "19:00",
  "termStart": "2026-03-02", "termEnd": "2027-02-28", "memo": null }

// Res
{ "classRoomId": 3, "name": "고2 심화반",
  "joinCode": "HK7F2Q", "joinCodeActive": true }
```

**`joinCode`는 서버가 생성합니다.** 요청에서 받지 마세요 — 선생님이 직접 정하면
`고2반` 같은 추측 가능한 값을 넣습니다. 6자리, 혼동 문자(`0 O 1 I L`) 제외,
`signup_codes.code`와도 겹치지 않게 발급합니다.

**POST `/teacher/class-rooms/{classRoomId}/join-code`** — 코드 재발급 · 여닫기

```jsonc
// Req  (active만 보내면 코드는 유지하고 열고 닫기만)
{ "regenerate": false, "active": false }

// Res
{ "joinCode": "HK7F2Q", "joinCodeActive": false }
```

| 상황 | 요청 |
|---|---|
| 등록 기간 종료 | `{ "active": false }` |
| 코드가 샌 것 같다 | `{ "regenerate": true, "active": true }` |

재발급하면 이전 코드는 즉시 무효입니다. **이미 가입한 학생의 `enrollments`는 그대로입니다.**

**등록 기간이 끝나면 반드시 닫으세요.** 반 코드는 전화번호 대조가 없어서,
열려 있는 동안은 코드를 아는 누구나 그 반 학생으로 가입할 수 있습니다.
T-3 반 목록에 코드 상태를 항상 보이게 두세요.

### 4-4. 수업

**POST `/teacher/lessons`**

```jsonc
{ "classRoomId": 3, "lessonDate": "2026-05-20",
  "year": 2026, "month": 5, "week": 4,
  "title": "관계대명사 what과 관계부사",
  "videoUrl": "https://youtu.be/xxxxxxxxxxx",
  "content": "...", "keyPoints": "...", "nextPreview": "..." }
```

**`year`·`month`·`week`는 필수입니다.** `lessons`에서 셋 다 `NOT NULL`이고,
T-4 화면이 주차를 먼저 고르는 구조라 선택한 값을 그대로 보냅니다.
**서버가 날짜에서 계산해 덮어쓰지 마세요** — 달 경계에 걸친 주는 세는 방식이 갈립니다.
`POST /teacher/lessons/bulk`에서만 서버가 기본값을 채우고, 이후 화면에서 고칠 수 있게 둡니다.

`UNIQUE (class_room_id, lesson_date)` → 중복 시 409.

`videoUrl` 허용 형식 3가지. 서버가 파싱해 `videoId`·`embedUrl`로 내려줍니다.

```
https://youtu.be/{id}
https://www.youtube.com/watch?v={id}
https://www.youtube.com/embed/{id}
```

**POST `/teacher/lessons/bulk`**

```jsonc
{ "classRoomId": 3, "from": "2026-03-02", "to": "2026-07-31",
  "skipDates": ["2026-05-05", "2026-06-06"] }
```

반의 `dayOfWeek` 기준으로 생성. 내용 비어 있고 `PENDING`, `published_at=null`. 기존 날짜는 건너뜁니다.

**POST `/teacher/lessons/{lessonId}/publish`** — 공개. `PATCH`는 `published_at`을 건드리지 않습니다.

**GET `/teacher/lessons/{lessonId}/views`**

```jsonc
{ "lessonId": 501, "totalStudents": 20, "viewedCount": 14,
  "items": [
    { "studentId": 88, "name": "서동환", "viewed": true,
      "firstViewedAt": "2026-05-21T20:14:00+09:00", "watchSeconds": 1820 },
    { "studentId": 91, "name": "김하늘", "viewed": false,
      "firstViewedAt": null, "watchSeconds": 0 } ] }
```

미시청 학생도 포함합니다. 선생님이 보려는 건 "누가 안 봤는지"입니다.

### 4-5. 출석

**GET `/teacher/lessons/{lessonId}/attendance`**

```jsonc
{ "lessonId": 501, "classRoomId": 3, "classRoomName": "고2 심화반",
  "lessonDate": "2026-05-20", "attendanceStatus": "PENDING",
  "students": [
    { "studentId": 88, "name": "서동환", "status": "PRESENT", "memo": null },
    { "studentId": 91, "name": "김하늘", "status": "PRESENT", "memo": null } ] }
```

명단은 `findActiveStudents(classRoomId, lessonDate)`. **오늘이 아니라 수업일 기준 재원생**입니다. 정렬은 이름순.

**POST `/teacher/lessons/{lessonId}/attendance/confirm`**

안 온 학생만 보냅니다.

```jsonc
// Req
{ "exceptions": [
    { "studentId": 91, "status": "ABSENT", "memo": "무단" },
    { "studentId": 97, "status": "SICK", "memo": "병원 진료, 학부모 사전 연락" } ] }

// Res
{ "lessonId": 501, "attendanceStatus": "CONFIRMED",
  "confirmedAt": "2026-05-20T22:10:00+09:00",
  "summary": { "present": 18, "late": 0, "absent": 1, "sick": 1, "excused": 0 } }
```

- 빈 배열이면 전원 출석 확정 (유효한 요청)
- 재원생 **전원**의 `attendances` 행 생성. `attend_date`에 `lessons.lesson_date` 복사
- 명단에 없는 `studentId`가 섞이면 400
- 재확정은 `ON CONFLICT (student_id, lesson_id) DO UPDATE`. **409를 던지지 마세요**
- 재확정 시 `checked_by`는 유지, `updated_by`/`updated_at` 갱신

```sql
INSERT INTO attendances
  (lesson_id, class_room_id, student_id, attend_date, status, memo, checked_by, checked_at)
VALUES (:lessonId, :classRoomId, :studentId, :attendDate, :status, :memo, :teacherId, now())
ON CONFLICT (student_id, lesson_id) DO UPDATE
SET status = EXCLUDED.status, memo = EXCLUDED.memo,
    updated_by = EXCLUDED.checked_by, updated_at = now();
```

**PATCH `/teacher/attendances/{attendanceId}`**

```jsonc
{ "status": "SICK", "memo": "학부모 사후 연락" }
```

`updated_by`·`updated_at` 필수 기록. 정정 요청은 반드시 들어옵니다.

**GET `/teacher/attendance/pending`** — `lesson_date <= 오늘`이고 `PENDING`인 수업만. 미래 제외.

### 4-6. 숙제

**POST `/teacher/homeworks`**

```jsonc
// Req
{ "classRoomId": 3, "lessonId": 501,
  "title": "주간지 전 범위 풀기", "description": "워크북 27~35쪽 풀어오기",
  "dueAt": "2026-05-21T20:00:00+09:00",
  "templateId": null, "saveAsTemplate": false }

// Res
{ "homeworkId": 720, "targetCount": 20 }
```

**출제 시 대상 학생 전원의 `submissions`를 `NOT_SUBMITTED`로 즉시 생성합니다.** 대상은 마감일 기준 재원생 전원 (일부 지정 불가).

`lessonId`는 선택이지만 연결해야 캘린더 숙제 완료율이 계산됩니다.

**DELETE `/teacher/homeworks/{homeworkId}`**

**`status`가 `SUBMITTED` 또는 `CHECKED`인 `submissions`가 1건이라도 있으면 409 `SUBMISSION_EXISTS`.**

출제 시점에 대상 전원의 `submissions`가 `NOT_SUBMITTED`로 미리 생성되므로,
"`submissions` 행이 있으면 409"로 구현하면 **어떤 숙제도 삭제할 수 없습니다.** 상태로 판단하세요.

```sql
SELECT count(*) FROM submissions
WHERE homework_id = :homeworkId AND status <> 'NOT_SUBMITTED';
-- > 0 이면 409 SUBMISSION_EXISTS ("제출한 학생이 있어 삭제할 수 없습니다")
```

삭제 가능할 때(전원 미제출)는 한 트랜잭션에서 `submissions`를 **먼저 지우고** `homeworks`를 지웁니다.
`submissions.homework_id`에는 `ON DELETE CASCADE`가 없습니다. 그냥 지우면 FK 제약 위반입니다.

**PATCH** — 마감은 **늦추는 방향만** 허용. 앞당기려면 삭제 후 재출제.

**GET `/teacher/homeworks/{homeworkId}/submissions`** — 페이징 없음

```jsonc
{ "homework": { "id": 720, "title": "주간지 전 범위 풀기",
                "classRoomName": "고2 심화반",
                "dueAt": "2026-05-21T20:00:00+09:00" },
  "counts": { "total": 20, "notSubmitted": 3, "submitted": 5, "checked": 12 },
  "items": [
    { "submissionId": 4412, "studentId": 88, "studentName": "서동환",
      "status": "SUBMITTED", "submittedAt": "2026-05-21T19:42:00+09:00",
      "isLate": false, "photoCount": 2, "hasFeedback": false,
      "thumbnailUrl": "https://..." },
    { "submissionId": 4413, "studentId": 91, "studentName": "김하늘",
      "status": "NOT_SUBMITTED", "submittedAt": null,
      "isLate": false, "photoCount": 0, "hasFeedback": false,
      "thumbnailUrl": null } ] }
```

정렬: `SUBMITTED` → `NOT_SUBMITTED` → `CHECKED`. 처리할 것이 위로.

**N+1 주의.** 20명 각각 사진을 따로 조회하면 21쿼리입니다. `sort_order = 1`로 한 번에 가져와 매핑하세요.

**GET `/teacher/submissions/{submissionId}`**

```jsonc
{ "submissionId": 4412, "studentName": "서동환", "status": "SUBMITTED",
  "submittedAt": "2026-05-21T19:42:00+09:00", "isLate": false,
  "photos": [ { "photoId": 8812, "url": "https://...", "sortOrder": 1 } ],
  "feedback": null,
  "prevSubmissionId": 4409, "nextSubmissionId": 4415 }
```

**`prev`/`next`가 핵심입니다.** 목록으로 돌아가지 않고 연속 처리하게 합니다. `next`는 아직 확인 안 한(`SUBMITTED`) 다음 제출물.

**POST `/teacher/submissions/{submissionId}/feedback`**

```jsonc
{ "content": "본문 필사 꼼꼼히 잘했어요. 독해도 이대로 꾸준히." }
```

`feedbacks` 생성(`submission_id` UNIQUE) + `submissions.status = CHECKED`.

**피드백은 학생 화면(S-4)에만 노출됩니다. 학부모에게는 보이지 않습니다.** 별도 발송 없음.
학부모 응답 DTO에 `feedback` 필드를 넣지 마세요 (6절 P-3 참조).

**POST `/teacher/submissions/{submissionId}/check`** — 피드백 없이 확인만. 200명 전원에게 글을 쓰는 건 비현실적이라 이 경로가 필요합니다.

### 4-7. 성적·시험

**POST `/teacher/scores/bulk`** — 반드시 구현

```jsonc
{ "scoreType": "INTERNAL", "examScheduleId": 12,
  "examName": "1학기 중간고사", "subject": "영어", "examDate": "2026-04-28",
  "scores": [
    { "studentId": 88, "rawScore": 96, "gradeLevel": 1 },
    { "studentId": 91, "rawScore": 88, "gradeLevel": 2 } ] }
```

200명을 한 명씩 폼으로 입력하게 하면 실사용이 불가능합니다.

**POST `/teacher/exam-schedules`**

```jsonc
{ "schoolId": 1, "grade": 2, "year": 2026, "semester": 1,
  "examType": "FINAL", "startDate": "2026-06-25", "endDate": "2026-06-30",
  "scopeNote": "교과서 5~8과, 부교재 전 범위" }
```

`UNIQUE (school_id, grade, year, semester, exam_type)` → 중복 409.
**여기 등록된 일정이 D-day의 유일한 근거입니다.**

### 4-8. 자료·공지

**POST `/teacher/materials`**

```jsonc
{ "title": "A고 2학년 1학기 중간 기출", "category": "PAST_EXAM",
  "s3Key": "materials/2026/05/{uuid}.pdf", "fileName": "A고_2학년_중간기출.pdf",
  "bytes": 3210544, "schoolId": 1, "grade": 2,
  "classRoomId": null, "visibility": "CLASS" }
```

허용 확장자: `pdf hwp hwpx docx xlsx pptx zip jpg png`, 최대 50MB.
**`exe sh bat js html` 금지.** HTML은 서빙 시 XSS 경로가 됩니다.

**POST `/teacher/notices`**

```jsonc
{ "title": "[SUMMER] 고1 영어 구문독해 선행 안내", "content": "...",
  "scope": "GRADE", "schoolId": 1, "grade": 1, "classRoomId": null, "pinned": false }
```

`scope`별 필수 필드를 서버에서 검증합니다.

| scope | 필수 |
|---|---|
| `ALL` | 없음 |
| `SCHOOL` | `schoolId` |
| `GRADE` | `schoolId`, `grade` |
| `CLASS` | `classRoomId` |

### 4-9. 온라인 테스트

**POST `/teacher/online-tests`** — 출제

```jsonc
// Req
{ "classRoomId": 3, "title": "6월 2주차 단어시험",
  "questionCount": 25, "choiceCount": 5,
  "correctChoices": [3,5,1,2,4, 1,3,3,5,2, 4,1,2,5,3, 2,4,4,1,5, 3,2,5,1,4],
  "points": null,                          // null이면 균등 배점
  "answerS3Key": "online-tests/2026/06/{uuid}-key.pdf",   // 해설지. 채점 후에만 공개
  "scoreType": "WORD",                     // null이면 성적에 반영 안 함
  "subject": "영어",                        // scoreType이 있으면 필수. scores.subject로 복사
  "year": 2026, "month": 6, "week": 2,
  "opensAt": "2026-06-08T00:00:00+09:00",
  "closesAt": "2026-06-14T23:59:59+09:00" }

// Res
{ "testId": 55, "targetCount": 20, "published": false }
```

**`correctChoices` 길이가 `questionCount`와 다르면 400.** DB CHECK가 잡기 전에 서비스에서
먼저 막으세요. 25문항인데 정답을 24개만 넣으면 전원의 점수가 조용히 틀립니다.

각 값은 `1 ~ choiceCount` 범위여야 합니다. 0이나 6이 들어오면 400입니다.

`points`를 주면 문항별 배점, `null`이면 균등 배점입니다. 균등일 때 100점을 문항 수로 나누면
나머지가 생기므로 **점수는 비율로 계산**합니다 (아래 채점 규칙 참조).

**`points`도 길이가 `questionCount`와 같아야 합니다.** 정답 배열과 같은 검사를 하세요.
배점 배열이 짧으면 뒤쪽 문항 배점이 없어 총점이 어긋나는데, 증상이 정답 배열 오류와
똑같아서 원인을 찾기 어렵습니다. DB에도 `ck_online_tests_points`가 있습니다.

**`scoreType`을 넣으면 `subject`가 필수입니다.** `scores.subject`가 `NOT NULL`이라
과목 없이는 성적 반영 행을 만들 수 없습니다. 없으면 400 `VALIDATION_FAILED`.
DB에도 `ck_online_tests_subject`가 걸려 있습니다.

**과목을 서버에서 지어내지 마세요.** 성적 관리 범위(영어만인지 전 과목인지)가
학원장 미확정 사항입니다. T-14 화면에서 성적 반영을 켜면 과목 입력란이 나타나게 하세요.

**POST `/teacher/online-tests/{testId}/publish`** — 공개

`published_at`을 채웁니다. 공개 전에는 학생 목록에 나오지 않습니다.
**공개 후에는 `correctChoices`·`questionCount`를 수정할 수 없습니다** (409).
이미 응시한 학생의 점수가 소급 변경되기 때문입니다. 고치려면 삭제 후 재출제하세요.

**DELETE `/teacher/online-tests/{testId}`** — 제출(`SUBMITTED`)이 1건이라도 있으면 409.

**GET `/teacher/online-tests/{testId}/results`** — 페이징 없음

```jsonc
{ "test": { "testId": 55, "title": "6월 2주차 단어시험",
            "questionCount": 25, "classRoomName": "고2 심화반" },
  "counts": { "total": 20, "notStarted": 3, "inProgress": 2, "submitted": 15 },
  "average": 84.3,
  "items": [
    { "studentId": 88, "name": "서동환", "status": "SUBMITTED",
      "score": 92.0, "correctCount": 23, "submittedAt": "2026-06-10T20:14:00+09:00" },
    { "studentId": 91, "name": "김하늘", "status": "NOT_STARTED",
      "score": null, "correctCount": null, "submittedAt": null } ] }
```

응시하지 않은 학생도 포함합니다. 선생님이 보려는 건 "누가 안 봤는지"입니다.
`average`는 제출자만으로 계산합니다.

**`average`를 학생·학부모 응답에는 절대 넣지 마세요.** 반 평균은 노출 금지 항목입니다.

---

**GET `/student/online-tests`** — 응시 가능 목록

```jsonc
{ "items": [ {
  "testId": 55, "title": "6월 2주차 단어시험",
  "classRoomName": "고2 심화반", "questionCount": 25,
  "closesAt": "2026-06-14T23:59:59+09:00", "remainingMinutes": 2870,
  "status": "IN_PROGRESS", "answeredCount": 12 } ] }
```

조건: `published_at IS NOT NULL` + 학생이 그 반 재원생 + `opens_at` 경과.
`status`는 `NOT_STARTED` / `IN_PROGRESS` / `SUBMITTED`.

**GET `/student/online-tests/{testId}`** — 응시 화면

```jsonc
{ "testId": 55, "title": "6월 2주차 단어시험",
  "questionCount": 25, "choiceCount": 5,
  "closesAt": "2026-06-14T23:59:59+09:00",
  "chosenChoices": [3,5,null,2,null, ...], // 임시 저장된 답
  "status": "IN_PROGRESS" }
```

**문제지 파일이 없습니다.** 학생은 수업에서 받은 **종이 시험지**를 풀고 답만 입력합니다.
화면은 `questionCount`만큼 1~`choiceCount` 라디오 버튼 줄을 그리면 됩니다.

**`answerS3Key`와 `correctChoices`를 이 응답에 넣지 마세요.** 정답입니다.
제출 전 응답에 섞이면 브라우저 개발자 도구로 그대로 보입니다. **필드 자체를 빼세요.**

**PUT `/student/online-tests/{testId}/answers`** — 임시 저장

```jsonc
{ "chosenChoices": [3,5,null,2,null, ...] }
```

배열 길이는 `questionCount`와 같아야 하고, 안 푼 문항은 `null`입니다.
`status = 'IN_PROGRESS'`로 유지되고 채점하지 않습니다.

**출석 입력과 달리 서버에 저장합니다.** 25문항을 푸는 데 20~30분이 걸려서, 브라우저가 닫히면
처음부터 다시 해야 합니다. 30초짜리 출석 입력과는 상황이 다릅니다.
프론트는 답을 고를 때마다 또는 30초마다 호출하세요.

**POST `/student/online-tests/{testId}/submit`** — 제출 + 자동 채점

```jsonc
// Req 없음. 마지막으로 PUT한 답안으로 채점

// Res
{ "testId": 55, "score": 92.0, "correctCount": 23, "questionCount": 25,
  "submittedAt": "2026-06-10T20:14:00+09:00",
  "answerFileUrl": "https://...",          // 해설지. 제출 후에만 내려감
  "results": [
    { "questionNo": 1, "chosen": 3, "correct": 3, "isCorrect": true },
    { "questionNo": 2, "chosen": 5, "correct": 1, "isCorrect": false } ] }
```

**채점 규칙**

```
correctCount = chosenChoices[i] == correctChoices[i] 인 문항 수
score        = 획득 배점 합계 / 전체 배점 합계 × 100   (소수 둘째 자리 반올림)
```

- **`null`(미체크)은 오답 처리**합니다. 감점은 없습니다.
- `points`가 `null`이면 전 문항 배점 1로 계산합니다. `23/25 × 100 = 92.00`.
- 균등 배점일 때 `100 / questionCount`를 문항마다 더하지 마세요. 25문항이면 `4.0`이라 딱 맞지만
  30문항이면 `3.333...`이 되어 만점이 99.99가 됩니다. **반드시 비율로 계산하세요.**

**처리 (하나의 트랜잭션)**

1. `closes_at` 경과 시 400. 이미 `SUBMITTED`면 409
2. 채점 → `score`·`correct_count`·`submitted_at` 기록, `status = 'SUBMITTED'`
3. `online_tests.score_type`이 `null`이 아니면 **`scores`에 행 생성**

`scores`는 `NOT NULL` 컬럼이 많습니다. 아래를 전부 채우세요.

| `scores` 컬럼 | 출처 |
|---|---|
| `raw_score` | 채점 점수 (100점 환산) |
| `score_type` | `online_tests.score_type` |
| **`subject`** | **`online_tests.subject`** — 없으면 NOT NULL 위반 |
| `exam_name` | `online_tests.title` |
| **`exam_date`** | **제출일** (`submitted_at`의 날짜) |
| `year` `month` `week` | `online_tests`에서 복사 |
| `exam_schedule_id` `grade_level` | `null` |

**3번이 P-4 그래프와 이어지는 지점입니다.** 주간 단어시험을 이 기능으로 치르면
선생님이 성적을 따로 입력할 필요가 없습니다. 같은 테스트를 다시 제출해도
`UNIQUE (online_test_id, student_id)` 때문에 중복 응시가 막히므로 `scores`도 1건만 생깁니다.
`uq_scores`가 한 겹 더 막아 줍니다.

**재응시는 없습니다.** 제출 후에는 답을 바꿀 수 없습니다.
다시 보게 하려면 선생님이 해당 제출을 삭제하고 학생이 새로 응시합니다.

**GET `/student/online-tests/{testId}/result`** — 제출 후 결과 재조회

`submit` 응답과 같은 형식입니다. 제출 전이면 404.

### 4-10. 클리닉

**POST `/teacher/clinics`** — 시간대 개설

```jsonc
// Req  (capacity 생략 시 인원 제한 없음)
{ "clinicDate": "2026-06-04", "startTime": "17:00", "endTime": "18:00",
  "capacity": 6, "memo": "고2 구문 보충" }

// Res
{ "clinicId": 41, "reservedCount": 0, "capacity": 6, "status": "OPEN" }
```

`UNIQUE (clinic_date, start_time)` → 같은 날 같은 시각에 두 번 열면 409.

**GET `/teacher/clinics?from&to&status`** — 페이징 없음. 기간 내 시간대와 신청 인원

```jsonc
[ { "clinicId": 41, "clinicDate": "2026-06-04",
    "startTime": "17:00", "endTime": "18:00",
    "capacity": 6, "reservedCount": 4, "status": "OPEN",
    "attendanceConfirmed": false } ]
```

**GET `/teacher/clinics/{clinicId}/reservations`** — 페이징 없음

```jsonc
{ "clinicId": 41, "clinicDate": "2026-06-04", "startTime": "17:00",
  "capacity": 6, "attendanceConfirmed": false,
  "students": [
    { "reservationId": 902, "studentId": 88, "name": "서동환",
      "assignedByTeacher": false, "attendStatus": null },
    { "reservationId": 903, "studentId": 91, "name": "김하늘",
      "assignedByTeacher": true, "attendStatus": null } ] }
```

`assignedByTeacher`는 `assigned_by IS NOT NULL`입니다. 학생이 직접 신청했는지 선생님이 배정했는지
구분해야 "왜 여기 있냐"는 문의에 답할 수 있습니다.

**POST `/teacher/clinics/{clinicId}/students`** — 배정 (복수)

```jsonc
{ "studentIds": [88, 91, 97] }
```

이미 신청한 학생은 무시하고 나머지만 추가 (멱등). **정원을 넘으면 넘는 만큼만 넣지 말고 409**로
전체를 거절하세요. 일부만 들어가면 선생님이 누가 빠졌는지 알 수 없습니다.

**DELETE `/teacher/clinics/{clinicId}/students/{studentId}`** — 행 삭제가 아니라 `status = 'CANCELED'`.

**POST `/teacher/clinics/{clinicId}/attendance/confirm`** — 출석 확정

T-5와 **같은 방식**입니다. 안 온 학생만 보냅니다.

```jsonc
// Req
{ "exceptions": [ { "studentId": 91, "status": "ABSENT", "memo": "무단" } ] }

// Res
{ "clinicId": 41, "confirmedAt": "2026-06-04T18:05:00+09:00",
  "summary": { "present": 3, "late": 0, "absent": 1, "sick": 0, "excused": 0 } }
```

대상은 `status = 'RESERVED'`인 예약 전원입니다. 나머지는 `PRESENT`로 채웁니다.
`clinic_reservations.attend_status`에 기록하고, **`attendances` 테이블은 건드리지 마세요.**

**GET `/teacher/clinic-change-requests?status=PENDING`** — 페이징 없음

```jsonc
[ { "requestId": 12, "studentId": 88, "studentName": "서동환",
    "from": { "clinicId": 41, "clinicDate": "2026-06-04", "startTime": "17:00" },
    "to":   { "clinicId": 47, "clinicDate": "2026-06-06", "startTime": "19:00" },
    "reasonCode": "...", "reasonNote": "학교 보충수업",
    "requestedAt": "2026-06-02T21:10:00+09:00" } ] }
```

`to`가 `null`이면 **취소 요청**입니다.

**POST `/teacher/clinic-change-requests/{requestId}/decide`**

```jsonc
{ "approve": true, "note": null }
```

승인 시 **한 트랜잭션**으로 처리합니다.

1. 기존 `clinic_reservations.status = 'MOVED'`
2. 목표 클리닉에 새 `RESERVED` 행 생성
3. `clinic_change_requests.status = 'APPROVED'`, `decided_by`·`decided_at` 기록

**2번에서 목표 클리닉의 정원을 다시 확인하세요.** 요청 시점에는 자리가 있었어도 승인 시점에는
찼을 수 있습니다. 초과면 409를 반환하고 요청은 `PENDING`으로 남깁니다.

취소 요청(`to`가 `null`)이면 기존 예약만 `CANCELED`로 바꿉니다.

---

## 5. 학생 API

### GET `/student/me`

S-7 화면 상단의 학생 정보 카드입니다. 성적은 `/student/scores`가 따로 내려줍니다.

```jsonc
{ "studentId": 88, "name": "서동환",
  "schoolName": "A고등학교", "grade": 2,
  "classRooms": [ { "classRoomId": 3, "name": "고2 심화반" },
                  { "classRoomId": 7, "name": "썸머 집중반" } ],
  "phone": "010-****-2222",
  "parentLinked": true }
```

`name`은 **`students.name`**입니다. 전화번호는 서버에서 마스킹합니다.

**`parentLinked`가 `false`면 화면에 "보호자 계정이 아직 연결되지 않았습니다.
선생님께 문의해 주세요"를 띄우세요.** 학생이 알아야 조치가 됩니다.

`classRooms`에 `joinCode`를 넣지 마세요. 학생이 코드를 알면 반 밖으로 퍼집니다.

### GET `/student/home`

```jsonc
{ "student": { "name": "서동환", "schoolName": "A고등학교", "grade": 2 },
  "nextLesson": { "lessonDate": "2026-06-03", "dDay": 5, "classRoomName": "고2 심화반" },
  "nextExam": { "examType": "FINAL", "startDate": "2026-06-25", "dDay": 27 },
  "currentHomeworks": [
    { "homeworkId": 720, "title": "주간지 전 범위 풀기",
      "dueAt": "2026-05-21T20:00:00+09:00",
      "status": "NOT_SUBMITTED", "remainingMinutes": 137 } ],
  "unreadFeedbackCount": 2, "noticeCount": 6 }
```

**`currentHomeworks`에 마감 지난 미제출도 포함합니다.** 사라지면 학생이 잊습니다.

### GET `/student/homeworks`

```jsonc
{ "items": [ {
  "homeworkId": 720, "title": "주간지 전 범위 풀기",
  "classRoomName": "고2 심화반",
  "dueAt": "2026-05-21T20:00:00+09:00",
  "status": "NOT_SUBMITTED", "isLate": false,
  "photoCount": 0, "hasFeedback": false, "remainingMinutes": 137 } ] }
```

`remainingMinutes`는 **서버가 계산**합니다. 클라이언트 시계는 틀릴 수 있습니다. 음수면 마감 경과.
정렬: `NOT_SUBMITTED` 우선, 그 안에서 `due_at ASC`.

### 사진 업로드 3단계

```
1) POST /student/homeworks/{homeworkId}/photos/upload-url
   Req  { "contentType": "image/webp", "bytes": 284012 }
   Res  { "uploadUrl": "https://...", "s3Key": "submissions/2026/05/{uuid}.webp" }

2) 클라이언트가 uploadUrl로 직접 PUT (서버 경유 금지)
   업로드 전 Canvas 리사이즈: 장변 1600px, WebP, quality 0.8
   Content-Type을 1번에서 보낸 값과 동일하게

3) POST /student/homeworks/{homeworkId}/photos
   Req  { "s3Key": "...", "sortOrder": 1, "bytes": 284012 }
   Res  { "photoId": 8812, "photoCount": 1 }
```

**서버 검증**

| 항목 | 실패 시 |
|---|---|
| 사진 10장 초과 | 409 `PHOTO_LIMIT_EXCEEDED` |
| `contentType`이 jpeg/png/webp 외 | 400 `UNSUPPORTED_FILE_TYPE` |
| `bytes` > 10MB | 413 `FILE_TOO_LARGE` |
| `s3Key`가 서버 발급분과 불일치 | 400 |

**`s3Key` 대조가 중요합니다.** 클라이언트가 보낸 값을 그대로 믿으면 다른 경로의 객체를 참조하게 만들 수 있습니다. 발급 시 서버에 임시 저장하거나 서명 토큰으로 발급하세요.

presigned 유효기간 5분. 만료되면 1번 재호출.

EXIF 회전 처리: `createImageBitmap(file, { imageOrientation: "from-image" })`

### POST `/student/homeworks/{homeworkId}/submit`

```jsonc
{ "submissionId": 4412, "status": "SUBMITTED",
  "submittedAt": "2026-05-21T19:42:00+09:00", "isLate": false, "photoCount": 2 }
```

- 사진 0장이면 400
- `is_late = now() > due_at`
- **마감 후 제출 허용** (확정 정책). 차단하지 않습니다
- `CHECKED` 상태면 수정 불가

### POST `/student/lessons/{lessonId}/view`

```jsonc
{ "watchSeconds": 320 }
```

```sql
INSERT INTO lesson_views (lesson_id, student_id, watch_seconds)
VALUES (:lessonId, :studentId, :watchSeconds)
ON CONFLICT (lesson_id, student_id) DO UPDATE
SET last_viewed_at = now(),
    watch_seconds = lesson_views.watch_seconds + EXCLUDED.watch_seconds;
```

호출 시점: 재생 시작(0), 30초마다, 이탈 시. **매초 호출하지 마세요.**
이탈은 `visibilitychange`로. `beforeunload`는 모바일에서 신뢰할 수 없습니다.

### GET `/student/materials?category&page&size` (S-8)

**자료실은 학생 전용입니다.** 학부모에게 열지 마세요.

```jsonc
{ "items": [ {
  "materialId": 41, "title": "5월 24일 수업자료", "category": "LESSON",
  "fileName": "0524_lesson.pdf", "bytes": 7130316,
  "year": 2026, "month": 5, "week": 4,
  "createdAt": "2026-05-13T10:00:00+09:00" } ] }
```

**정렬은 최신순**(`created_at DESC`)입니다. 학생이 "이번 주 자료"를 맨 위에서 찾습니다.

**응답에 `s3Key`를 넣지 마세요.** 내부 저장 경로가 노출됩니다.

**세 차원을 각각 독립으로 봅니다.** 채워진 차원만 검사하고 비어 있으면 통과입니다.

```sql
WHERE (m.visibility = 'PUBLIC'
    OR ( (m.school_id     IS NULL OR m.school_id     = :schoolId)
     AND (m.grade         IS NULL OR m.grade         = :grade)
     AND (m.class_room_id IS NULL OR m.class_room_id IN (:myClassRoomIds)) ))
  AND (:category IS NULL OR m.category = :category)
ORDER BY m.created_at DESC
```

**분기를 `school_id` 기준으로 묶지 마세요.** `m.school_id = :schoolId`를 AND의 앞단에 두면
`school_id`가 `NULL`이고 `class_room_id`만 지정된 자료가 `NULL = 1` → NULL로 탈락해
**아무에게도 안 보입니다.** 반이 이미 학교·학년을 갖고 있어 반 전용 자료에 학교를 따로
채울 이유가 없고, 그래서 이 조합이 실제로 자주 나옵니다.

`:myClassRoomIds`는 학생의 현재 활성 `enrollments`에서 가져옵니다. **빈 리스트일 수 있습니다.**
빈 `IN ()`은 SQL 오류이므로 더미 값을 넣거나 조건을 빼세요.

`studentId` 파라미터가 없습니다. **본인 것만 보므로 토큰에서 학생을 찾습니다.**

### GET `/student/materials/{materialId}/download-url`

```jsonc
{ "downloadUrl": "https://...", "fileName": "0524_lesson.pdf", "expiresIn": 300 }
```

**발급 전 권한을 재확인하세요.** 목록에서 걸러졌다고 안심하면 안 됩니다.
`materialId`를 직접 넣는 호출을 막아야 합니다. 노출 대상이 아니면 403.

`Content-Disposition: attachment; filename="..."`을 함께 서명해 원본 파일명으로 저장되게 합니다.

### GET `/student/clinics?from&to` (S-9)

페이징 없음. 신청 가능한 시간대와 본인 신청 현황을 함께 내려줍니다.

```jsonc
[ { "clinicId": 41, "clinicDate": "2026-06-04",
    "startTime": "17:00", "endTime": "18:00",
    "capacity": 6, "reservedCount": 4, "full": false,
    "myReservation": { "reservationId": 902, "status": "RESERVED",
                       "changeRequestStatus": null } },
  { "clinicId": 47, "clinicDate": "2026-06-06",
    "startTime": "19:00", "endTime": "20:00",
    "capacity": 6, "reservedCount": 6, "full": true,
    "myReservation": null } ]
```

`full`은 서버가 계산합니다. `capacity`가 `null`이면 항상 `false`입니다.
프론트에서 `reservedCount >= capacity`를 계산하게 두면 `capacity`가 `null`일 때 깨집니다.

**다른 학생의 이름을 내려주지 마세요.** 인원 수만입니다.

### POST `/student/clinics/{clinicId}/reservation`

본문 없음. 신청합니다.

```jsonc
{ "reservationId": 902, "clinicId": 41, "status": "RESERVED" }
```

| 실패 | 응답 |
|---|---|
| 정원 초과 | 409 `CLINIC_CAPACITY_EXCEEDED` |
| 이미 신청함 | 409 `DUPLICATE_RESOURCE` |
| `status = 'CLOSED'`인 클리닉 | 409 `DUPLICATE_RESOURCE` |
| 지난 날짜 | 400 `VALIDATION_FAILED` |

**앞의 두 개는 다른 코드입니다.** S-9 화면이 "정원이 모두 찼습니다"와 "이미 신청하셨습니다"를
다르게 보여줘야 합니다.

**정원 체크는 `SELECT ... FOR UPDATE`로 클리닉 행을 잠근 뒤에 합니다**
(`02_phase1_db_schema.md` 2-9 참조). 조건부 삽입 한 방은 READ COMMITTED에서
경합을 막지 못합니다 — 서브쿼리의 `count(*)`가 세는 행에 락을 걸지 않기 때문입니다.
같은 방식을 선생님 배정과 변경 요청 승인에도 씁니다.

### DELETE `/student/clinics/{clinicId}/reservation`

본인 신청 취소. 행을 지우지 말고 `status = 'CANCELED'`로 바꿉니다.
부분 유니크 인덱스가 `WHERE status = 'RESERVED'`라 나중에 다시 신청할 수 있습니다.

### POST `/student/clinic-change-requests`

시간 변경 요청. **학생이 직접 옮기지 못합니다.** 선생님 승인이 필요합니다.

```jsonc
// Req  (targetClinicId가 null이면 취소 요청)
{ "reservationId": 902, "targetClinicId": 47,
  "reasonCode": "...", "reasonNote": "학교 보충수업과 겹칩니다" }

// Res
{ "requestId": 12, "status": "PENDING" }
```

`reasonCode`는 **필수**이며 정해진 목록에서 고릅니다. 자유 입력은 `reasonNote`입니다.

> ⚠️ **`reasonCode` 옵션 목록이 미확정입니다.** 학원장 확정 전까지 값을 임의로 만들지 마세요.
> 서버는 문자열로 받아 저장만 하고, CHECK 제약과 enum은 목록이 정해진 뒤에 추가합니다.

같은 예약에 `PENDING` 요청이 이미 있으면 409. 두 개가 동시에 승인되면 예약이 꼬입니다.

---

## 6. 학부모 API

### GET `/parent/children`

```jsonc
[ { "studentId": 88, "name": "서동환", "schoolName": "A고등학교", "grade": 2 },
  { "studentId": 92, "name": "서동희", "schoolName": "A고등학교", "grade": 1 } ]
```

`status = ENROLLED`인 자녀만.

### GET `/parent/children/{studentId}/home`

여러 도메인을 조합합니다. **단일 API로 묶으세요.** 프론트에서 6개를 병렬 호출하면 로딩이 지저분해집니다.

```jsonc
{ "student": { "id": 88, "name": "서동환", "schoolName": "A고등학교", "grade": 2,
               "classRooms": ["고2 심화반", "썸머 집중반"] },
  "nextExam": { "examType": "FINAL", "startDate": "2026-06-25", "dDay": 27 },
  "nextLessonDate": "2026-06-03",
  "notices": { "totalCount": 6, "recent": [
    { "noticeId": 15, "title": "[SUMMER] ...", "publishedAt": "2026-05-22T09:00:00+09:00" } ] },
  "pendingHomeworkCount": 1,
  "nextClinic": { "clinicId": 41, "clinicDate": "2026-06-04",
                  "startTime": "17:00", "dDay": 6 },
  "thisMonthAttendance": { "present": 11, "late": 1, "absent": 1, "sick": 0, "excused": 0 } }
```

각 값은 `null`일 수 있습니다. 시험 일정 미등록이면 `nextExam: null` → 프론트가 카드를 숨깁니다. **0이나 임의 값을 넣지 마세요.**

`nextExam`은 **학생의 학교 기준**입니다. 학교가 2곳이라 자녀마다 다릅니다.

### GET `/parent/children/{studentId}/attendances?year=2026&month=5`

`GET /student/attendances`도 동일 형식입니다.

```jsonc
{ "year": 2026, "month": 5,
  "summary": { "present": 11, "late": 1, "absent": 1, "sick": 0, "excused": 0 },
  "homeworkCompletionRate": 94,
  "days": [
    { "date": "2026-05-04", "status": "PRESENT", "homeworkRate": 100 },
    { "date": "2026-05-13", "status": "LATE",    "homeworkRate": 60 },
    { "date": "2026-05-20", "status": "ABSENT",  "homeworkRate": 0 },
    { "date": "2026-05-27", "status": "PENDING", "homeworkRate": null } ] }
```

- **`PENDING`은 선생님이 확정하지 않은 날.** 출석으로 취급하지 않고 회색 "미확인"으로 표시합니다
- `summary`는 `CONFIRMED`된 날만 집계
- `days`에는 **수업이 있는 날만** 담습니다. 없는 날은 프론트가 빈 칸으로 처리
- `homeworkRate`는 Phase 5 완료 전까지 `null`
- **그날 숙제가 없으면 `homeworkRate`는 `0`이 아니라 `null`입니다.** SQL에
  `coalesce(..., 0)`을 붙이면 숙제 없던 날이 학부모에게 0%(빨간 띠)로 보입니다.
  `0`은 "전부 미제출", `null`은 "숙제 없음"입니다

### GET `/parent/children/{studentId}/clinics?from&to` (P-2)

페이징 없음. 자녀의 클리닉 일정과 출석입니다.

```jsonc
[ { "clinicId": 41, "clinicDate": "2026-06-04",
    "startTime": "17:00", "endTime": "18:00",
    "attendStatus": "PRESENT",
    "changeRequestStatus": null } ]
```

`attendStatus`가 `null`이면 아직 출석 확정 전입니다.
`changeRequestStatus`는 자녀가 변경을 요청해 둔 상태(`PENDING`)일 때만 값이 있습니다.

**클리닉 신청·변경 API를 학부모에게 열지 마세요.** 조회만입니다.

### GET `/parent/children/{studentId}/homeworks` (P-3)

**제출 여부만 내려줍니다.** 숙제 내용·사진·피드백은 응답에 넣지 마세요.

```jsonc
{ "items": [ {
  "homeworkId": 720, "title": "주간지 전 범위 풀기",
  "classRoomName": "고2 심화반",
  "dueAt": "2026-05-21T20:00:00+09:00",
  "status": "SUBMITTED", "isLate": false, "checked": true } ] }
```

| 넣는 것 | 넣지 않는 것 |
|---|---|
| `title` (제목까지만) | `description` (숙제 내용) |
| `status` `isLate` `checked` | `photos` `photoCount` `thumbnailUrl` |
| `dueAt` | `feedback` |

**`GET /parent/children/{studentId}/homeworks/{homeworkId}` 상세 엔드포인트를 만들지 마세요.**
목록의 제출 여부가 학부모가 보는 전부입니다.

### GET `/parent/children/{studentId}/scores?scoreType` (P-4)

**주차별 그래프용 응답입니다.** 학부모 화면의 핵심이라 표가 아니라 시계열로 내려줍니다.

```jsonc
{ "word": {
    "unit": "주차",
    "points": [
      { "year": 2026, "month": 5, "week": 3, "label": "5월 3주", "score": 88.0,
        "examName": "5월 3주차 단어시험", "examDate": "2026-05-13" },
      { "year": 2026, "month": 5, "week": 4, "label": "5월 4주", "score": 92.0,
        "examName": "5월 4주차 단어시험", "examDate": "2026-05-20" } ] },
  "mock": [
    { "scoreId": 355, "examName": "3월 학평", "subject": "영어",
      "gradeLevel": 1, "examDate": "2026-03-26" } ],
  "internal": [
    { "scoreId": 301, "examName": "1학기 중간고사", "subject": "영어",
      "rawScore": 96.0, "gradeLevel": 1, "examDate": "2026-04-28" } ] }
```

- `word`는 **`year` · `month` · `week` 오름차순**입니다. 그래프의 가로축 순서 그대로 내려주세요.
  프론트에서 정렬하게 두면 달이 바뀌는 지점에서 어긋납니다.
- `score`는 **100점 만점 환산값**입니다 (`scores.raw_score`). 원점수를 그대로 내리지 마세요.
- 시험을 안 본 주는 **배열에 넣지 마세요.** `null` 점을 넣으면 선이 0으로 떨어집니다.
- `internal` · `mock`은 `examDate` 내림차순 목록입니다.

**등수·백분위·반 평균은 어디에도 포함하지 않습니다.**

---

## 7. 공통 API

### GET `/notices?studentId=88`

정렬: `pinned DESC`, `published_at DESC`. `published_at IS NOT NULL`만.
`studentId`는 학부모가 자녀를 지정할 때 사용합니다.

**`studentId`를 받으므로 서비스 첫 줄은 `studentAccessGuard.requireAccessible(studentId)`입니다.**
`/api/notices/**`는 공통 경로라 `SecurityConfig`의 역할 검사가 걸리지 않습니다.
경로가 공통이라고 검증을 건너뛰지 마세요 — 1-5절의 규칙에 예외는 없습니다.
`studentId`가 없으면(학생 본인 호출) `requireSelf()`를 씁니다.

**공지 본문을 HTML로 렌더링하지 마세요.** 일반 텍스트 + 줄바꿈만입니다.

### GET `/health`

```jsonc
{ "success": true, "data": "ok" }
```

Docker healthcheck가 이 경로를 씁니다. 인증 불필요.

---

## 8. Enum 레퍼런스

DB에 **문자열로 저장**합니다. `@Enumerated(EnumType.STRING)` 필수.

| Enum | 값 |
|---|---|
| `UserRole` | `TEACHER` `STUDENT` `PARENT` |
| `UserStatus` | `ACTIVE` `INACTIVE` |
| `StudentStatus` | `ENROLLED` `WITHDRAWN` |
| `ClassRoomStatus` | `ACTIVE` `CLOSED` |
| `LessonAttendanceStatus` | `PENDING` `CONFIRMED` |
| `AttendanceStatus` | `PRESENT` `LATE` `ABSENT` `SICK` `EXCUSED` |
| `SubmissionStatus` | `NOT_SUBMITTED` `SUBMITTED` `CHECKED` |
| `ExamType` | `MIDTERM` `FINAL` |
| `ScoreType` | `WORD` `INTERNAL` `MOCK` |
| `MaterialCategory` | `LESSON` `TEXTBOOK` `PAST_EXAM` `ETC` |
| `MaterialVisibility` | `PUBLIC` `CLASS` |
| `NoticeScope` | `ALL` `SCHOOL` `GRADE` `CLASS` |
| `OnlineTestStatus` | `IN_PROGRESS` `SUBMITTED` |
| `ClinicStatus` | `OPEN` `CLOSED` |
| `ReservationStatus` | `RESERVED` `CANCELED` `MOVED` |
| `ChangeRequestStatus` | `PENDING` `APPROVED` `REJECTED` |

**상태 전이**

```
submissions            NOT_SUBMITTED → SUBMITTED → CHECKED   (is_late는 별도 boolean)
lessons                PENDING → CONFIRMED                   (출석 확정)
students               ENROLLED → WITHDRAWN                  (복구 가능)
clinics                OPEN → CLOSED                         (신청 마감)
clinic_reservations    RESERVED → CANCELED                   (취소)
                       RESERVED → MOVED                      (변경 승인. 새 RESERVED 행 생성)
clinic_change_requests PENDING → APPROVED | REJECTED
```

`clinic_change_requests.reason_code`는 **enum이 아닙니다.** 옵션 목록이 미확정이라 문자열로 둡니다.

---

## 9. 구현 체크리스트

새 엔드포인트를 만들 때마다 확인합니다.

- [ ] `studentId`를 받는가 → 서비스 첫 줄에 `requireAccessible` (`/notices` 포함, 예외 없음)
- [ ] 간접 참조(`submissionId` 등)인가 → 소속 학생을 찾아 동일 검증
- [ ] 학생·학부모 조회인가 → `published_at IS NOT NULL`
- [ ] 반 학생을 뽑는가 → `findActiveStudents(classRoomId, targetDate)`
- [ ] 학생 이름을 쓰는가 → `students.name` (`users.name`을 타면 미가입 학생이 사라짐)
- [ ] 학부모 응답인가 → 사진·피드백·숙제 내용·수업 내용·자료실이 없는가
- [ ] 409를 던지는가 → 화면 문구가 다르면 코드도 다른가
- [ ] 응답이 `ApiResponse`로 감싸졌는가
- [ ] 목록인가 → `PageResponse` (예외 15개는 1-3 참조)
- [ ] 경로 변수가 `{id}`가 아니라 명시적 이름인가
- [ ] enum이 문자열로 직렬화되는가
- [ ] 시각이 ISO 8601 + KST 오프셋인가
- [ ] `s3Key`·내부 경로가 응답에 새지 않는가
- [ ] 개인정보(이름·전화번호)가 불필요하게 노출되지 않는가
- [ ] N+1이 없는가 (목록 API는 쿼리 로그 확인)

---

## 10. 이 명세에 없는 것

아래는 **의도적으로 제외**되었습니다. 엔드포인트를 만들지 마세요.

| 영역 | 사유 |
|---|---|
| 결제·수강료 | 학원장 확정: 미포함 |
| SMS·알림톡 발송 | 건당 과금. 1차 범위 밖 |
| 숙제 미제출 자동 독려 | 발송 수단 없음. 앱 푸시로 |
| 공부 시간 기록·랭킹·스트릭 | 웹에서 측정 불가 |
| 푸시 알림·알림 센터 | 앱 단계 |
| 영상 업로드·트랜스코딩 | YouTube 링크 문자열만 저장 |
| 등수·백분위·반 평균 | 상대 지표 노출 안 함 |
| 공지 읽음 표시 | 1차 범위 밖 |
| 소셜 로그인·자유 회원가입 | 학원 발급 계정만 |
| 엑셀 일괄 업로드 | 요구사항 없음 |
| 학생 상호 열람 | 만들지 않음 |
| **수강 후기** | 학원장 요청으로 제외. `reviews` 테이블·API 전부 없음 |
| **학부모의 수업 영상·레포트** | 학부모는 "했는지 여부"만 봄 |
| **학부모의 숙제 사진·피드백·숙제 내용** | 목록의 제출 여부까지만 |
| **학부모의 자료실** | 자료실은 학생 전용(`/student/materials`) |
| 클리닉 학생 상호 명단 열람 | 인원 수만 노출. 다른 학생 이름 금지 |
