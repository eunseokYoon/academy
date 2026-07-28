# Phase 6 — 수업 레포트 · 성적 · 시험 일정

**선행 조건:** Phase 3
**목표:** 학생이 수업 영상과 레포트를 보고, 온라인 테스트를 응시해 자동 채점을 받는다. 학생·학부모가 성적과 시험 D-day를 확인한다.
**화면:** P-4, S-5, S-7, S-10, T-8, T-11, T-14

---

## 1. 수업 영상 및 레포트 (S-5)

**학생 전용입니다.** 학부모는 수업 영상·수업내용·중점사항을 보지 않습니다.

### 1-1. API

| Method | Endpoint | 역할 |
|---|---|---|
| GET | `/api/student/lessons` | S |
| GET | `/api/student/lessons/{lessonId}` | S |
| POST | `/api/student/lessons/{lessonId}/view` | S |
| GET | `/api/teacher/lessons/{lessonId}/views` | T |

**`/api/parent/children/{studentId}/lessons`를 만들지 마세요.** 학부모 범위 밖입니다.

### GET `/api/student/lessons?year&month&week&page&size`

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "lessonId": 501,
        "lessonDate": "2026-05-20",
        "title": "관계대명사 what과 관계부사",
        "classRoomName": "고2 심화반",
        "hasVideo": true,
        "isNew": true,
        "viewed": false,
        "homeworkTitle": "주간지 전 범위 풀기"
      }
    ],
    "page": 0, "size": 20, "totalElements": 18, "totalPages": 1
  }
}
```

**조회 조건 (반드시 모두 적용)**

```
1) studentAccessGuard.requireSelf()   // 학생 본인
2) 학생이 소속된(또는 소속되었던) 반의 수업만
3) published_at IS NOT NULL
4) lesson_date >= 해당 반 enrollment.joined_at
   AND (enrollment.left_at IS NULL OR lesson_date < enrollment.left_at)
```

**4번을 빠뜨리지 마세요.** 이것이 없으면 5월에 입반한 학생이 3월 수업 영상을 보게 됩니다. 학생이 재원 중이었던 기간의 수업만 노출해야 합니다.

`isNew`는 최근 7일 내 공개된 수업입니다. `viewed`는 `lesson_views`에 기록이 있는지 여부입니다.

### GET `/api/student/lessons/{lessonId}`

```json
{
  "success": true,
  "data": {
    "lessonId": 501,
    "lessonDate": "2026-05-20",
    "title": "관계대명사 what과 관계부사",
    "classRoomName": "고2 심화반",
    "videoId": "xxxxxxxxxxx",
    "embedUrl": "https://www.youtube.com/embed/xxxxxxxxxxx",
    "content": "관계대명사 what과 that의 구분을 학습하였으며...",
    "keyPoints": "선행사가 있으면 that, 없으면 what을 씁니다...",
    "nextPreview": "분사구문 (부대상황 with + 목적어 + 분사)",
    "homework": {
      "homeworkId": 720,
      "title": "주간지 전 범위 풀기",
      "description": "워크북 27~35쪽 풀어오기",
      "dueAt": "2026-05-21T20:00:00+09:00",
      "submissionStatus": "SUBMITTED"
    },
    "attendanceStatus": "PRESENT"
  }
}
```

`videoId`와 `embedUrl`을 서버에서 파싱해 내려줍니다. 프론트가 URL 형식을 판단하지 않게 하세요 (Phase 3에서 구현).

`videoId`가 `null`이면 영상이 등록되지 않은 수업입니다. 프론트는 영상 영역을 숨깁니다.

### 1-2. 영상 시청 기록

### POST `/api/student/lessons/{lessonId}/view`

```json
{ "watchSeconds": 320 }
```

**동작**

- `lesson_views`에 `(lesson_id, student_id)` UNIQUE 제약이 있으므로 upsert
- `first_viewed_at`은 최초 1회만 기록
- `last_viewed_at = now()`
- `watch_seconds`는 **누적 합산** (`watch_seconds = watch_seconds + :watchSeconds`)

```sql
INSERT INTO lesson_views (lesson_id, student_id, watch_seconds)
VALUES (:lessonId, :studentId, :watchSeconds)
ON CONFLICT (lesson_id, student_id) DO UPDATE
SET last_viewed_at = now(),
    watch_seconds = lesson_views.watch_seconds + EXCLUDED.watch_seconds;
