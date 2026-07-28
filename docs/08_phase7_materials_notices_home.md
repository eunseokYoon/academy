# Phase 7 — 자료실 · 공지 · 홈 · 대시보드

**선행 조건:** Phase 4, 5, 6
**목표:** 남은 기능을 채우고, 앞 단계 데이터를 조합해 각 역할의 첫 화면을 완성한다.
**화면:** P-1, S-1, S-8, T-1, T-9, T-10

---

## 1. 자료실 (S-8, T-9)

**자료실은 학생 전용입니다.** 학부모에게 노출하지 않습니다.

### 1-1. 공개 범위 규칙

`materials`의 `school_id`, `grade`, `class_room_id`, `visibility` 조합으로 결정됩니다.

| visibility | school_id / grade | 노출 대상 |
|---|---|---|
| `PUBLIC` | `null` | 로그인한 전체 사용자 (학교·학년 무관) |
| `CLASS` | `null` | 로그인한 전체 재원생 |
| `CLASS` | 값 있음 | 해당 학교·학년 재원생만 |
| `CLASS` | + `class_room_id` | 해당 반 재원생만 |

**`school_id + grade`가 내신형 구조의 핵심입니다.** A고 2학년 학생은 자기 학교 기출만 보이고 B고 자료는 쿼리에서 아예 제외됩니다.

**비로그인 공개는 없습니다.** `/api/student/materials/**`는 로그인한 학생 전용입니다.
`PUBLIC`은 "학교·학년 제한 없음"이지 "누구나"가 아닙니다.

### 1-2. API

| Method | Endpoint | 역할 |
|---|---|---|
| GET | `/api/student/materials?category&page&size` | S |
| GET | `/api/student/materials/{materialId}/download-url` | S |
| GET | `/api/teacher/materials?year&month&week&category` | T |
| POST | `/api/teacher/materials/upload-url` | T |
| POST | `/api/teacher/materials` | T |
| PATCH | `/api/teacher/materials/{materialId}` | T |
| DELETE | `/api/teacher/materials/{materialId}` | T |

### GET `/api/student/materials?category=PAST_EXAM`

**`studentId` 파라미터가 없습니다.** 본인 것만 보므로 토큰에서 학생을 찾습니다.

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "materialId": 41,
        "title": "5월 24일 수업자료",
        "category": "LESSON",
        "fileName": "0524_lesson.pdf",
        "bytes": 7130316,
        "year": 2026, "month": 5, "week": 4,
        "createdAt": "2026-05-13T10:00:00+09:00"
      }
    ],
    "page": 0, "size": 20, "totalElements": 12, "totalPages": 1
  }
}
```

**정렬은 최신순**(`created_at DESC`)입니다. 학생이 "이번 주 자료"를 맨 위에서 찾습니다.

**조회 쿼리**

**세 차원을 각각 독립으로 봅니다.** 채워져 있는 차원만 검사하고, 비어 있는 차원은 통과입니다.

```sql
SELECT m.* FROM materials m
WHERE (m.visibility = 'PUBLIC'
    OR ( (m.school_id     IS NULL OR m.school_id     = :schoolId)
     AND (m.grade         IS NULL OR m.grade         = :grade)
     AND (m.class_room_id IS NULL OR m.class_room_id IN (:myClassRoomIds)) ))
  AND (:category IS NULL OR m.category = :category)
