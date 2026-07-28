# Phase 5 — 숙제

**선행 조건:** Phase 3
**목표:** 선생님이 숙제를 출제하고, 학생이 사진을 올려 제출하고, 선생님이 피드백을 단다. 학생은 사진·피드백을 보고, 학부모는 제출 여부만 본다.
**화면:** T-6, T-7, S-2, S-3, S-4, P-3

**이 Phase가 서비스에서 가장 많이 사용되는 기능입니다.** 특히 T-7은 강사 1명이 200명을 확인하는 화면이라, 여기가 불편하면 서비스 전체를 안 씁니다. 프론트 작업량의 상당 부분을 T-7에 배분하세요.

---

## 1. 핵심 설계

### 1-1. 출제 시 submissions 미리 생성

숙제를 출제하는 순간 대상 학생 **전원**의 `submissions` 행을 `NOT_SUBMITTED`로 만듭니다.

제출할 때 행을 생성하는 방식이면 "누가 안 냈는지"를 매번 `students LEFT JOIN submissions`로 역산해야 합니다. 미리 만들어두면 `WHERE homework_id = ? AND status = 'NOT_SUBMITTED'` 한 줄이고, 앱 단계에서 독려 알림을 붙일 때 그대로 대상자 목록이 됩니다.

### 1-2. 상태 전이

```
NOT_SUBMITTED  →  SUBMITTED  →  CHECKED
   (출제 시)      (학생 제출)    (선생님 피드백)
```

`is_late`는 별도 boolean입니다. 상태에 `LATE`를 넣지 않는 이유는 "늦게 냈지만 확인 완료"를 표현할 수 없기 때문입니다.

### 1-3. 마감 후 제출 정책 (확정)

**마감 후에도 제출을 허용하고 `is_late = true`로 기록합니다.**

차단(409 `DUE_DATE_PASSED`) 방식도 가능하지만, 실제로는 늦게라도 내는 학생이 대부분이고 아예 막으면 선생님이 예외 처리를 손으로 해야 합니다. `DUE_DATE_PASSED` 에러 코드는 정의만 되어 있고 이 API에서는 사용하지 않습니다.

### 1-4. 사진은 S3 직접 업로드

서버를 경유하지 않습니다. presigned URL을 발급해 클라이언트가 S3로 직접 PUT합니다. 서버 경유는 EC2 대역폭을 낭비하고 사진 10장 업로드 시 타임아웃이 발생합니다.

**업로드 전 브라우저에서 리사이즈합니다.** 200명 × 주 1~2회 × 3~5장이면 연 5~8만 장입니다. 요즘 폰 사진은 장당 3~4MB이므로 원본 그대로면 연 200GB가 넘고, 선생님이 T-7에서 넘길 때마다 로딩이 멈춥니다.

---

## 2. 숙제 템플릿

| Method | Endpoint | 설명 |
|---|---|---|
| GET | `/api/teacher/homework-templates` | 목록 (`use_count DESC`) |
| POST | `/api/teacher/homework-templates` | 저장 |
| DELETE | `/api/teacher/homework-templates/{templateId}` | 삭제 |

```json
{
  "success": true,
  "data": [
    { "id": 1, "title": "단어 시험", "description": "챕터 단어 50개", "useCount": 42 },
    { "id": 2, "title": "독해 문제 3p", "description": null, "useCount": 31 },
    { "id": 3, "title": "본문 필사", "description": "틀린 문장은 빨간색으로 고쳐서 필사", "useCount": 18 }
  ]
}
```

출제 시 템플릿을 사용하면 `use_count`를 1 증가시킵니다. 목록을 사용 빈도 내림차순으로 정렬해 자주 쓰는 숙제가 위에 오게 하세요.

---

## 3. 숙제 출제 (T-6)

### POST `/api/teacher/homeworks`

```json
// Request
{
  "classRoomId": 3,
  "lessonId": 501,
  "title": "주간지 전 범위 풀기",
  "description": "워크북 27~35쪽 풀어오기",
  "dueAt": "2026-05-21T20:00:00+09:00",
  "templateId": null,
  "saveAsTemplate": false
}

// Response
{
  "success": true,
  "data": {
    "homeworkId": 720,
    "targetCount": 20
  }
}
```

