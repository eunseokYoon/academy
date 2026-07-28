# Phase 4 — 출석 · 클리닉

**선행 조건:** Phase 3
**목표:** 선생님이 안 온 학생만 체크해 출석을 확정하고, 학생·학부모가 월별 캘린더로 확인한다.
클리닉을 개설·신청·배정하고 출석까지 처리한다.
**화면:** T-5, T-13, P-2, S-6, S-9

---

## 1. 핵심 설계

### 1-1. 기본값은 출석

선생님은 **안 온 학생만** 지정합니다. 반당 20명이면 보통 결석이 0~2명이므로, 20번 탭할 일을 2번으로 줄입니다.

### 1-2. 확정 전과 후를 구분

`lessons.attendance_status`는 **그 수업의 출석이 확정됐는지**를 나타냅니다.

미래 수업일도 `PENDING`이라, 이 구분이 없으면 아직 오지도 않은 날이 출석으로 집계됩니다.

| `attendance_status` | 캘린더 |
|---|---|
| `PENDING` | 수업일 표시만. 색을 입히지 않음 |
| `CONFIRMED` | 상태별 색상 |

`summary` 집계에는 `CONFIRMED`된 날만 넣습니다.

### 1-3. 확정 시 전원 행 생성

확정 버튼을 누르면 재원생 **전원**의 `attendances` 행을 만듭니다. 예외로 지정된 학생은 해당 상태로, 나머지는 `PRESENT`로 저장합니다.

예외만 저장하고 "행이 없으면 출석"으로 처리하는 방식도 가능하지만, 그러면 캘린더를 그릴 때마다 재원생 명단 × 수업일을 크로스조인해 역산해야 합니다. 200명 × 연 40회면 연 8천 행이고 PostgreSQL에서는 부담이 없습니다.

```
수업일 생성 (PENDING)
  → 선생님이 안 온 학생만 탭 (지각/결석/병결/공결)
  → 출석 확정 버튼 → 전원 attendances 행 생성 → CONFIRMED
  → 학생·학부모 캘린더 반영
```

---

## 2. API

| Method | Endpoint | 역할 | 설명 |
|---|---|---|---|
| GET | `/api/teacher/lessons/{lessonId}/attendance` | T | 출석 입력 화면 데이터 |
| POST | `/api/teacher/lessons/{lessonId}/attendance/confirm` | T | 확정 |
| PATCH | `/api/teacher/attendances/{attendanceId}` | T | 확정 후 개별 정정 |
| GET | `/api/teacher/attendance/pending` | T | 미확정 수업 목록 |
| GET | `/api/student/attendances` | S | 본인 월별 캘린더 |
| GET | `/api/parent/children/{studentId}/attendances` | P | 자녀 월별 캘린더 |

### 클리닉 API

| Method | Endpoint | 역할 | 설명 |
|---|---|---|---|
| GET | `/api/teacher/clinics` | T | 기간별 시간대 + 신청 인원 |
| POST | `/api/teacher/clinics` | T | 시간대 개설 |
| PATCH | `/api/teacher/clinics/{clinicId}` | T | 시간·정원 수정 |
| DELETE | `/api/teacher/clinics/{clinicId}` | T | 시간대 삭제 |
| GET | `/api/teacher/clinics/{clinicId}/reservations` | T | 신청 명단 |
| POST | `/api/teacher/clinics/{clinicId}/students` | T | 학생 배정 (복수) |
| DELETE | `/api/teacher/clinics/{clinicId}/students/{studentId}` | T | 배정 해제 |
| POST | `/api/teacher/clinics/{clinicId}/attendance/confirm` | T | 출석 확정 |
| GET | `/api/teacher/clinic-change-requests` | T | 변경 요청 대기 목록 |
| POST | `/api/teacher/clinic-change-requests/{requestId}/decide` | T | 승인·거절 |
| GET | `/api/student/clinics` | S | 신청 가능 목록 + 본인 신청 현황 |
| POST | `/api/student/clinics/{clinicId}/reservation` | S | 신청 |
| DELETE | `/api/student/clinics/{clinicId}/reservation` | S | 신청 취소 |
| POST | `/api/student/clinic-change-requests` | S | 시간 변경 요청 |
| GET | `/api/parent/children/{studentId}/clinics` | P | 자녀 일정·출석 조회만 |

### GET `/api/teacher/lessons/{lessonId}/attendance`

