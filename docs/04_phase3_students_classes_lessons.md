# Phase 3 — 학생 · 반 · 수업 관리

**선행 조건:** Phase 2
**목표:** 선생님이 학생을 등록하고 반에 배정하고 수업일을 만들 수 있다. 이후 Phase의 데이터 기반이 완성된다.
**화면:** T-2, T-3, T-4, T-12

---

## 1. 학생 관리 (T-2)

### 1-1. API

| Method | Endpoint | 설명 |
|---|---|---|
| GET | `/api/teacher/students` | 목록 (필터·검색·페이징) |
| POST | `/api/teacher/students` | 등록 |
| GET | `/api/teacher/students/{studentId}` | 상세 |
| PATCH | `/api/teacher/students/{studentId}` | 수정 |
| POST | `/api/teacher/students/{studentId}/withdraw` | 퇴원 |
| POST | `/api/teacher/students/{studentId}/restore` | 퇴원 취소 |
| DELETE | `/api/teacher/students/{studentId}` | **삭제** (운영 기록 없을 때만) |
| POST | `/api/teacher/students/{studentId}/signup-code` | 회원가입 코드 재발급 |
| POST | `/api/teacher/students/{studentId}/reset-password` | 비밀번호 초기화 |
| GET | `/api/teacher/schools` | 학교 목록 |

### GET `/api/teacher/students`

쿼리 파라미터: `schoolId`, `grade`, `classRoomId`, `status`, `keyword`, `sort`, `page`, `size`

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "studentId": 88, "name": "서동환",
        "schoolName": "A고등학교", "grade": 2,
        "classRooms": ["고2 심화반", "썸머 집중반"],
        "studentPhone": "01011112222", "studentSignedUp": true,
        "parentPhone": "01098765432", "parentLinked": false,
        "status": "ENROLLED",
        "createdAt": "2026-03-02T19:41:00+09:00"
      }
    ],
    "page": 0, "size": 20, "totalElements": 197, "totalPages": 10
  }
}
```

`sort`: `name`(기본) | `recent`(`students.created_at DESC`)

**`recent` 정렬이 제3자 탐지 수단입니다.** 반 코드에는 전화번호 대조가 없어서
코드를 아는 사람은 누구나 가입합니다. 막을 방법이 없으므로 **가입 후 발견해서 지웁니다**
(`DELETE /api/teacher/students/{studentId}`).

`createdAt`은 `students.created_at`입니다. 반 코드 자가 가입은 이 값이 곧 가입 시각이고,
선생님이 직접 등록한 학생은 등록 시각입니다.

**등록 기간에는 매일 `sort=recent`로 상단만 훑으세요.** 200명 목록을 이름순으로 놓고
낯선 이름 하나를 찾는 것은 실제로 불가능합니다. 최근 가입 20명은 30초면 확인합니다.
등록 기간이 끝나면 코드를 닫고(T-3), 그 뒤로는 신규 가입 자체가 없습니다.

**`studentSignedUp`과 `parentLinked`가 `false`인 학생을 목록에서 눈에 띄게 표시하세요.**
200명 중 아직 가입하지 않은 사람을 찾아 코드를 다시 알려주는 것이 선생님의 실제 업무입니다.

| 필드 | 의미 |
|---|---|
| `studentSignedUp` | `students.user_id IS NOT NULL` — 학생 본인이 회원가입을 마쳤는지 |
| `parentLinked` | `students.parent_id IS NOT NULL` — 학부모가 회원가입하고 연결됐는지 |

`loginId`는 내려주지 않습니다. 가입 전에는 존재하지 않고, 가입 후에는 `studentPhone`과 같은 값입니다.

`keyword`는 학생 이름과 전화번호(`login_id`)를 대상으로 부분 검색합니다. 번호 뒷자리 4개로
찾는 경우가 많으니 앞부분 일치가 아니라 **부분 일치**로 구현하세요.

### POST `/api/teacher/students`

```json
// Request
{
  "name": "서동환",
  "schoolId": 1,
  "grade": 2,
  "studentPhone": "01011112222",
  "parentPhone": "01098765432",
  "memo": "독해 보강 필요",
  "classRoomIds": [3, 7]
}