**처리 (하나의 트랜잭션)**

```java
@Transactional
public HomeworkCreateResponse create(HomeworkCreateRequest req) {
    ClassRoom classRoom = classRoomRepository.findById(req.classRoomId())
        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

    Homework homework = homeworkRepository.save(Homework.of(classRoom, req, currentTeacher()));

    // 마감일 기준 재원생 전원
    LocalDate targetDate = req.dueAt().toLocalDate();
    List<Student> students = enrollmentRepository
        .findActiveStudents(classRoom.getId(), targetDate);

    List<Submission> rows = students.stream()
        .map(s -> Submission.notSubmitted(homework, s))
        .toList();
    submissionRepository.saveAll(rows);

    if (req.templateId() != null) templateService.increaseUseCount(req.templateId());
    if (req.saveAsTemplate()) templateService.save(req.title(), req.description());

    return new HomeworkCreateResponse(homework.getId(), rows.size());
}
```

`lessonId`는 선택입니다. 수업과 연결하면 캘린더의 숙제 완료율 색띠 계산에 사용됩니다. **가능하면 연결하도록 UI에서 유도하세요.**

대상은 반 전체입니다. 일부 학생만 지정하는 기능은 1차 범위 밖입니다.

### 기타 API

| Method | Endpoint | 설명 |
|---|---|---|
| GET | `/api/teacher/homeworks` | 목록 (`classRoomId`, `from`, `to`, 페이징) |
| GET | `/api/teacher/homeworks/{homeworkId}` | 상세 |
| PATCH | `/api/teacher/homeworks/{homeworkId}` | 내용·마감 수정 |
| DELETE | `/api/teacher/homeworks/{homeworkId}` | 삭제 |

**삭제 차단 조건은 "행의 존재"가 아니라 "상태"입니다.**

`status`가 `SUBMITTED` 또는 `CHECKED`인 `submissions`가 1건이라도 있으면 409를 반환하세요.
학생이 올린 사진이 경고 없이 사라지면 안 됩니다.

```sql
SELECT count(*) FROM submissions
WHERE homework_id = :homeworkId AND status <> 'NOT_SUBMITTED';
```

**"`submissions` 행이 있으면 409"로 구현하면 어떤 숙제도 삭제할 수 없습니다.** 1-1에서 출제 즉시
대상 전원의 행을 `NOT_SUBMITTED`로 만들기 때문에, 행은 언제나 존재합니다.

삭제 가능할 때(전원 미제출)는 한 트랜잭션에서 **`submissions`를 먼저 지우고** `homeworks`를 지웁니다.
`submissions.homework_id`에는 `ON DELETE CASCADE`가 없으므로 그냥 지우면 FK 제약 위반이 납니다.
`submission_photos`와 `feedbacks`는 `submissions`에서 CASCADE로 따라 지워집니다.

마감 시각을 앞당기는 수정을 하면 이미 제출한 학생의 `is_late`를 재계산해야 합니다. 구현이 번거로우므로 **마감은 늦추는 방향만 허용**하고, 앞당기려면 삭제 후 재출제하도록 하세요.

---

## 4. 학생 숙제 제출 (S-2, S-3, S-4)

### 4-1. API

| Method | Endpoint | 설명 |
|---|---|---|
| GET | `/api/student/homeworks` | 목록 (`status` 필터, 페이징) |
| GET | `/api/student/homeworks/{homeworkId}` | 상세 + 피드백 |
| POST | `/api/student/homeworks/{homeworkId}/photos/upload-url` | 업로드 URL 발급 |
| POST | `/api/student/homeworks/{homeworkId}/photos` | 업로드 완료 등록 |
| DELETE | `/api/student/homeworks/{homeworkId}/photos/{photoId}` | 사진 삭제 |
| POST | `/api/student/homeworks/{homeworkId}/submit` | 제출 확정 |