```json
{
  "success": true,
  "data": {
    "lessonId": 501,
    "classRoomId": 3,
    "classRoomName": "고2 심화반",
    "lessonDate": "2026-05-20",
    "attendanceStatus": "PENDING",
    "students": [
      { "studentId": 88, "name": "서동환", "status": "PRESENT", "memo": null },
      { "studentId": 91, "name": "김하늘", "status": "PRESENT", "memo": null },
      { "studentId": 97, "name": "박서준", "status": "PRESENT", "memo": null }
    ]
  }
}
```

**명단 조회 규칙**

- Phase 3의 `findActiveStudents(classRoomId, lessonDate)`를 사용합니다
- **`lessonDate` 기준 재원생만.** 오늘 기준이 아닙니다. 5월 수업의 출석을 6월에 입력할 때, 5월에 재원 중이었던 학생이 나와야 합니다
- 정렬은 학생 이름 기준

**`attendanceStatus`에 따른 `students` 구성**

| 상태 | 내용 |
|---|---|
| `PENDING` | 전원 `PRESENT`로 초기화 |
| `CONFIRMED` | 저장된 `attendances` 값 |

**임시 저장은 서버에 두지 않습니다.** 확정 전 중간 입력은 프론트 컴포넌트 상태로만 유지하세요.