// Response
{
  "success": true,
  "data": {
    "studentId": 88,
    "signupCodes": {
      "student": { "code": "K7F2QX", "phone": "01011112222",
                   "expiresAt": "2026-08-03T23:59:59+09:00" },
      "parent":  { "code": "M4T8BW", "phone": "01098765432",
                   "expiresAt": "2026-08-03T23:59:59+09:00" }
    }
  }
}
```

> **이 API는 보조 경로입니다.** 주 경로는 반 코드로 학생이 직접 가입하는 것입니다(Phase 2).
> 여기는 **폰이 없거나 코드를 못 쓰는 학생**을 선생님이 대신 넣어 주는 용도입니다.
> 200명을 이걸로 다 등록하려 하지 마세요. 코드 400장을 구두로 전달하게 됩니다.

**계정(`users`)은 여기서 만들지 않습니다.** 당사자가 회원가입할 때 만들어집니다.
그래서 `initialPassword`도 없습니다. 초기 비밀번호는 가입 시 서버가 `0000`으로 설정합니다.

**`name`은 `students.name`에 저장합니다.** `users`가 아닙니다. 미가입 학생도 출석부·숙제
명단에 이름이 떠야 하는데, `users` 행은 가입 전까지 없습니다.

**전화번호 칸이 두 개입니다. 합치지 마세요.**

| 필드 | 용도 |
|---|---|
| `studentPhone` | 학생용 `signup_codes.phone`. 가입 시 대조하고, 가입 후 학생의 `login_id`가 됨 |
| `parentPhone` | 학부모용 `signup_codes.phone`. 가입 시 대조하고, 가입 후 학부모의 `login_id`가 됨 |

**두 번호가 같으면 409 `DUPLICATE_RESOURCE`.** `login_id`가 UNIQUE라 같은 번호로 두 계정을
만들 수 없습니다. 학생이 본인 휴대폰이 없는 경우가 실제로 생기는데, 그때는 등록을 막고
다른 번호를 받으세요. **나중에 회원가입 단계에서 터지면 원인을 찾기 어렵습니다.**

**처리 순서 (하나의 트랜잭션)**

1. 두 번호 정규화(숫자만). `studentPhone == parentPhone`이면 409 `DUPLICATE_RESOURCE`
2. 각 번호가 기존 `users.login_id` 또는 미사용 `signup_codes.phone`과 중복이면 409
3. `students` 생성 (`name` 저장, `user_id = null`, `parent_id = null`)
4. `classRoomIds`가 있으면 `enrollments` 생성 (`joined_at = 오늘`)
5. `signup_codes` **2행** 생성 (`STUDENT` / `PARENT`) 후 코드 2장 반환

**등록과 동시에 코드 두 장을 발급합니다.** 선생님이 등록 후 별도 화면에 다시 들어가게 만들면 실제로 안 씁니다.

**`users` 행은 만들지 않습니다.** 계정은 당사자가 회원가입할 때 생깁니다. 선생님은 200명을 먼저
다 입력해 두고 출석·숙제를 운영할 수 있고, 계정은 각자 가입하는 대로 붙습니다.

**`loginId`는 요청에 없습니다.** 가입 시 입력한 전화번호가 그대로 아이디가 됩니다.
`stu0088` 같은 별도 아이디를 쓰지 않는 이유는 학부모·학생이 외우지 못해 문의가 반복되기 때문입니다.

### POST `/api/teacher/students/{studentId}/withdraw`

```json
{ "withdrawnAt": "2026-07-31" }
```

**처리 내용**

1. `students.status = 'WITHDRAWN'`, `withdrawn_at` 기록
2. `users.status = 'INACTIVE'` (로그인 차단). **`user_id`가 `null`이면(미가입) 건너뜁니다**
3. 진행 중인 `enrollments`의 `left_at = withdrawnAt`
4. 해당 학부모가 다른 재원 자녀가 없으면 학부모 계정도 `INACTIVE`
5. 미사용 `signup_codes`가 남아 있으면 폐기 (퇴원생 코드로 가입되면 안 됩니다)

**데이터를 삭제하지 마세요.** 과거 출석·성적·숙제 기록은 그대로 보존합니다. 재등록 가능성도 있고, 삭제하면 통계가 깨집니다.

### DELETE `/api/teacher/students/{studentId}`

**반 코드로 들어온 제3자를 지우는 용도입니다.** 실제 학생에게 쓰지 마세요.

반 코드는 여러 명이 나눠 쓰는 값이라 코드를 아는 사람은 누구나 가입할 수 있습니다
(`03_phase2_auth.md` 4절). 선생님이 T-2 명단에서 모르는 이름을 발견하면 여기서 지웁니다.

```json
// Response
{
  "success": true,
  "data": { "deletedStudentId": 91, "deletedUser": true, "deletedEnrollments": 1 }
}
```

**삭제 차단 조건** — 하나라도 있으면 409 `STUDENT_HAS_RECORDS`

```sql
SELECT count(*) FROM attendances              WHERE student_id = :studentId;
SELECT count(*) FROM submissions              WHERE student_id = :studentId
                                                AND status <> 'NOT_SUBMITTED';