### GET `/api/student/homeworks`

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "homeworkId": 720, "title": "주간지 전 범위 풀기",
        "classRoomName": "고2 심화반",
        "dueAt": "2026-05-21T20:00:00+09:00",
        "status": "NOT_SUBMITTED",
        "isLate": false, "photoCount": 0, "hasFeedback": false,
        "remainingMinutes": 137
      }
    ],
    "page": 0, "size": 20, "totalElements": 34, "totalPages": 2
  }
}
```

`remainingMinutes`는 마감까지 남은 분입니다. 음수면 마감이 지난 것입니다. 프론트에서 계산하지 말고 서버가 내려주세요. 클라이언트 시계는 틀릴 수 있습니다.

정렬: 미제출이면서 마감 임박한 것이 먼저. `status = 'NOT_SUBMITTED'` 우선, 그 안에서 `due_at ASC`.

### 4-2. 사진 업로드 흐름

```
1) POST /api/student/homeworks/{homeworkId}/photos/upload-url
   Request:  { "contentType": "image/webp", "bytes": 284012 }
   Response: { "uploadUrl": "https://...", "s3Key": "submissions/2026/05/{uuid}.webp" }

2) 클라이언트가 uploadUrl로 직접 PUT
   - 업로드 전 Canvas로 리사이즈: 장변 1600px, WebP, quality 0.8
   - Content-Type 헤더를 1번에서 보낸 값과 동일하게 설정

3) POST /api/student/homeworks/{homeworkId}/photos
   Request:  { "s3Key": "submissions/2026/05/{uuid}.webp", "sortOrder": 1, "bytes": 284012 }
   Response: { "photoId": 8812, "photoCount": 1 }