```

**호출 시점**

| 시점 | 값 |
|---|---|
| 재생 시작 | `watchSeconds: 0` (첫 시청 기록 생성) |
| 30초마다 | 누적 30 |
| 페이지 이탈 | 마지막 구간 |

**과도하게 자주 호출하지 마세요.** 30초 간격이면 충분합니다. 매초 호출하면 200명 기준으로 불필요한 부하가 생깁니다.

이탈 시점 기록은 `visibilitychange` 이벤트를 사용하세요. `beforeunload`는 모바일에서 신뢰할 수 없습니다.

**YouTube 재생 상태를 정밀 추적하지 마세요.** IFrame Player API로 정확한 시청 구간을 계산하려 하면 복잡도가 급증합니다. "봤는지 여부와 대략적 누적 시간"만 필요합니다.

### GET `/api/teacher/lessons/{lessonId}/views`

```json
{
  "success": true,
  "data": {
    "lessonId": 501,
    "totalStudents": 20,
    "viewedCount": 14,
    "items": [
      { "studentId": 88, "name": "서동환", "viewed": true,
        "firstViewedAt": "2026-05-21T20:14:00+09:00", "watchSeconds": 1820 },
      { "studentId": 91, "name": "김하늘", "viewed": false,
        "firstViewedAt": null, "watchSeconds": 0 }
    ]
  }
}
```

미시청 학생도 목록에 포함합니다 (`viewed: false`). 선생님이 확인하려는 것은 "누가 안 봤는지"입니다.

---

## 2. 시험 일정 (T-11)

### 2-1. API

| Method | Endpoint | 설명 |
|---|---|---|
| GET | `/api/teacher/exam-schedules?classRoomId&year` | 목록 |
| POST | `/api/teacher/exam-schedules` | 등록 |
| PATCH | `/api/teacher/exam-schedules/{examScheduleId}` | 수정 |
| DELETE | `/api/teacher/exam-schedules/{examScheduleId}` | 삭제 |

### POST `/api/teacher/exam-schedules`

```json
{
  "classRoomId": 3,
  "year": 2026,
  "semester": 1,
  "examType": "FINAL",
  "startDate": "2026-06-25",
  "endDate": "2026-06-30",
  "scopeNote": "교과서 5~8과, 부교재 전 범위"
}
```

`exam_schedules`에 `UNIQUE (class_room_id, year, semester, exam_type)`가 있습니다. 중복 등록 시 409 `DUPLICATE_RESOURCE`.

**같은 시험이라도 반마다 따로 등록합니다.** 학교·학년 개념이 없어 한 번에 묶을 수 없습니다.
같은 학교 같은 학년 반이 3개면 3번 등록해야 하므로, **화면에서 반 다중 선택을 지원하고
한 번의 저장으로 여러 행을 만드세요.** 한 반만 빠지면 그 반 학생들의 D-day가 비어 있게 됩니다.

**여기 등록된 일정이 학생·학부모 홈 화면 D-day의 유일한 근거입니다.** 등록이 안 되어 있으면 D-day가 표시되지 않습니다.

### 2-2. D-day 계산

```java
public Optional<NextExamResponse> findNextExam(Student student) {
    // 학생이 속한 모든 재원 반의 일정 중 가장 가까운 것
    return examScheduleRepository.findFirstByClassRoomIdInAndStartDateGreaterThanEqualOrderByStartDate(
            enrollmentRepository.findActiveClassRoomIds(student.getId(), LocalDate.now(KST)),
            LocalDate.now(KST))
        .map(e -> new NextExamResponse(
            e.getExamType(), e.getStartDate(), e.getScopeNote(),
            (int) ChronoUnit.DAYS.between(LocalDate.now(KST), e.getStartDate())));
}
```

**학생이 여러 반에 속할 수 있습니다.** 재원 중인 반 전체를 대상으로 가장 가까운 일정 하나를 고릅니다.
`findFirst...`로 한 건만 가져오되, 반 목록을 `enrollments`에서 `left_at IS NULL` 조건으로 뽑으세요.
퇴원한 반의 일정이 섞이면 지난 학기 시험이 D-day로 뜹니다.

다자녀 학부모는 자녀별로 다른 D-day를 봅니다. 자녀 선택에 따라 값이 바뀌어야 합니다.

일정이 없으면 `null`을 반환하고, 프론트는 해당 영역을 숨깁니다. **0이나 임의 값을 넣지 마세요.**

### 2-3. 학생·학부모 조회

| Method | Endpoint | 설명 |
|---|---|---|
| GET | `/api/student/exam-schedules` | 본인 반 일정 |
| GET | `/api/parent/children/{studentId}/exam-schedules` | 자녀 반 일정 |

```json
{
  "success": true,
  "data": [
    { "examType": "MIDTERM", "startDate": "2026-04-28", "endDate": "2026-05-02",
      "scopeNote": "교과서 1~4과", "dDay": -31 },
    { "examType": "FINAL", "startDate": "2026-06-25", "endDate": "2026-06-30",
      "scopeNote": "교과서 5~8과, 부교재 전 범위", "dDay": 27 }
  ]
}
```

---

## 3. 성적 (T-8, P-4, S-7)

### 3-1. API

| Method | Endpoint | 역할 |
|---|---|---|
| POST | `/api/teacher/students/{studentId}/scores` | T |
| POST | `/api/teacher/scores/bulk` | T |
| PATCH | `/api/teacher/scores/{scoreId}` | T |
| DELETE | `/api/teacher/scores/{scoreId}` | T |
| GET | `/api/teacher/students/{studentId}/scores` | T |
| GET | `/api/student/scores` | S |
| GET | `/api/parent/children/{studentId}/scores` | P (주차별 그래프) |

### POST `/api/teacher/students/{studentId}/scores`

```json
{
  "scoreType": "INTERNAL",
  "examScheduleId": 12,
  "examName": "1학기 중간고사",
  "subject": "영어",
  "rawScore": 96,
  "gradeLevel": 1,
  "examDate": "2026-04-28",
  "memo": null
}
```

`scoreType` 세 가지입니다.

| 값 | 뜻 | `rawScore` | `gradeLevel` | `examScheduleId` |
|---|---|---|---|---|
| `WORD` | **주간 단어 테스트** | **100점 만점 환산 필수** | `null` | `null` |
| `INTERNAL` | 내신 | 원점수 | 1~9등급 | 내신일 때만 연결 |
| `MOCK` | 모의고사 | 있을 수도, 없을 수도 | 1~9등급 | `null` |

**`year`·`month`·`week`는 세 종류 모두 필수입니다.** T-8 화면이 주차를 먼저 고르는 구조라,
선택한 주차를 그대로 실어 보냅니다. 서버가 날짜에서 계산하지 마세요.

**`WORD`는 반드시 100점 만점으로 환산해 저장합니다.** 25문항 중 20개면 `80.00`입니다.
문항 수가 주마다 달라지는데 원점수를 그대로 넣으면 P-4 주차별 그래프의 세로축이 무너집니다.
**환산은 입력 화면에서** 하고 DB에는 환산값만 들어갑니다.

`gradeLevel`은 1~9입니다. `rawScore`는 소수 둘째 자리까지 (`NUMERIC(5,2)`).

**성적 관리 범위는 학원장 미확정 사항입니다.** 영어만 입력할 가능성이 높지만 `subject`를 자유 문자열로 두었으므로 전 과목도 가능합니다. UI에서 과목 선택은 드롭다운 + 직접 입력 방식으로 하세요.

### POST `/api/teacher/scores/bulk`

한 시험의 여러 학생 성적을 한 번에 입력합니다.

```json
{
  "scoreType": "INTERNAL",
  "examScheduleId": 12,
  "examName": "1학기 중간고사",
  "subject": "영어",
  "examDate": "2026-04-28",
  "scores": [
    { "studentId": 88, "rawScore": 96, "gradeLevel": 1 },
    { "studentId": 91, "rawScore": 88, "gradeLevel": 2 },
    { "studentId": 97, "rawScore": 72, "gradeLevel": 4 }
  ]
}
```

**이 API를 반드시 구현하세요.** 200명의 성적을 한 명씩 폼으로 입력하게 하면 실사용이 불가능합니다. 프론트는 반 명단을 표 형태로 띄우고 점수만 순서대로 입력하는 화면을 만드세요.

### GET `/api/parent/children/{studentId}/scores?scoreType` (P-4)

**학부모 화면의 핵심입니다.** 자녀의 단어 테스트 흐름을 한눈에 보는 것이 목적이라
표가 아니라 **주차별 시계열**로 내려줍니다. `GET /api/student/scores`도 형식이 같습니다.

```json
{
  "success": true,
  "data": {
    "word": {
      "unit": "주차",
      "points": [
        { "year": 2026, "month": 5, "week": 3, "label": "5월 3주", "score": 88.0,
          "examName": "5월 3주차 단어시험", "examDate": "2026-05-13" },
        { "year": 2026, "month": 5, "week": 4, "label": "5월 4주", "score": 92.0,
          "examName": "5월 4주차 단어시험", "examDate": "2026-05-20" }
      ]
    },
    "internal": [
      { "scoreId": 301, "examName": "1학기 중간고사", "subject": "영어",
        "rawScore": 96.0, "gradeLevel": 1, "examDate": "2026-04-28" }
    ],
    "mock": [
      { "scoreId": 355, "examName": "3월 학평", "subject": "영어",
        "rawScore": null, "gradeLevel": 1, "examDate": "2026-03-26" }
    ]
  }
}
```

**`word.points` 규칙**

- **`year` · `month` · `week` 오름차순**으로 내려주세요. 그래프 가로축 순서 그대로입니다.
  프론트에서 정렬하게 두면 달이 바뀌는 지점에서 어긋납니다.
- `score`는 **100점 만점 환산값**입니다. 원점수를 그대로 내리지 마세요.
- **시험을 안 본 주는 배열에 넣지 마세요.** `null` 점을 넣으면 선이 0으로 떨어져
  "0점 맞았다"로 읽힙니다.
- `label`은 서버가 만듭니다 (`5월 3주`). 프론트가 조립하면 표기가 화면마다 갈립니다.

`internal` · `mock`은 `examDate` 내림차순 목록입니다.

첫 줄에 `studentAccessGuard.requireAccessible(studentId)`를 호출하세요.

### 3-2. 등수·석차를 저장하지 않는 이유

`scores`에 반 등수나 백분위 컬럼이 없습니다. 의도된 설계입니다.

- 내신 석차는 학교에서 산출하는 값이고 학원이 정확히 알 수 없습니다
- 학원 내 등수를 학부모에게 노출하면 민원의 원인이 됩니다
- 필요하면 `memo`에 텍스트로 적습니다

**반 평균, 상위 몇 %, 등수 같은 상대 지표를 계산해 노출하지 마세요.** 요구사항에 없고, 학부모 간 비교로 이어집니다.

### 3-3. 성적 추이

프론트에서 `examDate` 순으로 `gradeLevel`을 꺾은선으로 그리는 것은 괜찮습니다. 등급은 낮을수록 좋으므로 **Y축을 역방향**으로 하세요 (1등급이 위).

서버는 원본 데이터만 내려주고 차트 계산은 프론트에서 합니다. 별도 통계 API를 만들지 마세요.

---

## 4. 온라인 테스트 (T-14, S-10)

**시험은 종이로 봅니다.** 학생이 수업에서 받은 종이 시험지를 풀고, **답만 웹에 입력**하면
제출하는 순간 자동 채점됩니다.

```
수업에서 종이 시험지 배부 (오프라인)
선생님이 출제 (T-14)
  정답 배열 + 해설지 파일 + 주차 + 성적 반영 여부
  → 공개(publish)
     → 학생이 종이 답안을 보고 웹에 답만 입력, 중간중간 자동 저장 (S-10)
     → 제출 → 즉시 채점 → 점수 + 문항별 정오 + 해설지 공개
     → score_type이 있으면 scores에 자동 반영 → P-4 그래프에 얹힘