SELECT count(*) FROM scores                   WHERE student_id = :studentId;
SELECT count(*) FROM online_test_submissions  WHERE student_id = :studentId;
SELECT count(*) FROM clinic_reservations      WHERE student_id = :studentId;
SELECT count(*) FROM lesson_views             WHERE student_id = :studentId;
-- 학부모가 이미 가입해 연결되어 있으면(parent_id IS NOT NULL) 역시 409
```

**차단되면 `withdraw`를 안내하세요.** 반 삭제(`DELETE /class-rooms/{id}`)와 같은 원칙입니다.

| 상황 | 써야 할 것 |
|---|---|
| 반 코드로 들어온 모르는 사람 | `DELETE` |
| 실제로 다녔던 학생이 그만둠 | `POST /withdraw` |

**`submissions`가 `NOT_SUBMITTED`만 있는 것은 삭제를 막지 않습니다.** 출제 시 대상 전원의 행이
자동 생성되므로(`06_phase5_homework.md` 1-1), 가입 직후에도 행이 이미 존재합니다.
"행이 있으면 409"로 구현하면 **아무도 지울 수 없습니다.** 숙제 삭제와 같은 함정입니다.

**삭제 순서 (하나의 트랜잭션)**

1. `submissions` 삭제 (`NOT_SUBMITTED`만 남아 있음이 위에서 보장됨)
2. `enrollments` 삭제
3. 이 학생 앞으로 발급된 `signup_codes` 삭제 (가입 시 자동 발급된 학부모용 포함)
4. `students` 삭제
5. `users` 삭제 (`user_id`가 있으면). 없으면 건너뜀

**FK 순서를 지키세요.** `students`를 먼저 지우면 제약 위반입니다.

**삭제 전 확인 다이얼로그에 이름·전화번호·가입일시를 함께 띄우세요.** 되돌릴 수 없고,
실제 학생을 잘못 지우면 복구 경로가 없습니다.

`dryRun`은 필요 없습니다. 대상이 1명이고 차단 조건이 화면에 이미 보입니다.

### POST `/api/teacher/students/{studentId}/signup-code`

회원가입 코드를 잃어버렸을 때 재발급합니다. 미사용 코드가 남아 있으면 **폐기하고 새로 발급**합니다.
유효한 코드가 여러 개 떠다니면 관리가 안 됩니다.

```json
// Request
{ "target": "STUDENT", "phone": "01011112222" }

// Response
{
  "success": true,
  "data": { "target": "STUDENT", "code": "K7F2QX",
            "phone": "01011112222", "expiresAt": "2026-08-03T23:59:59+09:00" }
}
```

`target`: `STUDENT` | `PARENT`. `phone`을 함께 주면 번호도 같이 바꿉니다(번호를 잘못 입력한 경우).
생략하면 기존 번호를 유지합니다.

**이미 가입을 마친 대상에게는 400을 반환하세요.** 학생이 가입했는데 학생용 코드를 다시 발급하면
같은 번호로 두 번째 계정을 만들려다 409가 납니다. 그때는 코드가 아니라
`reset-password`가 필요한 상황입니다.

**코드 생성 규칙**

- 6자리, 영문 대문자 + 숫자
- **혼동되는 문자 제외: `0`, `O`, `1`, `I`, `L`** — 선생님이 구두로 전달하고 상대가 입력하는 값입니다
- 유효기간 7일
- 중복 시 재생성 (`code`에 UNIQUE 제약이 있음)

```java
private static final String CHARS = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
```

### POST `/api/teacher/students/{studentId}/reset-password`

**비밀번호 분실 시 유일한 복구 경로입니다.** 이메일·SMS가 범위 밖이라 자동 재설정을 만들 수 없습니다.

```json
// Request — newPassword를 주면 그 값으로, 생략(null)하면 서버가 8자리 임시 비밀번호를 생성
{ "target": "STUDENT", "newPassword": null }

// Response
{
  "success": true,
  "data": {
    "target": "STUDENT",
    "loginId": "01011112222",
    "temporaryPassword": "7K2M9QXF"
  }
}
```

`target`: `STUDENT`(기본) | `PARENT`. `PARENT`면 해당 학생에 연결된 학부모 계정을 초기화합니다.

**아직 회원가입하지 않은 대상이면 400 `VALIDATION_FAILED`.** 계정이 없으니 초기화할 것도 없습니다.
이 경우 필요한 것은 `signup-code` 재발급입니다. 오류 메시지에서 두 경로를 구분해 주세요.

**처리**

1. `users.password_hash` 갱신 (BCrypt)
2. `users.must_change_password = true` — 다음 로그인 시 변경 화면으로 강제 이동
3. 해당 사용자의 refresh 토큰 **전부 폐기**. 분실이 곧 유출일 수 있습니다

`temporaryPassword`는 **이 응답에서 한 번만** 내려갑니다. 저장하지도, 다시 조회하지도 못합니다.
초대코드와 같은 문자셋(`0 O 1 I L` 제외)을 씁니다. 선생님이 구두로 불러 주는 값이기 때문입니다.

화면에는 초대코드와 마찬가지로 **복사 버튼**을 두세요.

### POST `/api/teacher/students/promote` (T-12)

학년 일괄 진급입니다.

```json
// Request
{ "schoolId": 1, "dryRun": true }