```

**서버 검증 항목**

- 사진 수가 10장을 넘으면 409 `PHOTO_LIMIT_EXCEEDED`
- `contentType`이 `image/jpeg`, `image/png`, `image/webp` 중 하나가 아니면 400 `UNSUPPORTED_FILE_TYPE`
- `bytes`가 10MB를 넘으면 413 `FILE_TOO_LARGE`
- `s3Key`가 서버가 발급한 형식과 일치하는지 확인 (클라이언트가 임의 경로를 보내지 못하게)

presigned URL 유효기간은 5분입니다. 만료되면 1번을 다시 호출합니다.

**s3Key 검증이 중요합니다.** 클라이언트가 보낸 `s3Key`를 그대로 신뢰하면 다른 경로의 객체를 참조하게 만들 수 있습니다. 발급 시 `s3Key`를 서버 측에 임시 저장(또는 서명된 토큰으로 발급)하고, 3번에서 대조하세요.

### 클라이언트 리사이즈

```ts
async function resizeImage(file: File, maxSide = 1600, quality = 0.8): Promise<Blob> {
  const bitmap = await createImageBitmap(file);
  const scale = Math.min(1, maxSide / Math.max(bitmap.width, bitmap.height));
  const canvas = document.createElement("canvas");
  canvas.width = Math.round(bitmap.width * scale);
  canvas.height = Math.round(bitmap.height * scale);
  canvas.getContext("2d")!.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
  return new Promise((resolve) =>
    canvas.toBlob((b) => resolve(b!), "image/webp", quality)
  );
}
```

**리사이즈를 생략하지 마세요.** 이 한 함수가 연간 저장 비용의 대부분을 결정합니다.

EXIF 회전 정보 때문에 사진이 눕는 경우가 있습니다. `createImageBitmap(file, { imageOrientation: "from-image" })`를 사용하세요.

### POST `/api/student/homeworks/{homeworkId}/submit`

```json
{
  "success": true,
  "data": {
    "submissionId": 4412,
    "status": "SUBMITTED",
    "submittedAt": "2026-05-21T19:42:00+09:00",
    "isLate": false,
    "photoCount": 2
  }
}
```

**처리**

1. 사진이 0장이면 400 `VALIDATION_FAILED` — "사진을 1장 이상 첨부해 주세요"
2. `status = 'SUBMITTED'`, `submitted_at = now()`
3. `is_late = now() > homework.due_at`
4. 마감이 지나도 **허용**

제출 후 마감 전까지 사진 추가·삭제와 재제출을 허용합니다. `status`는 `SUBMITTED`를 유지합니다.

`status`가 이미 `CHECKED`(선생님 확인 완료)면 수정을 막으세요. 선생님이 본 내용이 바뀌면 안 됩니다.

### GET `/api/student/homeworks/{homeworkId}`

```json
{
  "success": true,
  "data": {
    "homework": {
      "id": 720, "title": "주간지 전 범위 풀기",
      "description": "워크북 27~35쪽 풀어오기",
      "dueAt": "2026-05-21T20:00:00+09:00",
      "classRoomName": "고2 심화반"
    },
    "submission": {
      "id": 4412, "status": "CHECKED",
      "submittedAt": "2026-05-21T19:42:00+09:00", "isLate": false,
      "photos": [
        { "photoId": 8812, "url": "https://...", "sortOrder": 1 },
        { "photoId": 8813, "url": "https://...", "sortOrder": 2 }
      ]
    },
    "feedback": {
      "content": "본문 필사 꼼꼼히 잘했어요. 독해도 이대로 꾸준히.",
      "createdAt": "2026-05-22T09:15:00+09:00"
    }
  }
}
```

사진 `url`은 **읽기용 presigned URL**입니다 (유효기간 10분). S3 버킷을 공개로 만들지 마세요. 숙제 사진에 학생 필기와 이름이 담깁니다.

---

## 5. 숙제 확인 및 피드백 (T-7)

### GET `/api/teacher/homeworks/{homeworkId}/submissions`

**이 화면이 서비스의 승부처입니다.**

```json
{
  "success": true,
  "data": {
    "homework": {
      "id": 720, "title": "주간지 전 범위 풀기",
      "classRoomName": "고2 심화반",
      "dueAt": "2026-05-21T20:00:00+09:00"
    },
    "counts": { "total": 20, "notSubmitted": 3, "submitted": 5, "checked": 12 },
    "items": [
      {
        "submissionId": 4412, "studentId": 88, "studentName": "서동환",
        "status": "SUBMITTED", "submittedAt": "2026-05-21T19:42:00+09:00",
        "isLate": false, "photoCount": 2, "hasFeedback": false,
        "thumbnailUrl": "https://..."
      },
      {
        "submissionId": 4413, "studentId": 91, "studentName": "김하늘",
        "status": "NOT_SUBMITTED", "submittedAt": null,
        "isLate": false, "photoCount": 0, "hasFeedback": false,
        "thumbnailUrl": null
      }
    ]
  }
}
```

**페이징 없이 전체를 반환합니다.** 반 단위(최대 30명 내외)이므로 한 화면에 다 보여주는 것이 맞습니다.

정렬: 미확인(`SUBMITTED`) → 미제출(`NOT_SUBMITTED`) → 확인완료(`CHECKED`). 선생님이 처리해야 할 것이 위에 옵니다.

`thumbnailUrl`은 첫 사진의 읽기용 presigned URL입니다. **N+1 쿼리에 주의하세요.** 20명 각각에 대해 사진을 따로 조회하면 21번 쿼리가 나갑니다. `submission_photos`를 `sort_order = 1` 조건으로 한 번에 가져와 매핑하세요.

### GET `/api/teacher/submissions/{submissionId}`

사진 전체와 학생 정보를 반환합니다. 응답에 **이전·다음 제출물의 ID를 포함하세요.**

```json
{
  "success": true,
  "data": {
    "submissionId": 4412,
    "studentName": "서동환",
    "status": "SUBMITTED",
    "submittedAt": "2026-05-21T19:42:00+09:00",
    "isLate": false,
    "photos": [
      { "photoId": 8812, "url": "https://...", "sortOrder": 1 },
      { "photoId": 8813, "url": "https://...", "sortOrder": 2 }
    ],
    "feedback": null,
    "prevSubmissionId": 4409,
    "nextSubmissionId": 4415
  }
}
```

`prev`/`next`가 있으면 선생님이 목록으로 돌아가지 않고 **사진을 연속으로 넘기며** 피드백을 이어서 쓸 수 있습니다. 200명을 확인해야 하므로 이 동선이 없으면 실사용이 어렵습니다.

`next`는 아직 확인하지 않은(`SUBMITTED`) 제출물 중 다음 것을 가리키게 하세요.

### POST `/api/teacher/submissions/{submissionId}/feedback`

```json
// Request
{ "content": "본문 필사 꼼꼼히 잘했어요. 독해도 이대로 꾸준히." }
```

**처리**

1. `feedbacks` 생성 (`submission_id`가 UNIQUE이므로 1건만)
2. `submissions.status = 'CHECKED'`

작성된 피드백은 **학생 화면에만** 노출됩니다 (S-4). 학부모에게는 보이지 않습니다. 별도 발송 과정이 없습니다.

| Method | Endpoint | 설명 |
|---|---|---|
| PATCH | `/api/teacher/submissions/{submissionId}/feedback` | 수정 |
| POST | `/api/teacher/submissions/{submissionId}/check` | 피드백 없이 확인 처리만 |

`check`는 피드백을 쓰지 않고 확인만 하고 넘어갈 때 사용합니다. 200명 전원에게 글을 쓰는 것은 현실적이지 않으므로 이 경로가 필요합니다.

### GET `/api/teacher/homeworks/pending`

미확인 숙제 요약입니다. T-1 대시보드에서 사용합니다.

```json
{
  "success": true,
  "data": [
    { "homeworkId": 720, "title": "주간지 전 범위 풀기",
      "classRoomName": "고2 심화반",
      "dueAt": "2026-05-21T20:00:00+09:00",
      "notSubmitted": 3, "awaitingCheck": 5 }
  ]
}
```

---

## 6. 학부모 조회

| Method | Endpoint | 설명 |
|---|---|---|
| GET | `/api/parent/children/{studentId}/homeworks` | **제출 여부만** |

**학부모는 "했는지 여부"만 봅니다.** 숙제 내용·사진·피드백은 학생 화면(S-4)에서만 보입니다.

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "homeworkId": 720, "title": "주간지 전 범위 풀기",
        "classRoomName": "고2 심화반",
        "dueAt": "2026-05-21T20:00:00+09:00",
        "status": "SUBMITTED", "isLate": false, "checked": true
      }
    ],
    "page": 0, "size": 20, "totalElements": 34, "totalPages": 2
  }
}
```