ORDER BY m.created_at DESC;
```

`:myClassRoomIds`는 해당 학생의 현재 활성 `enrollments`에서 가져옵니다.
**비어 있을 수 있습니다** (배정 전 학생). 빈 리스트를 `IN`에 그대로 넘기면 SQL이 깨지므로,
`IN (-1)` 같은 더미를 넣거나 조건을 통째로 빼는 처리를 하세요.

응답에 `s3Key`를 포함하지 마세요. 내부 저장 경로가 노출됩니다.

> ⚠️ **분기를 `school_id` 기준으로 묶지 마세요.** 아래처럼 쓰면 반 전용 자료가 사라집니다.
>
> ```sql
> OR (m.school_id = :schoolId AND (m.grade IS NULL OR m.grade = :grade)
>     AND (m.class_room_id IS NULL OR m.class_room_id IN (:myClassRoomIds)))  -- ❌
> ```
>
> `visibility = 'CLASS'`, `school_id = NULL`, `class_room_id = 3`인 자료는
> `m.school_id = :schoolId`에서 `NULL = 1` → NULL이 되어 탈락합니다. **아무에게도 안 보입니다.**
>
> 그리고 이건 흔한 입력입니다. **반이 이미 학교·학년을 갖고 있어서**(`02_phase1_db_schema.md` 2-2)
> 선생님이 "고2 심화반 전용 자료"를 올릴 때 학교를 따로 채울 이유가 없습니다.
> 위 표의 4번째 줄(`CLASS` + `class_room_id` → 해당 반 재원생만)이 그대로 죽습니다.

### GET `/api/student/materials/{materialId}/download-url`

```json
{
  "success": true,
  "data": {
    "downloadUrl": "https://...",
    "fileName": "0524_lesson.pdf",
    "expiresIn": 300
  }
}
```

**발급 전 반드시 권한을 재확인하세요.** 목록에서 걸러졌다고 안심하면 안 됩니다. `materialId`를 직접 넣어 호출하는 것을 막아야 합니다.

```java
public DownloadUrlResponse getDownloadUrl(Long materialId) {
    Student student = studentAccessGuard.requireSelf();   // 학생 본인
    Material material = materialRepository.findById(materialId)
        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

    if (!materialVisibilityChecker.isVisibleTo(material, student)) {
        throw new BusinessException(ErrorCode.ROLE_NOT_ALLOWED);   // 403
    }
    return s3Service.presignDownload(material);
}
```

presigned URL 유효기간은 5분입니다. `Content-Disposition: attachment; filename="..."`을 함께 서명해 원본 파일명으로 저장되게 하세요.

### POST `/api/teacher/materials`

업로드는 Phase 5의 사진과 동일한 presigned 방식입니다.

```json
{
  "title": "A고 2학년 1학기 중간 기출",
  "category": "PAST_EXAM",
  "s3Key": "materials/2026/05/{uuid}.pdf",
  "fileName": "A고_2학년_중간기출.pdf",
  "bytes": 3210544,
  "schoolId": 1,
  "grade": 2,
  "classRoomId": null,
  "visibility": "CLASS",
  "year": 2026, "month": 5, "week": 4
}
```

**`year`·`month`·`week`는 필수입니다.** T-9 화면이 주차를 먼저 고르고 그 주의 자료를 올리는 구조라,
선택한 주차를 그대로 실어 보냅니다. 서버가 날짜에서 계산하지 마세요.

`category`: `LESSON` / `TEXTBOOK` / `PAST_EXAM` / `ETC`

허용 확장자: `pdf`, `hwp`, `hwpx`, `docx`, `xlsx`, `pptx`, `zip`, `jpg`, `png`. 최대 50MB.

**실행 파일 확장자를 허용하지 마세요** (`exe`, `sh`, `bat`, `js`, `html`). HTML은 저장 후 서빙 시 XSS 경로가 됩니다.

---

## 2. 공지 (T-10)

### 2-1. API

| Method | Endpoint | 역할 |
|---|---|---|
| GET | `/api/notices?studentId&page&size` | S, P |
| GET | `/api/notices/{noticeId}` | S, P |
| GET | `/api/teacher/notices` | T |
| POST | `/api/teacher/notices` | T |
| PATCH | `/api/teacher/notices/{noticeId}` | T |
| POST | `/api/teacher/notices/{noticeId}/publish` | T |
| DELETE | `/api/teacher/notices/{noticeId}` | T |

### POST `/api/teacher/notices`

```json
{
  "title": "[SUMMER] 고1 영어 구문독해 선행 안내",
  "content": "...",
  "scope": "GRADE",
  "schoolId": 1,
  "grade": 1,
  "classRoomId": null,
  "pinned": false
}
```

`scope`: `ALL` / `SCHOOL` / `GRADE` / `CLASS`

`scope`에 따라 필수 필드가 달라집니다.

| scope | 필수 |
|---|---|
| `ALL` | 없음 |
| `SCHOOL` | `schoolId` |
| `GRADE` | `schoolId`, `grade` |
| `CLASS` | `classRoomId` |

불일치하면 400 `VALIDATION_FAILED`. 서버에서 검증하세요.

`published_at`이 `null`이면 초안입니다. 학생·학부모 조회에는 `published_at IS NOT NULL` 조건을 넣습니다.

### GET `/api/notices?studentId=88`

**`studentId`를 받으므로 서비스 첫 줄은 권한 검증입니다.**

```java
@Transactional(readOnly = true)
public PageResponse<NoticeSummaryResponse> list(Long studentId, Pageable pageable) {
    Student student = studentAccessGuard.requireAccessible(studentId);   // 반드시 첫 줄
    ...
}
```

`/api/notices/**`는 역할별 접두사가 아니라 공통 경로라 `SecurityConfig`의 역할 검사가
걸리지 않습니다(`03_phase2_auth.md` 2절). **경로가 공통이라고 검증을 건너뛰지 마세요.**
`studentId`를 받는 API에 예외는 없습니다.

공지 자체는 개인 데이터가 아니지만, 검증이 없으면 남의 자녀 `studentId`로 그 학생의
학교·학년·반 범위 공지를 조회할 수 있습니다. 그리고 이 규칙에 한 곳이라도 구멍을 내면
다음 사람이 따라 합니다.

`studentId`가 없으면(학생 본인 호출) `studentAccessGuard.requireSelf()`를 씁니다.

정렬: `pinned DESC`, `published_at DESC`. 고정 공지가 항상 위에 옵니다.

```json
{
  "success": true,
  "data": {
    "items": [
      { "noticeId": 15, "title": "[SUMMER] 고1 영어 구문독해 선행 안내",
        "pinned": false, "publishedAt": "2026-05-22T09:00:00+09:00" }
    ],
    "page": 0, "size": 20, "totalElements": 6, "totalPages": 1
  }
}
```

**공지 내용은 사용자가 입력한 텍스트입니다.** 프론트에서 HTML로 렌더링하지 말고 일반 텍스트로 출력하세요. 마크다운이나 리치 에디터를 붙이면 XSS 처리가 필요해집니다. 줄바꿈만 유지하는 정도로 충분합니다.

읽음 표시(`notice_reads`)는 1차 범위 밖입니다. 만들지 마세요.

---

## 3. 홈 화면

### 3-1. GET `/api/parent/children/{studentId}/home` (P-1)

여러 도메인을 조합하는 화면입니다. **하나의 API로 묶어 내려주세요.** 프론트에서 6개 API를 병렬 호출하면 로딩이 지저분해집니다.

```json
{
  "success": true,
  "data": {
    "student": {
      "id": 88, "name": "서동환",
      "schoolName": "A고등학교", "grade": 2,
      "classRooms": ["고2 심화반", "썸머 집중반"]
    },
    "nextExam": {
      "examType": "FINAL", "startDate": "2026-06-25", "dDay": 27
    },
    "nextLessonDate": "2026-06-03",
    "notices": {
      "totalCount": 6,
      "recent": [
        { "noticeId": 15, "title": "[SUMMER] 고1 영어 구문독해 선행 안내",
          "publishedAt": "2026-05-22T09:00:00+09:00" }
      ]
    },
    "pendingHomeworkCount": 1,
    "nextClinic": {
      "clinicId": 41, "clinicDate": "2026-06-04", "startTime": "17:00", "dDay": 6
    },
    "thisMonthAttendance": {
      "present": 11, "late": 1, "absent": 1, "sick": 0, "excused": 0
    }
  }
}
```

각 값이 `null`일 수 있습니다. 시험 일정이 등록 안 됐으면 `nextExam: null`입니다. 프론트는 해당 카드를 숨깁니다.

**`nextLessonDate`는 날짜만 내려줍니다.** 학부모에게는 "다음 수업이 언제인지"까지가 전부이고,
수업 제목·내용·영상은 노출하지 않습니다.

**첫 줄에 `studentAccessGuard.requireAccessible(studentId)`를 호출하세요.**

### 3-2. GET `/api/student/home` (S-1)

```json
{
  "success": true,
  "data": {
    "student": { "name": "서동환", "schoolName": "A고등학교", "grade": 2 },
    "nextLesson": { "lessonDate": "2026-06-03", "dDay": 5,
                    "classRoomName": "고2 심화반" },
    "nextExam": { "examType": "FINAL", "startDate": "2026-06-25", "dDay": 27 },
    "currentHomeworks": [
      { "homeworkId": 720, "title": "주간지 전 범위 풀기",
        "dueAt": "2026-05-21T20:00:00+09:00",
        "status": "NOT_SUBMITTED", "remainingMinutes": 137 }
    ],
    "unreadFeedbackCount": 2,
    "noticeCount": 6
  }
}
```

**`currentHomeworks`를 가장 크게 보여주세요.** 학생이 앱을 여는 이유는 "뭘 해야 하는지" 확인하기 위해서입니다. 미제출이면서 마감 임박한 것을 최상단에 배치합니다.

마감이 지난 미제출 숙제도 포함해야 합니다. 사라지면 학생이 잊습니다.

### 3-3. GET `/api/teacher/dashboard` (T-1)

```json
{
  "success": true,
  "data": {
    "today": {
      "date": "2026-05-20",
      "lessons": [
        { "lessonId": 501, "classRoomName": "고2 심화반",
          "startTime": "19:00", "studentCount": 20,
          "attendanceStatus": "PENDING", "contentWritten": false }
      ]
    },
    "todo": {
      "pendingAttendanceCount": 3,
      "awaitingCheckCount": 12,
      "unwrittenLessonCount": 2,
      "unsignedStudentCount": 12,
      "unlinkedParentCount": 7,
      "pendingClinicRequestCount": 2,
      "recentSignupCount": 4,
      "openJoinCodeCount": 2
    },
    "stats": {
      "totalStudents": 197,
      "activeClassRooms": 9
    }
  }
}
```

**`todo`가 이 화면의 존재 이유입니다.** 선생님이 무엇을 안 했는지 한눈에 보여주고, 각 항목을 탭하면 해당 화면으로 이동합니다.

| 항목 | 의미 | 이동 |
|---|---|---|
| `pendingAttendanceCount` | 출석 미확정 수업 | T-5 |
| `awaitingCheckCount` | 확인 대기 제출물 | T-7 |
| `unwrittenLessonCount` | 내용 미작성 수업 | T-4 |
| `unsignedStudentCount` | **회원가입 안 한 학생** (`user_id IS NULL`) | T-2 |
| `unlinkedParentCount` | 학부모 미가입·미연결 학생 | T-2 |
| `pendingClinicRequestCount` | **클리닉 변경 요청 대기** | T-13 |
| `recentSignupCount` | **최근 7일 신규 가입** (`students.created_at`) | T-2 (`sort=recent`) |
| `openJoinCodeCount` | **가입 코드가 열려 있는 반** | T-3 |

**뒤의 두 개가 반 코드 가입의 안전장치입니다.**

반 코드에는 전화번호 대조가 없어서 코드를 아는 사람은 누구나 가입합니다. 막을 수단이 없으므로
**가입 후 발견해서 지웁니다**(`DELETE /api/teacher/students/{studentId}`).

| 항목 | 선생님이 할 일 |
|---|---|
| `recentSignupCount` | 탭 → 최근 가입 순 목록. 모르는 이름이 있으면 삭제 |
| `openJoinCodeCount` | 등록 기간이 끝났는데 0이 아니면 T-3에서 닫기 |

`recentSignupCount`는 **0이어도 숨기지 마세요.** 다른 `todo` 항목과 달리 "확인했다"는 것이
의미 있는 정보이고, 등록 기간에는 매일 봐야 합니다.

`openJoinCodeCount`가 0보다 크면 **경고색으로 표시하세요.** 학기 중 내내 코드를 열어두는 것이
가장 흔한 사고 경로입니다.

**`unsignedStudentCount`와 `unlinkedParentCount`가 특히 중요합니다.** 가입하지 않으면 선생님이
입력한 출석·숙제·성적이 아무에게도 전달되지 않습니다. 오픈 초기에는 이 두 숫자를 0으로 만드는 것이
가장 중요한 운영 업무이고, 남은 사람에게 코드를 다시 알려주는 것으로 해결합니다.

**쿼리 최적화** — 이 API는 매번 호출되므로 count 쿼리 6개가 나갑니다. 200명 규모에서는 문제없지만, 각 쿼리에 인덱스가 걸려 있는지 확인하세요. 캐시는 넣지 마세요.

---

## 4. 프론트엔드

### 4-1. P-1 포털 홈 구조

```
[헤더] 학원명                          [내 정보]

[자녀 카드]
서○환 (고2 심화반 · 썸머 집중반) 학생, 환영합니다
010-****-****                          [기말 D-60]

자녀 선택  [서○환 (고2 심화반) ▾]

[공지 배너] 학원 공지·안내 (6)
  [공지] [SUMMER] 고1 영어 구문독해 선행...  05/22
  + 4개 더 보기 ▾

[메뉴 그리드 2열]
  수업·클리닉 일정     테스트 결과
  숙제 제출 현황       내 정보
```

**학부모 메뉴는 4개입니다.** 수업영상·레포트, 수업 자료실, 수강 후기는 학부모 화면에서 제외되었습니다.
`latestLesson` 같은 수업 내용 필드를 홈 응답에 넣지 마세요.

자녀가 1명이면 자녀 선택 드롭다운을 숨깁니다.

**전화번호는 마스킹해서 표시하세요** (`010-****-1234`). 서버에서 마스킹한 값을 내려주는 것이 안전합니다.

### 4-2. 메뉴 그리드

KW-Study 메뉴는 넣지 마세요. 공부 시간 기록이 1차 범위 밖이므로 해당 항목이 없습니다. 참고 디자인에 있더라도 제외합니다.

### 4-3. T-1 대시보드

```
2026년 5월 20일 (수)

[오늘 수업]
고2 심화반  19:00  20명
  출석 미확정 · 내용 미작성        [출석 확정하기]

[할 일]
출석 미확정        3건  →
확인 대기 숙제     12건 →
내용 미작성 수업    2건 →
학생 미가입        12명 →
학부모 미가입       7명 →
클리닉 변경 요청    2건 →

[확인]
최근 7일 신규 가입  4명 →     ← 모르는 이름이 있으면 삭제
가입 코드 열림      2개 ⚠ →   ← 등록 기간 끝났으면 닫기
```

숫자가 0인 항목은 회색으로 흐리게 처리하거나 숨기세요. 할 일이 없으면 "오늘 할 일을 모두 마쳤습니다" 같은 상태를 보여주는 것도 좋습니다.

**단, `[확인]` 두 항목은 0이어도 숨기지 마세요.** 할 일이 아니라 점검 항목입니다.
반 코드는 아무나 가입할 수 있어서, 선생님이 명단을 보는 것 자체가 방어 수단입니다.

---

## 5. 완료 조건 (DoD)

### 자료실

- [ ] **자료실이 학생에게만 열려 있다** (학부모 토큰으로 호출 시 403)
- [ ] 업로드 시 `year`·`month`·`week`가 저장되고, 선생님이 고른 값 그대로다
- [ ] A고 2학년 학생에게 B고 자료가 조회되지 않는다
- [ ] `school_id`가 `null`인 자료는 전체 학생에게 조회된다
- [ ] `class_room_id`가 지정된 자료는 해당 반 학생에게만 조회된다
- [ ] **`school_id`가 `null`이고 `class_room_id`만 지정된 자료가 그 반 학생에게 보인다**
      (차원별 독립 검사 확인. 가장 틀리기 쉬운 조합)
- [ ] **활성 배정이 없는 학생이 목록을 조회해도 SQL 오류가 나지 않는다** (빈 `IN` 처리)
- [ ] **목록에 나오지 않는 `materialId`로 download-url을 호출하면 403이 반환된다**
- [ ] 응답에 `s3Key`가 포함되지 않는다
- [ ] download-url이 presigned이고 원본 파일명으로 저장된다
- [ ] `exe`, `html` 등 실행 가능 확장자 업로드가 거부된다

### 공지

- [ ] `scope`별 필수 필드 검증이 동작한다 (`GRADE`인데 `grade` 없으면 400)
- [ ] `published_at`이 `null`인 공지가 학생·학부모에게 보이지 않는다
- [ ] `pinned` 공지가 항상 상단에 온다
- [ ] **학부모 A가 학부모 B의 자녀 `studentId`로 `/api/notices`를 호출하면 403이 반환된다**
- [ ] 공지 내용이 HTML로 렌더링되지 않는다 (`<script>` 입력 후 확인)

### 홈 · 대시보드

- [ ] P-1이 단일 API 호출로 렌더링된다
- [ ] `nextExam`이 `null`일 때 D-day 카드가 숨겨진다
- [ ] 다자녀 학부모가 자녀를 바꾸면 전체 카드 내용이 갱신된다
- [ ] 전화번호가 마스킹되어 표시된다
- [ ] S-1의 `currentHomeworks`에 마감 지난 미제출 숙제가 포함된다
- [ ] T-1의 `todo` 8개 항목이 실제 데이터와 일치한다
- [ ] **`recentSignupCount`가 최근 7일 `students.created_at` 건수와 일치한다**
- [ ] **`openJoinCodeCount`가 `join_code_active = true`인 활성 반 수와 일치한다**
- [ ] **두 항목이 0이어도 화면에서 숨겨지지 않는다**
- [ ] `unsignedStudentCount`가 `students.user_id IS NULL`인 재원생 수와 일치한다
- [ ] `todo` 항목 클릭 시 해당 화면으로 이동한다
- [ ] KW-Study 관련 메뉴가 어디에도 없다
- [ ] **학부모 A가 학부모 B의 자녀 홈을 조회하면 403이 반환된다**

### 전체 통합 확인

- [ ] 세 역할로 각각 로그인해 모든 화면을 순회했을 때 오류가 없다
- [ ] 데이터가 없는 초기 상태에서 모든 화면이 빈 상태 UI로 정상 렌더링된다
- [ ] 모바일 화면(360px 폭)에서 모든 화면이 깨지지 않는다
- [ ] 33개 화면이 모두 구현되어 있다 (`00_README.md` 8절 목록과 대조)

---

## 6. 하지 말 것

- 공지 읽음 표시(`notice_reads`)를 구현하지 마세요.
- 리치 텍스트 에디터, 마크다운 렌더링을 붙이지 마세요. 일반 텍스트 + 줄바꿈만입니다.
- 자료실에 파일 미리보기(PDF 뷰어)를 구현하지 마세요. 다운로드만입니다.
- **자료실을 학부모에게 열지 마세요.** 학생 전용입니다.
- **학부모 홈에 수업 내용·영상·자료 관련 필드를 넣지 마세요.** 했는지 여부만입니다.
- 후기 관련 화면·API·테이블을 만들지 마세요. 1차 범위에서 제외되었습니다.
- 대시보드에 캐시나 집계 테이블을 도입하지 마세요. 200명 규모에서 실시간 count로 충분합니다.
- KW-Study, 공부 시간, 랭킹, 스트릭 관련 화면·메뉴·API를 만들지 마세요.
- 알림 센터, 푸시, 배지 카운트를 구현하지 마세요. 앱 단계 항목입니다.
- 통계 대시보드(월별 출석률 추이 그래프 등)를 임의로 추가하지 마세요. 요구사항에 없습니다.