// Response
{
  "success": true,
  "data": {
    "dryRun": true,
    "grade1to2": 31, "grade2to3": 28,
    "grade3Graduating": 25
  }
}
```

**동작**

- 3학년 → 졸업 처리 대상으로 집계만 하고 **자동 퇴원시키지 않습니다.** 목록을 반환해 선생님이 개별 확인하게 하세요.
- 2학년 → 3학년, 1학년 → 2학년 일괄 상향
- `dryRun: true`면 변경하지 않고 예상 결과만 반환

**`dryRun`을 반드시 구현하세요.** 200명의 학년을 한 번에 바꾸는 작업이고 되돌리기 어렵습니다. 확인 화면 없이 실행되면 사고입니다.

`dryRun: false` 실행 시 감사 로그를 남기세요 (누가 언제 몇 명을 진급시켰는지).

---

## 2. 반 관리 (T-3)

### 2-1. API

| Method | Endpoint | 설명 |
|---|---|---|
| GET | `/api/teacher/class-rooms` | 목록 |
| POST | `/api/teacher/class-rooms` | 생성 |
| GET | `/api/teacher/class-rooms/{classRoomId}` | 상세 |
| PATCH | `/api/teacher/class-rooms/{classRoomId}` | 이름·시간 수정 |
| DELETE | `/api/teacher/class-rooms/{classRoomId}` | **삭제** (기록 없을 때만) |
| POST | `/api/teacher/class-rooms/{classRoomId}/close` | 종료 처리 |
| POST | `/api/teacher/class-rooms/{classRoomId}/join-code` | **가입 코드 재발급 · 여닫기** |
| GET | `/api/teacher/class-rooms/{classRoomId}/students` | 명단 |
| POST | `/api/teacher/class-rooms/{classRoomId}/students` | 학생 배정 (복수) |
| DELETE | `/api/teacher/class-rooms/{classRoomId}/students/{studentId}` | 배정 해제 |

### POST `/api/teacher/class-rooms`

```json
// Request
{
  "name": "고2 심화반",
  "dayOfWeek": 3,
  "startTime": "19:00",
  "termStart": "2026-03-02",
  "termEnd": "2027-02-28",
  "memo": null
}

// Response
{
  "success": true,
  "data": {
    "classRoomId": 3,
    "name": "고2 심화반",
    "joinCode": "HK7F2Q",
    "joinCodeActive": true
  }
}
```

**반에 학교·학년이 없습니다.** 선생님이 이름을 직접 정합니다. `학교`·`학년`은 학생에만 있습니다.

필수는 `name` 하나입니다. 나머지는 전부 선택입니다. 요일·시간을 비워 두면
`POST /lessons/bulk`(정기 수업일 일괄 생성)를 쓸 수 없으니, 정기반이면 채우도록 UI에서 유도하세요.

**활성 반끼리 이름이 겹치면 409 `DUPLICATE_RESOURCE`.** 종료된 반의 이름은 다시 쓸 수 있습니다
(`uq_class_rooms_name`이 `WHERE status = 'ACTIVE'` 부분 인덱스).

`dayOfWeek`: 1=월 ~ 7=일 (ISO-8601, `java.time.DayOfWeek.getValue()`와 동일)

**`joinCode`는 서버가 생성합니다.** 요청에서 받지 마세요. 선생님이 직접 정하면
`고2반`처럼 추측 가능한 값을 넣습니다.

- 6자리, 영문 대문자 + 숫자, 혼동 문자 제외 (`0` `O` `1` `I` `L`)
- `signup_codes.code`와도 겹치면 안 됩니다 — 가입 화면이 코드 하나만 받아 서버가 종류를 판별합니다
- 생성 시 두 테이블을 모두 조회해 중복이면 재생성

```java
private static final String CHARS = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
```

### POST `/api/teacher/class-rooms/{classRoomId}/join-code`

가입 코드를 재발급하거나 여닫습니다.

```json
// Request — active만 보내면 코드는 그대로 두고 열고 닫기만 함
{ "regenerate": false, "active": false }