| 넣는 것 | 넣지 않는 것 |
|---|---|
| `title` (제목까지만) | `description` (숙제 내용) |
| `status` `isLate` `checked` | `photos` `photoCount` `thumbnailUrl` |
| `dueAt` | `feedback` |

**`GET /api/parent/children/{studentId}/homeworks/{homeworkId}` 상세 엔드포인트를 만들지 마세요.**
목록의 제출 여부가 학부모가 보는 전부입니다. 학생 API 응답 DTO를 그대로 재사용하면
사진 URL과 피드백이 따라 나갑니다. **학부모 전용 DTO를 따로 만드세요.**

**서비스 첫 줄에 `studentAccessGuard.requireAccessible(studentId)`를 호출하세요.**

학부모는 조회만 합니다. 사진 업로드·삭제·제출 API를 학부모에게 열지 마세요.

---

## 7. Phase 4 연동 — 숙제 완료율

Phase 4에서 `null`로 두었던 캘린더의 `homeworkRate`를 이제 채웁니다.

```sql
-- 특정 학생의 특정 수업일 숙제 완료율
-- 그날 숙제가 없으면 NULL이다. coalesce로 0을 만들지 말 것
SELECT count(*) FILTER (WHERE s.status IN ('SUBMITTED','CHECKED')) * 100
         / nullif(count(*), 0)
FROM homeworks h
JOIN submissions s ON s.homework_id = h.id AND s.student_id = :studentId
WHERE h.lesson_id = :lessonId;
```