`attendances`에 미리 쓰면 캘린더의 확정 판정("`attendances` 행이 있고
`lessons.attendance_status = 'CONFIRMED'`")이 무너집니다. 반당 20명에 예외가 0~2명이라
입력이 30초면 끝나서, 서버 저장을 만들 만한 작업량이 아닙니다.

### POST `/api/teacher/lessons/{lessonId}/attendance/confirm`

**안 온 학생만 전달합니다.**

```json
// Request
{
  "exceptions": [
    { "studentId": 91, "status": "ABSENT", "memo": "무단" },
    { "studentId": 97, "status": "SICK",   "memo": "병원 진료, 학부모 사전 연락" }
  ]
}

// Response
{
  "success": true,
  "data": {
    "lessonId": 501,
    "attendanceStatus": "CONFIRMED",
    "confirmedAt": "2026-05-20T22:10:00+09:00",
    "summary": { "present": 18, "late": 0, "absent": 1, "sick": 1, "excused": 0 }
  }
}
```

`exceptions`가 빈 배열이면 전원 출석으로 확정됩니다. **빈 배열을 유효한 요청으로 처리하세요.**

**구현 (하나의 트랜잭션)**

```java
@Transactional
public AttendanceConfirmResponse confirm(Long lessonId, List<AttendanceException> exceptions) {
    Lesson lesson = lessonRepository.findById(lessonId)
        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

    List<Student> students = enrollmentRepository
        .findActiveStudents(lesson.getClassRoom().getId(), lesson.getLessonDate());

    Map<Long, AttendanceException> exceptionMap = exceptions.stream()
        .collect(toMap(AttendanceException::studentId, identity()));

    // 예외 목록에 없는 studentId가 섞여 있으면 400
    validateAllInRoster(exceptionMap.keySet(), students);

    Teacher me = currentTeacher();
    for (Student s : students) {
        AttendanceException ex = exceptionMap.get(s.getId());
        AttendanceStatus status = (ex != null) ? ex.status() : AttendanceStatus.PRESENT;
        String memo = (ex != null) ? ex.memo() : null;

        attendanceRepository.upsert(
            lesson.getId(), lesson.getClassRoom().getId(), s.getId(),
            lesson.getLessonDate(),   // attend_date에 복사
            status, memo, me.getId());
    }

    lesson.confirmAttendance(me);   // status = CONFIRMED, confirmed_at/by 기록
    return buildSummary(lesson, students);
}
```

**`attend_date`에 `lesson.getLessonDate()`를 복사하는 것을 빠뜨리지 마세요.** 캘린더 조회가 이 컬럼에 의존합니다.

**재확정 처리** — `attendances`에 `UNIQUE (student_id, lesson_id)`가 있으므로 `ON CONFLICT DO UPDATE`로 처리합니다. 확정 버튼을 두 번 눌러도 중복 행이 생기지 않고 재확정으로 동작합니다.

```sql
INSERT INTO attendances
  (lesson_id, class_room_id, student_id, attend_date, status, memo, checked_by, checked_at)
VALUES (:lessonId, :classRoomId, :studentId, :attendDate, :status, :memo, :teacherId, now())
ON CONFLICT (student_id, lesson_id) DO UPDATE
SET status = EXCLUDED.status,
    memo = EXCLUDED.memo,
    updated_by = EXCLUDED.checked_by,
    updated_at = now();
```

재확정 시 `checked_by`/`checked_at`은 최초 값을 유지하고 `updated_by`/`updated_at`을 갱신합니다. 최초 확정자와 정정자를 구분하기 위한 것입니다.

**409를 던지지 마세요.** 이미 확정된 수업의 재확정은 정상적인 정정 흐름입니다. `ALREADY_CONFIRMED` 에러 코드는 정의되어 있지만 이 API에서는 사용하지 않습니다.

### PATCH `/api/teacher/attendances/{attendanceId}`

확정 후 개별 학생만 고칠 때 사용합니다.

```json
{ "status": "SICK", "memo": "학부모 사후 연락, 병원 진료 확인" }
```

`updated_by`, `updated_at`을 반드시 기록하세요. 출석은 학부모가 매일 보는 데이터라 **정정 요청이 반드시 들어옵니다.** "어제 병원 간다고 미리 말씀드렸는데 결석이에요" 같은 연락에 대응하려면 누가 언제 무엇을 바꿨는지 남아야 합니다.

### GET `/api/teacher/attendance/pending`

미확정 수업 목록입니다. T-1 대시보드와 T-5 화면에서 사용합니다.

```json
{
  "success": true,
  "data": [
    { "lessonId": 501, "classRoomName": "고2 심화반", "lessonDate": "2026-05-20", "studentCount": 20 }
  ]
}
```

`lesson_date <= 오늘`이고 `attendance_status = 'PENDING'`인 수업만 반환합니다. 미래 수업은 아직 확정할 수 없으므로 제외합니다.

### GET `/api/parent/children/{studentId}/attendances?year=2026&month=5`

`GET /api/student/attendances?year=2026&month=5`도 응답 형식이 동일합니다 (학생은 본인 것).

```json
{
  "success": true,
  "data": {
    "year": 2026, "month": 5,
    "summary": { "present": 11, "late": 1, "absent": 1, "sick": 0, "excused": 0 },
    "homeworkCompletionRate": 94,
    "days": [
      { "date": "2026-05-04", "status": "PRESENT", "homeworkRate": 100 },
      { "date": "2026-05-13", "status": "LATE",    "homeworkRate": 60  },
      { "date": "2026-05-20", "status": "ABSENT",  "homeworkRate": 0   },
      { "date": "2026-05-27", "status": "PENDING", "homeworkRate": null }
    ]
  }
}
```

**서비스 구현 첫 줄은 권한 검증입니다.**

```java
Student student = studentAccessGuard.requireAccessible(studentId);
```

**`status: "PENDING"`의 의미** — 수업일은 있지만 선생님이 아직 확정하지 않은 날입니다. `attendances` 행이 없고 `lessons.attendance_status = 'PENDING'`인 경우입니다. 프론트는 회색으로 표시하고 절대 출석으로 취급하지 않습니다.

`summary`는 `CONFIRMED`된 날만 집계합니다. 미확정 날짜를 출석 수에 포함하면 안 됩니다.

**`days` 배열에는 수업이 있는 날만 담습니다.** 수업 없는 날은 아예 넣지 마세요. 프론트가 캘린더 격자를 그리고, 배열에 없는 날짜는 빈 칸으로 둡니다.

### homeworkRate 계산

`homeworkRate`는 해당 수업일에 출제된 숙제의 제출 완료율입니다.

```sql
-- 날짜 칸 하나당 값 하나. GROUP BY를 걸면 숙제별로 행이 쪼개져 응답 형태와 맞지 않는다
-- 숙제가 없는 날은 NULL을 반환한다. coalesce로 0을 만들지 말 것
SELECT count(*) FILTER (WHERE s.status IN ('SUBMITTED','CHECKED')) * 100
         / nullif(count(*), 0)
FROM homeworks h
JOIN submissions s ON s.homework_id = h.id AND s.student_id = :studentId
WHERE h.lesson_id = :lessonId;
```

한 수업일에 숙제가 2개 이상 걸릴 수 있으므로 **숙제 전체를 묶어 하나의 비율**로 냅니다.
`06_phase5_homework.md` 7절의 쿼리와 같아야 합니다.

**`coalesce(..., 0)`을 붙이지 마세요.** 그날 숙제가 없으면 `count(*) = 0`이라
`nullif`가 NULL을 만들고 나눗셈도 NULL이 되는데, `coalesce`가 그걸 **0으로 바꿔 버립니다.**

`homeworkRate: 0`은 프론트에서 빨간 띠입니다(4-2절). 숙제가 없던 날이
학부모 눈에 **"우리 애가 하나도 안 냈다"**로 보입니다. 문의가 들어오고, 확인해 보면
그날은 숙제가 없었던 날입니다.

| 상황 | `homeworkRate` | 캘린더 |
|---|---|---|
| 숙제 2개 중 2개 제출 | `100` | 초록 띠 |
| 숙제 2개 중 0개 제출 | `0` | 빨간 띠 |
| **그날 숙제 없음** | **`null`** | **띠 없음** |
| Phase 5 완료 전 | `null` | 띠 없음 |

프론트는 `null`이면 띠를 그리지 않습니다. `0`과 `null`을 반드시 구분하세요.

**Phase 5가 완료되기 전에는 `homeworkRate`를 `null`로 반환하세요.** 캘린더 하단 색띠는 Phase 5 이후에 채워집니다. Phase 4에서 억지로 구현하려 하지 마세요.

`homeworkCompletionRate`(월 전체)도 동일하게 Phase 5 이후에 계산합니다.

---

## 3. 클리닉

정규 수업과 별개인 **보충 수업**입니다. **정해진 시간대에 여러 학생이 함께** 참여합니다.

```
선생님이 시간대 개설 (T-13)
  ├─ 학생이 직접 신청 (S-9)
  └─ 선생님이 배정 (T-13)
  → 학생이 변경 요청 (사유 선택 필수) → 선생님 승인·거절 (T-13)
  → 당일 출석 확정 (T-13, T-5와 같은 방식)
  → 학부모는 일정·출석만 조회 (P-2)
```

### 3-1. 동시 신청 경합

**`clinics`에 `student_id`가 없습니다.** 한 시간에 여러 명이라 `clinic_reservations`가 연결을 담습니다.

정원 체크는 **클리닉 행을 잠근 뒤** 하세요. 세 문장이 하나의 트랜잭션입니다.

```sql
-- 1) 같은 클리닉 신청을 직렬화한다
SELECT capacity FROM clinics WHERE id = :clinicId FOR UPDATE;

-- 2) 락을 쥔 상태에서 센다
SELECT count(*) FROM clinic_reservations
WHERE clinic_id = :clinicId AND status = 'RESERVED';

-- 3) capacity가 NULL이거나 count < capacity면 삽입, 아니면 409 CLINIC_CAPACITY_EXCEEDED
INSERT INTO clinic_reservations (clinic_id, student_id) VALUES (:clinicId, :studentId);
```

> ⚠️ **"조건부 삽입 한 방"은 해결책이 아닙니다.**
>
> ```sql
> INSERT INTO clinic_reservations (clinic_id, student_id)
> SELECT :clinicId, :studentId
> WHERE (SELECT count(*) FROM clinic_reservations ...) < (SELECT capacity ...);  -- ❌
> ```
>
> PostgreSQL 기본 격리수준은 READ COMMITTED이고 서브쿼리의 `count(*)`는 **세는 행에
> 락을 걸지 않습니다.** 두 트랜잭션이 동시에 `count = 5`를 읽으면 둘 다 통과해
> 정원 6에 7행이 들어갑니다. "세어 보고 넣기"를 SQL 한 문장에 넣었을 뿐입니다.
>
> 저부하에서는 통과하다가 신청이 몰리는 순간 깨져서, 테스트로 잡기도 어렵습니다.

`FOR UPDATE`는 클리닉 단위로만 직렬화하므로 서로 다른 클리닉은 병렬입니다.
200명 규모에서 대기 비용은 사실상 0입니다.

**같은 메서드를 세 곳에서 쓰세요** — 학생 신청(S-9), 선생님 일괄 배정(T-13),
변경 요청 승인 시 목표 클리닉 재확인. 세 곳 모두 같은 경합에 노출됩니다.

중복 신청은 `UNIQUE (clinic_id, student_id) WHERE status = 'RESERVED'` 부분 인덱스가 막습니다.
취소했다가 다시 신청하는 것은 허용됩니다. **정원 초과(`CLINIC_CAPACITY_EXCEEDED`)와
중복 신청(`DUPLICATE_RESOURCE`)은 다른 에러 코드입니다.** S-9 화면이 "정원이 찼습니다"와
"이미 신청하셨습니다"를 다르게 보여줘야 합니다.

### 3-2. 신청 경로 두 가지

| 경로 | `assigned_by` | 화면 |
|---|---|---|
| 학생 본인 신청 | `NULL` | S-9 |
| 선생님 배정 | 배정한 선생님 id | T-13 |

명단 응답에 `assignedByTeacher`로 내려주세요. "왜 여기 있냐"는 문의에 답하려면 구분이 필요합니다.

**배정 시 정원을 넘으면 넘는 만큼만 넣지 말고 409로 전체를 거절하세요.** 일부만 들어가면
선생님이 누가 빠졌는지 모릅니다.

### 3-3. 변경 요청 승인

**학생이 직접 시간을 옮기지 못합니다.** 요청하고 선생님이 승인합니다. 요청 시 **사유 선택은 필수**입니다.

승인은 한 트랜잭션입니다.

1. 기존 `clinic_reservations.status = 'MOVED'`
2. 목표 클리닉에 새 `RESERVED` 행 생성
3. `clinic_change_requests.status = 'APPROVED'`, `decided_by`·`decided_at` 기록

**2번에서 목표 클리닉 정원을 다시 확인하세요.** 요청 시점에는 자리가 있었어도 승인 시점에는
찼을 수 있습니다. 초과면 409를 반환하고 요청은 `PENDING`으로 남깁니다.

`target_clinic_id`가 `NULL`이면 취소 요청이라 기존 예약만 `CANCELED`로 바꿉니다.
같은 예약에 `PENDING` 요청이 이미 있으면 409입니다. 두 개가 동시에 승인되면 예약이 꼬입니다.

> ⚠️ **변경 사유 옵션 목록이 미확정입니다.** `reason_code`는 `String`으로 받아 저장만 하세요.
> CHECK 제약도 enum도 만들지 말고, 값을 임의로 지어내지도 마세요.

### 3-4. 클리닉 출석

**`attendances` 테이블을 쓰지 않습니다.** `attendances.class_room_id`가 `NOT NULL`인데 클리닉은 반이 없습니다.
`clinic_reservations.attend_status`에 기록하세요.

확정 방식은 **T-5와 동일**합니다. 안 온 학생만 보내고, 대상은 `status = 'RESERVED'`인 예약 전원,
나머지는 `PRESENT`로 채웁니다.

---

## 4. 프론트엔드

### 4-1. T-5 출석 확정

```
1) 반 선택 (또는 대시보드에서 미확정 수업 클릭)
2) 수업일 선택
3) 학생 목록 — 전원 초록색 "출석" 상태로 표시
4) 안 온 학생 탭 → 상태 선택 팝업 (지각 / 결석 / 병결 / 공결)
5) 선택 시 해당 학생만 색 변경, 메모 입력 가능
6) 하단 고정 버튼 "출석 확정 (결석 2명)"
7) 확인 다이얼로그 → 확정
```

**상태를 순환시키지 마세요.** 탭할 때마다 출석 → 지각 → 결석 → 병결로 순환하는 방식은 한 번 지나치면 세 번 더 눌러야 합니다. 탭하면 4개 선택지가 작게 뜨는 방식이 오답이 적습니다.

**확정 버튼에 예외 인원 수를 표시하세요.** "출석 확정" 대신 "출석 확정 (결석 2명)"으로 두면 실수를 알아챕니다. 예외가 0명이면 "전원 출석으로 확정"으로 문구를 바꾸세요.

이미 확정된 수업을 다시 열면 저장된 값이 표시되고, 버튼 문구는 "수정 저장"으로 바뀝니다.

### 4-2. P-2 / S-6 캘린더

월별 격자를 그리고 각 날짜 칸에 두 가지를 표시합니다.

| 요소 | 데이터 |
|---|---|
| 칸 배경색 | `status` |
| 칸 하단 색띠 | `homeworkRate` (0% 빨강 → 100% 초록) |

색상 매핑:

| status | 색상 |
|---|---|
| `PRESENT` | 연초록 |
| `LATE` | 연노랑 |
| `ABSENT` | 연빨강 |
| `SICK` | 연파랑 |
| `EXCUSED` | 연회색 |
| `PENDING` | 회색 + "미확인" 표기 |

**색상만으로 구분하지 마세요.** 색약 사용자를 위해 칸 안에 텍스트나 기호를 함께 넣거나, 하단에 범례를 반드시 두세요.

상단에 월 요약 수치(출석 11 / 지각 1 / 결석 1 / 병·공결 0)와 숙제 완료율을 표시합니다. 이전 달·다음 달 이동 버튼을 두세요.

### 4-3. 클리닉 화면 (T-13, S-9, P-2)

**S-9 학생 신청**

```
[주간 시간표]
6/4 (목) 17:00~18:00   4/6명   [신청]
6/5 (금) 17:00~18:00   6/6명   마감
6/6 (토) 19:00~20:00   2/6명   [신청]

[내 클리닉]
6/4 (목) 17:00  신청 완료   [취소] [시간 변경 요청]
```

- **다른 학생 이름을 보여주지 마세요.** 인원 수(`4/6`)만입니다.
- 마감(`full: true`)은 서버가 계산한 값을 그대로 쓰세요. `capacity`가 `null`이면 항상 여유 있음입니다.
- 시간 변경 요청은 **사유 선택이 필수**라, 버튼을 누르면 사유 선택 다이얼로그가 먼저 떠야 합니다.

**T-13 선생님 관리**

```
[변경 요청]  2건 대기
  서동환  6/4 17:00 → 6/6 19:00   사유: ...   [승인] [거절]

[6/4 (목) 17:00~18:00]  4/6명
  서동환(신청)  김하늘(배정)  박서준(신청)  이수민(배정)
  [학생 추가 배정]  [출석 확정]
```

- 신청과 배정을 **뱃지로 구분**하세요. `assignedByTeacher`가 그 값입니다.
- 출석 확정은 T-5와 같은 UI를 재사용합니다. 안 온 학생만 탭하고 하단 버튼으로 확정합니다.

**P-2 학부모 캘린더**

수업일과 클리닉을 **한 캘린더에 다른 표시로** 얹습니다. 학부모는 조회만 하므로
신청·취소·변경 버튼을 두지 마세요.

---

## 5. 완료 조건 (DoD)

- [ ] 출석 입력 화면에서 전원이 `PRESENT`로 초기 표시된다
- [ ] 예외 2명만 담아 confirm을 호출하면 재원생 전원의 `attendances` 행이 생성된다
- [ ] 생성된 행의 `attend_date`가 `lessons.lesson_date`와 일치한다
- [ ] `exceptions`가 빈 배열이어도 전원 출석으로 확정된다
- [ ] confirm을 두 번 호출해도 `attendances` 행이 중복되지 않는다 (`ON CONFLICT` 동작 확인)
- [ ] 재확정 시 `checked_by`는 유지되고 `updated_by`/`updated_at`이 갱신된다
- [ ] `lessons.attendance_status`가 `CONFIRMED`로 변경되고 `confirmed_at`/`confirmed_by`가 기록된다
- [ ] **명단이 `lesson_date` 기준 재원생으로 조회된다** (수업일 이후 퇴원한 학생이 포함되고, 수업일 이후 입반한 학생은 제외됨)
- [ ] 명단에 없는 `studentId`를 `exceptions`에 넣으면 400이 반환된다
- [ ] 미확정 수업일이 학부모 캘린더에서 `PENDING`으로 내려온다
- [ ] `PENDING` 날짜가 `summary`의 출석 수에 포함되지 않는다
- [ ] 프론트에서 `PENDING`이 회색으로 표시되고 "미확인" 문구가 보인다
- [ ] **학부모 A가 학부모 B의 자녀 캘린더를 조회하면 403이 반환된다**
- [ ] 개별 정정(PATCH) 후 `updated_by`/`updated_at`이 기록된다
- [ ] `GET /teacher/attendance/pending`이 과거·오늘의 미확정 수업만 반환한다 (미래 제외)
- [ ] 캘린더 범례가 표시되고 색상 외 구분 수단이 있다
- [ ] `homeworkRate`가 `null`로 반환되어도 프론트가 오류 없이 렌더링된다
- [ ] **숙제가 없던 수업일의 `homeworkRate`가 `0`이 아니라 `null`이다** (빨간 띠가 뜨지 않음)

### 클리닉

- [ ] 한 클리닉에 학생 여러 명이 신청된다 (`clinic_reservations` 행이 여러 개)
- [ ] **정원이 찬 클리닉에 신청하면 409 `CLINIC_CAPACITY_EXCEEDED`가 반환된다**
- [ ] **동시에 정원 마지막 자리를 신청해도 정원을 넘지 않는다** (`FOR UPDATE` 확인.
      동시 요청 N개를 띄워 `count(*) <= capacity`를 검증할 것)
- [ ] **정원 초과와 중복 신청이 서로 다른 에러 코드로 반환된다** (S-9 문구가 갈림)
- [ ] 같은 클리닉에 두 번 신청하면 409 `DUPLICATE_RESOURCE`가 반환된다
- [ ] 취소 후 같은 클리닉에 다시 신청할 수 있다
- [ ] 선생님 배정 시 `assigned_by`가 기록되고, 명단에서 신청/배정이 구분된다
- [ ] 배정이 정원을 넘으면 일부만 넣지 않고 전체가 409로 거절된다
- [ ] 학생 목록 응답에 **다른 학생 이름이 포함되지 않는다** (인원 수만)
- [ ] 변경 요청 시 `reason_code` 없이 보내면 400이 반환된다
- [ ] 변경 승인 시 기존 예약이 `MOVED`, 목표 클리닉에 새 `RESERVED` 행이 생긴다
- [ ] **승인 시점에 목표 클리닉이 꽉 차 있으면 409가 나고 요청이 `PENDING`으로 남는다**
- [ ] 같은 예약에 `PENDING` 요청이 이미 있으면 409가 반환된다
- [ ] 클리닉 출석이 `clinic_reservations.attend_status`에 기록되고 `attendances`에는 행이 생기지 않는다
- [ ] 학부모가 클리닉 신청·취소·변경 API를 호출하면 403이 반환된다

### 테스트 시나리오

```java
@Test
void 예외가_빈_배열이면_전원_출석으로_확정된다() { }

@Test
void confirm을_두_번_호출해도_행이_중복되지_않는다() { }

@Test
void 수업일_이후_퇴원한_학생도_해당_수업_명단에_포함된다() { }

@Test
void 미확정_수업일은_캘린더에서_PENDING으로_내려온다() { }

@Test
void 미확정_날짜는_출석_집계에_포함되지_않는다() { }
```

---

## 6. 하지 말 것

- QR 코드, NFC, 출결 단말기 연동을 구현하지 마세요. 선생님 수동 입력만입니다.
- 학생이 스스로 출석을 체크하는 기능을 만들지 마세요.
- 결석 시 자동 알림(SMS·알림톡)을 보내지 마세요. 발송 수단은 1차 범위 밖입니다.
- 학부모가 결석 사유를 사전 등록하는 기능을 만들지 마세요. 2차 항목입니다. 지금은 선생님이 `memo`에 적습니다.
- 지각 판정을 시간으로 자동화하지 마세요. 선생님 판단입니다.
- 확정 전 중간 상태를 서버에 저장하는 API를 만들지 마세요. 프론트 로컬 상태로만 유지합니다.
- **클리닉 출석을 `attendances` 테이블에 넣지 마세요.** `class_room_id`가 `NOT NULL`이라 안 맞습니다.
- **학생이 클리닉 시간을 직접 바꾸게 하지 마세요.** 요청 → 선생님 승인만 있습니다.
- **클리닉 명단에 다른 학생 이름을 노출하지 마세요.** 학생 화면에는 인원 수만입니다.
- 정원 체크를 "세어 보고 넣기"로 구현하지 마세요. 동시 신청 시 정원을 넘깁니다. **조건부 삽입 한 방도 같은 버그입니다.** `SELECT ... FOR UPDATE`로 클리닉 행을 먼저 잠그세요.
- **`homeworkRate`에 `coalesce(..., 0)`을 쓰지 마세요.** 숙제 없던 날이 학부모에게 0%(빨강)로 보입니다. 숙제가 없으면 `null`입니다.
- **`reason_code` 옵션을 임의로 만들지 마세요.** 목록이 미확정입니다. CHECK 제약도 enum도 금지입니다.
- 클리닉 자동 배정·추천을 구현하지 마세요. 선생님이 직접 배정하거나 학생이 신청합니다.
- 학부모에게 클리닉 신청·변경 권한을 주지 마세요. 조회만입니다.
- `homeworkRate`를 Phase 4에서 구현하려 하지 마세요. Phase 5 완료 후입니다.
- 출석 데이터를 물리 삭제하는 API를 만들지 마세요. 정정만 있습니다.