// Response
{
  "success": true,
  "data": { "joinCode": "HK7F2Q", "joinCodeActive": false }
}
```

| 상황 | 요청 |
|---|---|
| 등록 기간이 끝났다 | `{ "active": false }` |
| 코드가 외부로 샌 것 같다 | `{ "regenerate": true, "active": true }` |
| 다음 학기 등록을 다시 연다 | `{ "regenerate": true, "active": true }` |

**재발급하면 이전 코드는 즉시 무효입니다.** 이미 가입한 학생에게는 영향이 없습니다
(`enrollments`는 그대로).

### 등록 기간이 끝나면 코드를 닫으세요

**반 코드는 20명이 나눠 쓰는 값이라 전화번호로 본인을 묶을 수 없습니다.**
개인 코드(`signup_codes`)와 결정적으로 다른 점입니다. 코드를 아는 사람은 누구나 가입할 수 있고,
가입하면 그 반의 수업영상·자료실·숙제·공지를 봅니다.

T-3 반 목록에 코드 상태를 **항상 보이게** 두고, 열려 있는 반은 눈에 띄게 표시하세요.
학기 중 내내 열어두는 것이 가장 흔한 사고 경로입니다.

`students.name`이 저장되므로 T-2 명단에서 모르는 이름을 발견할 수 있습니다.
사후 탐지 수단이지 예방책이 아닙니다.

### PATCH `/api/teacher/class-rooms/{classRoomId}`

이름·요일·시간·기간·메모를 고칩니다. 보낸 필드만 바꾸세요.

```json
{ "name": "고2 심화반 (수)" }
```

**이름 변경은 과거 기록에도 소급 적용됩니다.** `lessons`·`attendances`가 `class_room_id`를
참조하므로 지난 수업의 반 이름 표기도 함께 바뀝니다. 의도된 동작입니다. 학기 중 반 이름이
바뀌었을 때 과거만 옛 이름으로 남기려면 별도 이력 테이블이 필요한데, 200명 규모에서는 과합니다.

### DELETE `/api/teacher/class-rooms/{classRoomId}`

**잘못 만든 반을 지우는 용도입니다.** 운영이 시작된 반은 지울 수 없습니다.

```sql
-- 아래 중 하나라도 있으면 409
SELECT count(*) FROM lessons     WHERE class_room_id = :classRoomId;
SELECT count(*) FROM enrollments WHERE class_room_id = :classRoomId;
SELECT count(*) FROM homeworks   WHERE class_room_id = :classRoomId;
```

수업·배정 이력·숙제가 하나라도 있으면 **409를 반환하고 `close`를 안내하세요.**
지워버리면 그 반의 출석·숙제 기록이 FK로 묶여 있어 삭제 자체가 실패하거나, 억지로 지우면
학생의 과거 기록이 사라집니다.

| 상황 | 써야 할 것 |
|---|---|
| 이름을 잘못 입력해 방금 만든 반 | `DELETE` |
| 학기가 끝난 반 | `POST /close` (`status = 'CLOSED'`) |

### POST `/api/teacher/class-rooms/{classRoomId}/students`

```json
{ "studentIds": [88, 91, 97], "joinedAt": "2026-03-02" }
```

이미 활성 상태로 배정된 학생은 무시하고 나머지만 추가합니다 (409를 던지지 말고 멱등하게 처리). `enrollments`에 `WHERE left_at IS NULL` 부분 유니크 인덱스가 있으므로 중복 삽입은 DB에서도 막힙니다.

**학교·학년 일치 검사를 하지 마세요.** 반에 학교·학년이 없어 비교할 대상이 자체가 없습니다.
어떤 학생이든 어떤 반에 넣을 수 있습니다.

### DELETE `.../students/{studentId}`

행을 삭제하지 말고 `left_at = 오늘`을 기록합니다. 과거 수업·출석 기록이 이 학생과 연결되어 있기 때문입니다.

### GET `/api/teacher/class-rooms/{classRoomId}/students`

```json
{
  "success": true,
  "data": {
    "classRoom": { "id": 3, "name": "고2 심화반" },
    "students": [
      { "studentId": 88, "name": "서동환", "grade": 2, "joinedAt": "2026-03-02" }
    ]
  }
}
```

**기본은 현재 재원생만** (`left_at IS NULL`). `asOf=2026-05-20` 파라미터를 주면 그 날짜 기준 명단을 반환합니다. 과거 출석 확정 화면에서 필요합니다.

---

## 3. 재원생 조회 규칙

**Phase 3에서 만드는 이 쿼리를 이후 모든 Phase가 재사용합니다.** 별도 메서드로 분리해 중복 구현을 막으세요.

```java
// EnrollmentRepository
@Query("""
    SELECT e.student FROM Enrollment e
    WHERE e.classRoom.id = :classRoomId
      AND e.joinedAt <= :targetDate
      AND (e.leftAt IS NULL OR e.leftAt > :targetDate)
      AND e.student.status = 'ENROLLED'
    ORDER BY e.student.name
    """)