```

**문제지 파일을 저장하지 않습니다.** 서버가 가진 것은 정답 배열과 해설지뿐입니다.
S-10 화면은 `questionCount`만큼 1~`choiceCount` 라디오 버튼 줄을 그리면 됩니다.

### 4-1. 절대 새면 안 되는 것

**정답(`correctChoices`)과 해설지(`answerS3Key`)를 제출 전 응답에 넣지 마세요.**

`null`로 비우는 것도 안 됩니다. **필드 자체를 응답 DTO에서 빼세요.** 응답에 들어가는 순간
브라우저 개발자 도구에서 그대로 보입니다. 응시 화면 DTO와 결과 화면 DTO를 **분리**하세요.

| DTO | 정답 | 해설지 URL |
|---|---|---|
| `OnlineTestTakeResponse` (응시 중) | ❌ 필드 없음 | ❌ 필드 없음 |
| `OnlineTestResultResponse` (제출 후) | ✅ | ✅ |

### 4-2. 채점

```
correctCount = chosen[i] == correct[i] 인 문항 수
score        = 획득 배점 / 전체 배점 × 100   (소수 둘째 자리 반올림)
```

- **미체크(`null`)는 오답.** 감점은 없습니다.
- **`100 / questionCount`를 문항마다 더하지 마세요.** 30문항이면 `3.333...`이라 만점이 99.99가 됩니다.
  반드시 비율로 한 번에 계산하세요.
- `points` 배열이 있으면 문항별 배점, 없으면 전 문항 1점으로 계산합니다.

### 4-3. 임시 저장은 서버에 합니다

출석(T-5)과 반대입니다. 출석은 30초면 끝나서 프론트 상태로만 뒀지만, **25문항 시험은 20~30분**이
걸립니다. 브라우저가 닫히면 처음부터 다시 해야 하므로 `PUT .../answers`로 서버에 저장하세요.

### 4-4. 성적 반영

`online_tests.score_type`이 채워져 있으면 제출 시 **`scores`에 행을 만듭니다.**
주간 단어시험을 이 기능으로 치르면 선생님이 성적을 따로 입력할 필요가 없고,
P-4 학부모 그래프에 그대로 얹힙니다.

`raw_score`에 **100점 환산값**이 들어가므로 `WORD` 규칙(3절)과 그대로 맞습니다.
`null`이면 연습용이라 성적에 남지 않습니다.

**`scores`의 모든 `NOT NULL` 컬럼을 채워야 합니다.** 특히 `subject`와 `exam_date`는
`online_tests`에 자동으로 존재하지 않습니다.

| `scores` 컬럼 | 출처 |
|---|---|
| `raw_score` | 채점 점수 (100점 환산) |
| `score_type` | `online_tests.score_type` |
| **`subject`** | **`online_tests.subject`** — 출제 시 선생님이 입력 |
| `exam_name` | `online_tests.title` |
| **`exam_date`** | **제출일** (`submitted_at`의 날짜) |
| `year` `month` `week` | `online_tests`에서 복사 |
| `exam_schedule_id` `grade_level` | `null` (내신·모의가 아님) |

**과목을 코드에서 지어내지 마세요.** `"영어"`를 하드코딩하고 싶어지겠지만,
성적 관리 범위(영어만인지 전 과목인지)가 학원장 미확정 사항입니다.

`ck_online_tests_subject`가 "`score_type`이 있으면 `subject`도 있어야 한다"를 DB에서
강제합니다. T-14 출제 화면에서 **성적 반영을 켜면 과목 입력란이 나타나게** 하세요.

중복 반영은 `uq_scores`(`student_id`, `score_type`, `subject`, `exam_name`, `exam_date`)가
막습니다. 재응시가 없어서 원래 1건만 생기지만, 제약이 있으면 그 가정이 깨져도
P-4 그래프에 점이 두 개 찍히지 않습니다.

### 4-5. 공개 후 정답 수정 금지

`published_at`이 채워진 뒤에는 `correctChoices`·`questionCount`를 바꿀 수 없습니다 (409).
이미 응시한 학생의 점수가 소급 변경됩니다. 고치려면 삭제 후 재출제하세요.
제출이 1건이라도 있으면 삭제도 막힙니다.

---

## 5. 프론트엔드

### 5-1. S-5 수업 레포트 (학생 전용)

```
[학생 정보 카드]
서○환 · 고2 심화반