**`coalesce(..., 0)`을 붙이지 마세요.** 숙제가 없는 날은 `count(*) = 0`이라 결과가 NULL인데,
`coalesce`가 그걸 0으로 바꾸면 **캘린더에 빨간 띠**가 뜹니다(`05_phase4_attendance.md` 4-2).
학부모는 "우리 애가 하나도 안 냈다"로 읽습니다. 실제로는 그날 숙제가 없었을 뿐입니다.

`0`(전부 미제출)과 `null`(숙제 없음)은 다른 값입니다.

월 전체(`homeworkCompletionRate`)는 해당 월 수업일에 연결된 숙제 전체를 대상으로 계산합니다.
그 달에 숙제가 하나도 없으면 역시 `null`입니다.

`lesson_id`가 `null`인 숙제는 캘린더 색띠 계산에서 제외됩니다. 이 때문에 출제 시 `lessonId` 연결을 UI에서 유도해야 합니다.

**Phase 4의 완료 조건 중 `homeworkRate` 관련 항목을 이 시점에 다시 확인하세요.**

---

## 8. 프론트엔드

### 8-1. S-3 숙제 제출

```
1) 숙제 제목·설명·마감 표시 (남은 시간 강조)
2) 사진 추가 버튼 → 카메라 또는 갤러리
3) 선택 즉시 리사이즈 → 업로드 (진행률 표시)
4) 썸네일 그리드 + 삭제 버튼, "최대 10장" 표시
5) 제출하기 버튼
```

`<input type="file" accept="image/*" multiple>`을 사용합니다. 모바일에서 카메라와 갤러리 선택이 함께 뜹니다.

**업로드 진행 상태를 반드시 보여주세요.** 리사이즈 + 업로드에 몇 초가 걸리는데 아무 반응이 없으면 학생이 버튼을 여러 번 누릅니다.

업로드 실패 시 해당 사진만 재시도할 수 있게 하세요. 10장 중 1장 실패했다고 전부 다시 올리게 하면 안 됩니다.

### 8-2. T-7 숙제 확인 격자

```
[숙제 제목]  미제출 3 · 확인대기 5 · 완료 12

┌──────┬──────┬──────┐
│ 서동환 │ 김하늘 │ 박서준 │
│ [썸네일]│ 미제출 │ [썸네일]│
│ 대기   │       │ 완료  │
└──────┴──────┴──────┘
```

- 모바일 2열, 태블릿 3열, 데스크톱 4~5열
- 미제출 학생은 회색 카드로 구분
- 카드 탭 → 상세 뷰어 (사진 확대, 좌우 스와이프)
- 상세 뷰어 하단에 피드백 입력창 + "저장하고 다음" 버튼

**"저장하고 다음" 버튼이 핵심입니다.** 저장 후 목록으로 돌아가지 않고 다음 미확인 제출물로 바로 넘어갑니다. `nextSubmissionId`를 사용하세요.

자주 쓰는 피드백 문구를 버튼으로 두면 좋습니다 ("잘했어요", "다시 확인 필요", "글씨 정성껏"). 200명에게 매번 새로 쓰는 것은 불가능합니다.

### 8-3. 사진 뷰어

- 핀치 줌 또는 탭 확대 필수. 학생 필기를 읽어야 합니다
- 좌우 스와이프로 사진 간 이동
- 사진 매수 표시 (2/5)

---

## 9. 완료 조건 (DoD)