List<Student> findActiveStudents(Long classRoomId, LocalDate targetDate);
```

이 조건이 빠지면 다음이 전부 잘못됩니다.

- 퇴원생이 출석부에 계속 뜨고 결석으로 쌓임
- 퇴원생에게 숙제가 계속 출제됨
- 반 인원 통계가 실제보다 많이 나옴

정렬은 학생 이름 기준입니다. 선생님이 출석부에서 이름을 찾는 순서와 일치해야 합니다.

### `ORDER BY e.student.name`입니다. `e.student.user.name`이 아닙니다

**이걸 틀리면 미가입 학생이 명단에서 통째로 사라집니다.**

```java
ORDER BY e.student.user.name   // ❌ 미가입 학생이 전부 빠진다
ORDER BY e.student.name        // ✅
```

`students.user_id`는 nullable입니다. JPQL에서 nullable 연관을 `e.student.user.name`처럼
경로로 타면 **암묵적 INNER JOIN**이 생겨 `user_id IS NULL`인 행이 결과에서 제외됩니다.

에러가 나지 않는 것이 이 버그의 핵심입니다.

| 증상 | 실제 원인 |
|---|---|
| 출석부에 학생이 8명만 뜬다 | 나머지 12명이 아직 가입 안 함 |
| `targetCount: 20`이 `8`로 나온다 | 위와 같음 |
| 숙제를 냈는데 절반이 못 받았다 | 위와 같음 |

학기 초에는 미가입 학생이 다수라, 출석부가 통째로 비어 보입니다.
이름은 항상 `students.name`에 있으므로 `users`를 탈 이유가 없습니다.

---

## 4. 수업 관리 (T-4)

### 4-1. API

| Method | Endpoint | 설명 |
|---|---|---|
| GET | `/api/teacher/lessons` | 목록 (`classRoomId`, `from`, `to`) |
| POST | `/api/teacher/lessons` | 수업일 생성 |
| POST | `/api/teacher/lessons/bulk` | 정기 수업일 일괄 생성 |
| GET | `/api/teacher/lessons/{lessonId}` | 상세 |
| PATCH | `/api/teacher/lessons/{lessonId}` | 내용 작성·수정 |
| POST | `/api/teacher/lessons/{lessonId}/publish` | 공개 (학생·학부모에게 노출) |
| DELETE | `/api/teacher/lessons/{lessonId}` | 삭제 |

### POST `/api/teacher/lessons`

```json
{
  "classRoomId": 3,
  "lessonDate": "2026-05-20",
  "year": 2026, "month": 5, "week": 4,
  "title": "관계대명사 what과 관계부사",
  "videoUrl": "https://youtu.be/xxxxxxxxxxx",
  "content": "관계대명사 what과 that의 구분을 학습하였으며, 이후 관계부사 where·when·why의 용법까지 진도를 진행하였습니다.",
  "keyPoints": "선행사가 있으면 that, 없으면 what을 씁니다. 관계부사는 「전치사 + 관계대명사」로 바꿔 쓸 수 있다는 점을 반드시 기억하세요.",
  "nextPreview": "분사구문 (부대상황 with + 목적어 + 분사)"
}
```

`lessons`에 `UNIQUE (class_room_id, lesson_date)`가 있으므로 같은 반에 같은 날짜 수업을 두 번 만들 수 없습니다. 중복 시 409 `DUPLICATE_RESOURCE`.

**`year`·`month`·`week`는 필수입니다.** T-4 화면이 주차를 먼저 고르는 구조라, 선택한 값을 그대로 보냅니다.
`bulk` 생성 시에는 서버가 각 날짜의 주차를 계산해 채우되, 선생님이 이후 화면에서 고칠 수 있게 하세요.

### POST `/api/teacher/lessons/bulk`

반의 `dayOfWeek`와 기간을 기준으로 수업일을 미리 생성합니다.

```json
{
  "classRoomId": 3,
  "from": "2026-03-02",
  "to": "2026-07-31",
  "skipDates": ["2026-05-05", "2026-06-06"]
}
```

내용은 비어 있고 `attendance_status = 'PENDING'`, `published_at = null` 상태로 생성됩니다. 이미 존재하는 날짜는 건너뜁니다.

**이 API가 실제 운영 편의를 크게 좌우합니다.** 한 학기 20회 수업일을 매번 손으로 만들게 하면 안 됩니다.

### 4-2. 공개 상태

| `published_at` | 선생님 | 학생·학부모 |
|---|---|---|
| `null` | 보임 (작성 중) | **안 보임** |
| 값 있음 | 보임 | 보임 |

학생·학부모 조회 쿼리에는 항상 `published_at IS NOT NULL` 조건을 넣습니다. 작성 중인 초안이 학부모에게 노출되면 곤란합니다.

`PATCH`로 내용을 수정할 때는 `published_at`을 건드리지 않습니다. 공개는 명시적으로 `POST /publish`로만 이루어집니다.

### 4-3. YouTube URL

`videoUrl`은 문자열만 저장합니다. **영상 파일 업로드, 트랜스코딩, 스트리밍 서버를 구현하지 마세요.**

저장 시 URL 형식을 검증하고 비디오 ID를 추출할 수 있게 하세요. 프론트에서 iframe 임베드에 사용합니다.

```
허용 형식
https://youtu.be/{videoId}
https://www.youtube.com/watch?v={videoId}
https://www.youtube.com/embed/{videoId}
```

세 형식 모두 받아 `videoId`를 파싱하고, 프론트에는 `https://www.youtube.com/embed/{videoId}`로 변환해 내려주는 것이 편합니다. 응답 DTO에 `videoId` 필드를 함께 담으세요.