[NEW] 2026.05.20  수업
┌─────────────────────────┐
│  ▶ 수업영상 시청하기      │
└─────────────────────────┘
영상 시청 기록이 선생님께 전달됩니다

수업 내용
...

[중점 사항]
...

[다음 수업]
...

숙제  [HW]
주간지 전 범위 풀기 및 워크북 27~35쪽
```

영상은 YouTube iframe으로 임베드합니다.

```tsx
<iframe
  src={`${embedUrl}?rel=0&modestbranding=1`}
  title="수업 영상"
  allow="accelerometer; autoplay; clipboard-write; encrypted-media; picture-in-picture"
  allowFullScreen
/>
```

`rel=0`으로 관련 동영상 추천을 줄이세요. 학생이 다른 영상으로 새는 것을 막습니다.

**"영상 시청 기록이 선생님께 전달됩니다" 문구를 반드시 표시하세요.** 기록을 수집한다는 사실을 알리는 것이 맞습니다.

### 5-2. T-8 주차별 성적 입력

**주차 선택** → 반 선택 → 명단 표 → 점수 열에 순서대로 입력 → 일괄 저장.

단어 시험은 문항 수를 먼저 입력받아 **화면에서 100점 만점으로 환산**한 뒤 저장하세요.
(예: 25문항 중 20개 → 80점). 원점수를 그대로 보내면 P-4 그래프 세로축이 무너집니다.

- Enter 또는 Tab으로 다음 학생 칸으로 이동
- 시험명·과목·날짜는 상단에서 한 번만 입력
- 저장 전 입력 건수 표시 ("20명 중 18명 입력됨")

한 명씩 저장 버튼을 누르는 방식으로 만들지 마세요.

### 5-3. T-11 시험 일정

반 × 시험 격자로 등록 현황을 보여주세요.

```
              1학기 중간   1학기 기말   2학기 중간   2학기 기말