- [ ] 숙제 출제 시 재원생 전원의 `submissions`가 `NOT_SUBMITTED`로 생성된다
- [ ] `targetCount`가 실제 재원생 수와 일치한다
- [ ] 마감일 기준 재원생만 대상이 된다 (퇴원생 제외)
- [ ] 템플릿 사용 시 `use_count`가 증가한다
- [ ] presigned URL로 S3에 직접 업로드된다 (서버를 경유하지 않음)
- [ ] 업로드 전 클라이언트에서 리사이즈되어 장당 용량이 1MB 미만이다
- [ ] EXIF 회전이 반영되어 사진이 눕지 않는다
- [ ] 11번째 사진 업로드 시 409 `PHOTO_LIMIT_EXCEEDED`가 반환된다
- [ ] 허용되지 않은 `contentType`으로 URL 발급 시 400이 반환된다
- [ ] 클라이언트가 임의 `s3Key`를 보내면 거부된다
- [ ] 사진 0장으로 제출 시 400이 반환된다
- [ ] 마감 후 제출이 허용되고 `is_late = true`로 기록된다
- [ ] `CHECKED` 상태에서는 학생이 사진을 수정할 수 없다
- [ ] 피드백 작성 시 `status`가 `CHECKED`로 바뀐다
- [ ] **피드백이 학생 화면에만 노출되고 학부모 응답에는 포함되지 않는다**
- [ ] T-7 목록에서 미제출자가 한눈에 구분된다
- [ ] T-7 목록 조회가 N+1 없이 처리된다 (쿼리 로그 확인)
- [ ] 상세 응답에 `prevSubmissionId`/`nextSubmissionId`가 포함된다
- [ ] "저장하고 다음"으로 목록 복귀 없이 연속 처리된다
- [ ] `SUBMITTED`/`CHECKED` 제출물이 있는 숙제 삭제 시 409가 반환된다
- [ ] **전원 미제출인 숙제는 삭제되고 `submissions` 행도 함께 정리된다** (FK 위반이 나지 않음)
- [ ] 사진 URL이 presigned이며 버킷이 비공개다
- [ ] **학부모 응답에 photos·thumbnailUrl·feedback·description이 없다**
- [ ] 학부모용 숙제 상세 엔드포인트가 존재하지 않는다
- [ ] **학부모 A가 학부모 B의 자녀 숙제를 조회하면 403이 반환된다**
- [ ] **학부모에게 제출·삭제 API가 열려 있지 않다**
- [ ] Phase 4 캘린더의 `homeworkRate`가 실제 값으로 채워진다
- [ ] **숙제가 없던 수업일의 `homeworkRate`가 `0`이 아니라 `null`이다**
- [ ] 출제 대상이 `students.name` 기준으로 정렬되고, 미가입 학생도 포함된다

### 테스트 시나리오

```java
@Test
void 출제_시_재원생_전원의_submission이_생성된다() { }

@Test
void 마감_후_제출은_허용되고_is_late가_true다() { }

@Test
void 사진_11장_업로드는_거부된다() { }

@Test
void CHECKED_상태에서는_사진을_수정할_수_없다() { }

@Test
void 학부모는_자녀의_숙제만_조회할_수_있다() { }

@Test
void 제출한_학생이_있는_숙제는_삭제할_수_없다() { }

@Test
void 전원_미제출인_숙제는_submissions와_함께_삭제된다() { }
```

---

## 10. 하지 말 것

- 자동 채점, OCR, AI 첨삭을 구현하지 마세요.
- 미제출자 자동 독려 알림을 보내지 마세요. 발송 수단은 1차 범위 밖입니다. `submissions`의 미제출 목록 조회까지만 구현하면, 앱 단계에서 그대로 대상자로 사용합니다.
- 사진을 서버(EC2)로 받아 처리하지 마세요. presigned URL 직접 업로드만입니다.
- 서버 측 이미지 리사이즈(Lambda·ImageMagick)를 구현하지 마세요. 클라이언트 리사이즈로 충분합니다.
- S3 버킷을 공개로 설정하지 마세요. 학생 필기와 이름이 담긴 사진입니다.
- PDF·문서 파일 제출을 허용하지 마세요. 사진만입니다.
- 일부 학생만 지정해 출제하는 기능을 만들지 마세요. 반 전체가 대상입니다.
- 학생 간 제출물 상호 열람 기능을 만들지 마세요.
- 학부모에게 제출·수정 권한을 주지 마세요. 조회만입니다.
- **학부모 응답에 사진·피드백·숙제 내용을 넣지 마세요.** 학생 DTO를 재사용하면 그대로 새어 나갑니다.
- 학부모용 숙제 상세 엔드포인트를 만들지 마세요. 목록의 제출 여부까지입니다.