미등록(unlisted) 링크를 사용하므로 **URL이 유출되면 외부에서도 시청 가능합니다.** 이는 학원장이 인지하고 동의한 사항이며, 별도 보호 로직을 구현하지 않습니다.

---

## 5. 프론트엔드

### 5-1. 화면 구성

| ID | 경로 | 핵심 UI |
|---|---|---|
| T-2 | `/teacher/students` | 목록 + 학교·학년·반 필터 + 검색. **미가입 학생·학부모 강조** |
| T-2 | `/teacher/students/new` | 등록 폼. 저장 후 **코드 2장** 모달 표시 |
| T-2 | `/teacher/students/:id` | 상세 + 수정 + 퇴원 + 코드 재발급 + **비밀번호 초기화** |
| T-3 | `/teacher/class-rooms` | 반 목록 + **가입 코드와 열림 상태** |
| T-3 | `/teacher/class-rooms/:id` | 명단 + 학생 배정·해제 + **코드 복사·재발급·닫기** |
| T-4 | `/teacher/lessons` | **주차 선택** → 반 선택 → 수업일 목록 (작성 여부·공개 여부 표시) |
| T-4 | `/teacher/lessons/:id` | 수업 내용 작성 폼 + 공개 버튼 |
| T-12 | `/teacher/promote` | 진급 미리보기 → 확인 → 실행 |

### 5-2. 회원가입 코드 전달 UX

학생 등록 직후 모달로 **코드 2장을 나란히** 보여주고 각각 **복사 버튼**을 두세요.
학생용과 학부모용이 섞이면 서로의 계정으로 가입하게 되므로, 누구에게 줄 코드인지
번호와 함께 크게 표시해야 합니다.

```
[○○학원] 회원가입 안내 (학생용)
아래 링크에서 코드와 본인 전화번호를 입력해 주세요.
https://{도메인}/signup
회원가입 코드: K7F2QX
전화번호: 010-1111-2222
초기 비밀번호는 0000입니다. 로그인 후 꼭 변경해 주세요.
(7일 내 가입해 주세요)
```

**복사 버튼 없이 코드만 보여주면 선생님이 손으로 옮겨 적다가 틀립니다.**

### 5-3. 수업 목록 표시

수업일 목록에 세 가지 상태를 한눈에 보이게 하세요.

| 표시 | 의미 |
|---|---|
| 내용 미작성 | `content`가 비어 있음 |
| 미공개 | `published_at`이 `null` |
| 출석 미확정 | `attendance_status = 'PENDING'` |

선생님이 "무엇을 안 했는지" 찾는 화면입니다. 완료된 항목보다 미완료 항목이 눈에 띄어야 합니다.

---

## 6. 완료 조건 (DoD)