고2 심화반        ✓            ✓            ✓            −
고2 목요일반      ✓            ✓            −            −
고3 집중반        ✓            −            −            −
```

미등록 칸이 눈에 보여야 합니다. 등록이 빠지면 그 반 학생들의 D-day가 표시되지 않습니다.
**반이 늘어날수록 빠뜨리기 쉬우므로 등록 화면에서 반을 다중 선택하게 만드세요.**

### 5-4. P-4 테스트 결과 (그래프)

**단어 테스트 그래프를 화면 최상단에 둡니다.** 주차가 가로축, 100점 만점 점수가 세로축입니다.

- 서버가 준 `points` 순서를 그대로 그리세요. 다시 정렬하지 마세요.
- 시험 없는 주는 점이 없습니다. 선을 0으로 끌어내리지 마세요.
- 월별 보기 전환을 두면 같은 데이터를 월 평균으로 묶어 보여줍니다.

내신과 모의는 그래프 아래에 탭이나 섹션으로 분리합니다. 등급을 크게, 원점수는 보조로 표시하세요.

---

## 6. 완료 조건 (DoD)

- [ ] `published_at`이 `null`인 수업이 학생·학부모 목록에 나오지 않는다
- [ ] **학생 재원 기간 외의 수업이 조회되지 않는다** (5월 입반 학생에게 3월 수업이 안 보임)
- [ ] `videoId`와 `embedUrl`이 서버에서 파싱되어 내려온다
- [ ] `videoId`가 `null`인 수업에서 프론트가 영상 영역을 숨긴다
- [ ] YouTube iframe이 모바일에서 정상 재생된다
- [ ] 시청 기록이 upsert로 처리되어 `lesson_views` 행이 중복되지 않는다
- [ ] `first_viewed_at`이 최초 1회만 기록된다
- [ ] `watch_seconds`가 누적 합산된다
- [ ] 시청 기록 호출이 30초 간격 이하로 과도하지 않다
- [ ] 선생님 시청 현황에 미시청 학생도 포함된다
- [ ] 시험 일정 중복 등록 시 409가 반환된다
- [ ] **A고 학생에게 A고 시험 일정만 D-day로 표시된다**
- [ ] 다자녀 학부모가 자녀를 바꾸면 D-day가 함께 바뀐다
- [ ] 시험 일정이 없으면 D-day 영역이 숨겨진다 (0이 표시되지 않음)
- [ ] 성적 일괄 입력(bulk)으로 20명을 한 번에 저장할 수 있다
- [ ] 단어 시험이 100점 만점 환산값으로 저장된다 (원점수 아님)
- [ ] 성적에 `year`·`month`·`week`가 선생님이 고른 값으로 저장된다
- [ ] P-4 `word.points`가 주차 오름차순으로 내려온다
- [ ] 시험 없는 주가 `points`에 포함되지 않는다 (0점으로 안 떨어짐)
- [ ] **학부모 토큰으로 `/api/parent/children/{studentId}/lessons` 호출 시 404 또는 403이다**
- [ ] 성적이 `examDate` 내림차순으로 정렬된다
- [ ] 내신과 모의가 구분되어 조회된다
- [ ] **학부모 A가 학부모 B의 자녀 성적을 조회하면 403이 반환된다**
- [ ] 성적 추이 차트의 Y축이 역방향이다 (1등급이 위)
- [ ] 등수·백분위·반 평균이 어디에도 노출되지 않는다

### 온라인 테스트

- [ ] 정답 배열 길이가 `questionCount`와 다르면 400이 반환된다
- [ ] **응시 화면 응답에 정답과 해설지 URL이 없다** (개발자 도구로 확인)
- [ ] 제출 후에만 해설지 URL이 내려온다
- [ ] 미체크 문항이 오답으로 처리되고 감점은 없다
- [ ] 30문항 만점이 정확히 100.00이다 (반올림 누적 오차 없음)
- [ ] 임시 저장 후 브라우저를 닫았다 열어도 답이 유지된다
- [ ] 이미 제출한 테스트를 다시 제출하면 409가 반환된다
- [ ] 마감(`closesAt`) 후 제출하면 400이 반환된다
- [ ] `scoreType`이 있으면 제출 시 `scores` 행이 생성되고 P-4 그래프에 나타난다
- [ ] `scoreType`이 `null`이면 `scores`에 행이 생기지 않는다
- [ ] **`scoreType`을 넣고 `subject`를 비우면 출제 시 400이 반환된다**
- [ ] **자동 생성된 `scores` 행의 `subject`가 `online_tests.subject`와 같다** (하드코딩 아님)
- [ ] **자동 생성된 `scores` 행의 `exam_date`가 제출일이다**
- [ ] **배점 배열(`points`) 길이가 `questionCount`와 다르면 400이 반환된다**
- [ ] **성적 일괄 저장을 두 번 눌러도 `scores` 행이 중복되지 않는다** (`uq_scores` upsert 확인)
- [ ] 공개 후 정답을 수정하려 하면 409가 반환된다
- [ ] 제출이 있는 테스트를 삭제하면 409가 반환된다
- [ ] 선생님 결과 화면에 미응시 학생도 포함된다
- [ ] **반 평균이 학생·학부모 응답에 포함되지 않는다**

### 테스트 시나리오

```java
@Test
void 재원_기간_외의_수업은_조회되지_않는다() { }