- [ ] 학생을 등록하면 `students` + `signup_codes` **2행**이 한 트랜잭션으로 생성된다
- [ ] **등록 시점에 `users` 행이 생기지 않는다** (`students.user_id IS NULL`)
- [ ] 등록 응답에 코드 2장(학생용·학부모용)이 포함되고 각각 복사할 수 있다
- [ ] 코드에 `0`, `O`, `1`, `I`, `L`이 포함되지 않는다
- [ ] 코드 재발급 시 이전 미사용 코드가 폐기된다
- [ ] 이미 가입한 대상에게 코드를 재발급하면 400이 반환된다
- [ ] 비밀번호 초기화 후 `must_change_password = true`가 되고 refresh 토큰이 전부 폐기된다
- [ ] `target: "PARENT"`로 학부모 계정도 초기화된다 (미가입이면 400)
- [ ] 이미 쓰이는 번호로 학생 등록 시 409 `DUPLICATE_RESOURCE`가 반환된다
- [ ] `studentPhone`과 `parentPhone`이 같으면 409가 반환된다
- [ ] 목록에서 `studentSignedUp`·`parentLinked`로 미가입자를 구분할 수 있다
- [ ] **`sort=recent`로 최근 가입 순 정렬이 되고 `createdAt`이 응답에 있다**
- [ ] **반 코드로 방금 가입한 학생이 `sort=recent` 상단에 나타난다** (제3자 탐지 경로)
- [ ] **운영 기록이 없는 학생은 `DELETE`로 지워지고 `users`·`enrollments`·`signup_codes`가 함께 정리된다**
- [ ] **`NOT_SUBMITTED` 상태의 `submissions`만 있는 학생도 삭제된다** (FK 위반 없음)
- [ ] **출석·성적·제출물이 있는 학생을 `DELETE`하면 409 `STUDENT_HAS_RECORDS`가 반환된다**
- [ ] **학부모가 연결된 학생을 `DELETE`하면 409가 반환된다**
- [ ] 삭제 확인 다이얼로그에 이름·전화번호·가입일시가 표시된다
- [ ] 퇴원 처리 시 데이터가 삭제되지 않고 `status`만 변경된다
- [ ] **미가입 학생을 퇴원 처리해도 오류가 없고, 미사용 코드가 폐기된다**
- [ ] 퇴원 학생은 로그인이 차단된다
- [ ] 퇴원 학생이 `class-rooms/{classRoomId}/students` 명단에서 사라진다
- [ ] `asOf` 파라미터로 과거 시점 명단을 조회할 수 있다
- [ ] 반을 이름만으로 생성·수정할 수 있다 (학교·학년 입력란이 없음)
- [ ] 활성 반끼리 이름이 겹치면 409가 반환된다
- [ ] **반 생성 응답에 `joinCode`가 포함되고, 혼동 문자(`0 O 1 I L`)가 없다**
- [ ] **`joinCode`가 `signup_codes.code`와 겹치지 않는다** (양쪽 조회 확인)
- [ ] **`joinCode`를 요청 본문으로 지정할 수 없다** (서버 생성만)
- [ ] **코드 재발급 시 이전 코드로 가입하면 400이 반환된다**
- [ ] **`active: false`로 닫은 코드로 가입하면 400이 반환된다**
- [ ] **코드를 닫아도 이미 가입한 학생의 `enrollments`는 그대로다**
- [ ] T-3 반 목록에서 코드와 열림 상태가 보이고 복사할 수 있다
- [ ] **수업·배정 이력이 있는 반을 삭제하면 409가 반환된다**
- [ ] 기록이 없는 반은 삭제된다
- [ ] 같은 반에 같은 날짜 수업을 두 번 만들면 409가 반환된다
- [ ] `bulk` 생성으로 한 학기 수업일이 요일 기준으로 만들어지고, `skipDates`가 제외된다
- [ ] `published_at`이 `null`인 수업은 학생·학부모 API에서 조회되지 않는다
- [ ] YouTube URL 3가지 형식이 모두 파싱되어 `videoId`가 반환된다
- [ ] 수업에 `year`·`month`·`week`가 저장되고, 주차로 목록을 필터링할 수 있다
- [ ] 진급 API의 `dryRun: true`가 데이터를 변경하지 않고 예상 결과만 반환한다
- [ ] 3학년은 자동 퇴원되지 않고 대상 목록만 반환된다
- [ ] `findActiveStudents` 쿼리가 별도 메서드로 분리되어 있다
- [ ] **`findActiveStudents`가 `ORDER BY e.student.name`이다** (`e.student.user.name` 아님)
- [ ] **미가입 학생(`user_id IS NULL`)이 반 명단과 출석부에 이름과 함께 나온다**
- [ ] **미가입 학생만 있는 반에서도 명단이 비어 있지 않다**

---

## 7. 하지 말 것

- **운영 기록이 있는 학생을 물리 삭제하지 마세요.** 출석·제출물·성적·클리닉·시청기록이 하나라도 있으면 409 `STUDENT_HAS_RECORDS`입니다. 실제로 다닌 학생은 언제나 `withdraw`입니다.
- `DELETE /api/teacher/students/{studentId}`는 **반 코드로 들어온 제3자 전용**입니다. 그만둔 학생을 정리하는 용도로 쓰지 마세요. 과거 출석·성적 통계가 사라집니다.
- 삭제 차단 조건을 "`submissions` 행이 있으면"으로 구현하지 마세요. 출제 시 전원의 행이 미리 생겨서 아무도 지울 수 없습니다. `status <> 'NOT_SUBMITTED'`로 판단하세요.
- 영상 파일 업로드 기능을 만들지 마세요. YouTube URL 문자열만 저장합니다.
- 진급을 스케줄러로 자동 실행하지 마세요. 선생님이 확인 후 수동 실행합니다.
- 반에 학교·학년·유형(정규/특강)을 다시 넣지 마세요. 이름 하나로 관리합니다.
- 수업·배정 이력이 있는 반을 삭제하지 마세요. `close`만 허용합니다.
- 엑셀 일괄 업로드를 만들지 마세요. 요구사항에 없고, 200명은 한 번만 등록하면 됩니다. 필요하면 추후 별도 협의 항목입니다.
- 출석·숙제 로직을 이 Phase에서 구현하지 마세요. Phase 4, 5입니다.