@Test
void 미공개_수업은_학생에게_보이지_않는다() { }

@Test
void 시청_기록은_upsert되고_watch_seconds가_누적된다() { }

@Test
void D_day는_학생이_속한_반_기준으로_계산된다() { }

@Test
void 시험_일정이_없으면_D_day는_null이다() { }
```

---

## 7. 하지 말 것

- 영상 파일 업로드, 트랜스코딩, HLS, 자체 스트리밍을 구현하지 마세요. **YouTube 링크 문자열만 저장합니다.**
- YouTube IFrame Player API로 정밀 시청 구간을 추적하지 마세요. 누적 시간만 필요합니다.
- 영상 다운로드 방지, DRM, 화면 녹화 차단을 구현하지 마세요. 미등록 링크의 한계는 학원장이 인지·동의한 사항입니다.
- 등수, 백분위, 반 평균, 상위 몇 % 같은 상대 지표를 계산하거나 노출하지 마세요.
- 성적 예측, 목표 등급 추천 같은 분석 기능을 만들지 마세요.
- 성적 입력을 엑셀 업로드로 만들지 마세요. bulk API + 표 입력 화면으로 충분합니다.
- 학부모·학생이 성적을 수정하는 API를 만들지 마세요. 조회만입니다.
- **학부모용 수업 조회 API를 만들지 마세요.** 영상·수업내용은 학생 전용입니다.
- 단어 시험 원점수를 그대로 저장하지 마세요. 100점 환산값만 저장합니다.
- **온라인 테스트를 `scores`로 반영할 때 `subject`를 코드에서 지어내지 마세요.** 성적 범위가 미확정입니다. 출제 화면에서 받은 값만 씁니다.
- 주차를 서버가 날짜에서 계산해 덮어쓰지 마세요. 선생님이 고른 값이 기준입니다.
- 시험 일정을 외부에서 자동 수집하려 하지 마세요. 선생님이 직접 입력합니다.
- **온라인 테스트 응시 응답에 정답·해설지를 넣지 마세요.** DTO를 응시용·결과용으로 분리합니다.
- 주관식·서술형 문항을 만들지 마세요. 오지선다만입니다.
- 시험 시간 제한 타이머, 문제 순서 섞기, 부정행위 감지를 구현하지 마세요. 요구사항에 없습니다.
- 공개된 테스트의 정답을 수정하지 마세요. 삭제 후 재출제입니다.
