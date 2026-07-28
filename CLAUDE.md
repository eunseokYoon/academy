# 학원 관리 서비스

영어강사 1명, 학교 2곳(각 1~3학년), 학생 200명. 이게 전부다.
200이라는 숫자를 계속 떠올려라. 여기서 나올 나쁜 결정의 대부분은 "나중에 커지면"에서 나온다. 안 커진다.

웹 3종(학생/학부모/선생님)이 먼저, 앱은 나중.
개발 명세는 `docs/`에 있다. 계약·미확정 사항은 `spec/requirements_for_client.md`에 있다.
시작 전에 `docs/00_README.md`와 해당 Phase 문서를 읽어라.
엔드포인트는 `docs/10_api_reference.md`가 기준이다. Phase 문서와 다르면 그쪽을 따라라.
단, 레퍼런스에도 구버전 잔재가 있다. 개인정보 노출 방향으로 어긋나면 좁은 쪽을 따르고 물어봐라.

## 스택

Java 21 / Spring Boot 3.3 / PostgreSQL + Flyway / JPA / JWT
React 18 + TS + Vite + TanStack Query + Tailwind
Docker Compose · EC2 한 대 · S3

프론트는 한 코드베이스에 `/student` `/parent` `/teacher` 라우팅만 나눈다. 세 프로젝트로 쪼개면 유지보수가 3배다.

Docker: 로컬은 **DB만** 컨테이너(`docker compose up -d`), 백엔드·프론트는 호스트에서 직접 돌린다.
전부 컨테이너면 코드 한 줄 고칠 때마다 이미지를 다시 빌드하게 된다.
운영은 `web`(nginx) + `backend` + `certbot` 3개. **운영 DB는 컨테이너가 아니라 관리형(Supabase/RDS)이다.**

## 틀리면 아픈 것들

고치기 어렵거나, 조용히 망가져서 한참 뒤에 발견되는 것들.

**1. `studentId`를 받는 서비스 메서드는 첫 줄이 `studentAccessGuard.requireAccessible(studentId)`다.**
예외 없다. 컨트롤러에서 직접 검사하지 마라. 빼먹으면 학부모가 URL 숫자만 바꿔 남의 아이 성적을 본다.

**2. 스키마는 Flyway로만 바꾼다.** `ddl-auto: validate` 고정, 기존 마이그레이션은 안 건드린다.
엔티티와 스키마가 어긋나 부팅이 실패하면 버그가 아니라 기능이다.

**3. 반 학생은 항상 `findActiveStudents(classRoomId, targetDate)`로 뽑는다.**
`students`를 직접 조회하면 퇴원생이 출석부에 남아서 결석으로 쌓인다.
정렬은 `ORDER BY e.student.name`이다. **`e.student.user.name`을 쓰지 마라.**
암묵적 INNER JOIN이 생겨 미가입 학생이 명단에서 통째로 사라진다. 에러도 안 난다.

**3-1. 학생 이름은 `students.name`이다.** `users.name`이 아니다.
미가입 학생은 `users` 행이 없다. 출석부·숙제 명단이 전부 미가입 학생을 포함한다.

**4. 숙제 출제 시 대상 전원의 `submissions`를, 출석 확정 시 재원생 전원의 `attendances`를 미리 만든다.**
제출할 때 만들면 될 것 같지만 그러면 미제출자를 매번 LEFT JOIN으로 역산해야 한다. 연 8천 행, 아무것도 아니다.

**5. `attendance_status = PENDING`인 날은 출석이 아니다.** 회색 "미확인"이다.
기본값이 출석이라, 이 구분이 없으면 선생님이 깜빡한 날이 학부모에게 초록색으로 보인다.

**6. 파일은 presigned URL로 S3에 직접 올린다.** 서버 경유 금지.
사진은 올리기 전 브라우저에서 리사이즈(장변 1600px, WebP). 안 하면 연 200GB다.

**7. 학생·학부모 조회에는 `published_at IS NOT NULL`을 넣는다.** 작성 중인 초안이 학부모한테 보이면 안 된다.

**8. 컨테이너에서 세 가지.** JVM은 `-Xmx` 말고 `-XX:MaxRAMPercentage`(고정값은 한도 넘겨 OOM Kill).
이미지에 타임존 `Asia/Seoul` 박기(Alpine 기본 UTC라 마감시각이 9시간 어긋난다).
Compose에 `logging` max-size 걸기(안 걸면 로그가 디스크를 채워 서비스가 멈춘다).

## 만들지 마라

결제, SMS/알림톡, 자동 독려, 공부시간 기록, 랭킹, 스트릭, 소셜 로그인, 자유 회원가입, 별도 ADMIN 역할.
전부 범위 밖이고 학원장이 확정한 사항이다. 그리고,

- 영상은 YouTube 미등록 링크 문자열만 저장한다. 업로드도 트랜스코딩도 스트리밍도 없다.
- 등수·백분위·반 평균 같은 상대 지표는 계산도 노출도 하지 않는다.
- 캐시, 큐, 로드밸런서, 오토스케일링, k8s/ECS도 넣지 마라. 학생이 200명이고 Compose 한 파일이면 된다.
- 운영 DB를 컨테이너로 띄우지 마라. 인스턴스가 죽으면 데이터가 같이 간다.
- 이미지 태그에 `latest`를 쓰지 마라. 롤백할 게 없어진다. 날짜나 커밋 해시를 써라.
- 참고 이미지에 KW-Study(공부시간·랭킹) 화면이 있다. 무시해라.

## 컨벤션

- 응답은 전부 `ApiResponse<T>` = `{success, data, error}`. 목록은 `PageResponse`.
- enum은 `@Enumerated(EnumType.STRING)`. ordinal로 저장되면 순서 바뀔 때 데이터가 조용히 깨진다.
- 연관관계는 전부 `LAZY`. 엔티티에 setter 만들지 마라.
- DB는 `TIMESTAMPTZ`, 앱은 `Asia/Seoul`, API는 ISO 8601.
- 테이블·컬럼 snake_case, API 필드 camelCase.
- 화면은 모바일 먼저. 360px에서 안 깨지면 된다.

## 순서

`0 설정 → 1 DB → 2 인증 → 3 학생/반/수업 → {4 출석, 5 숙제, 6 영상/성적/시험} → 7 자료실/공지/홈 → 8 배포`

4·5·6은 독립이라 순서 바꿔도 된다. 2는 건너뛰지 마라, 이후 모든 API가 여기 기대고 있다.
출석 캘린더의 `homeworkRate`는 Phase 5 끝나고 채운다. 그 전엔 `null` 내려라.

## 계정

**로그인 아이디는 전화번호다.** `users.login_id = phone`(숫자만), 둘은 `changePhone()` 한 곳에서 같이 바꾼다.
학생과 학부모는 서로 다른 번호여야 한다 (`login_id` UNIQUE).

**계정은 선생님이 안 만든다.** `users`는 당사자가 `POST /api/auth/signup`으로 가입할 때 생긴다.
그래서 `students.user_id`가 nullable이고, **`student.getUser()`는 null일 수 있다.** null 검사 빼먹지 마라.

**가입 주 경로는 반 코드다.** 선생님이 반을 만들면 `class_rooms.join_code`가 발급되고,
수업에서 반 전체에 구두로 알린다. 학생이 코드 + 이름 + 본인번호 + 보호자번호로
가입하면 `students`(name 포함) + `users` + `enrollments`가 한 번에 생기고 그 반에 자동 배정된다.
**학교·학년은 묻지 않는다.** 한 반은 한 학교·한 학년이라 반 코드가 이미 알고 있다.
`class_rooms.school_id`·`grade`를 `students`로 복사해라. 요청 본문으로 받으면 반과 어긋난다.
동시에 `signup_codes`(PARENT) 1장이 발급되고, 선생님이 T-2에서 확인해 학부모에게 전달한다.
보조 경로로 선생님 직접 등록(`POST /api/teacher/students` + 코드 2장)이 남아 있다.

**반 코드는 전화번호 대조가 없다.** 20명이 나눠 쓰는 값이라 본인을 묶을 수 없다.
코드를 아는 사람은 누구나 가입한다. 승인 대기 단계는 두지 않는다(확정).
**막는 대신 선생님이 발견해서 지운다.** 그래서 세 가지가 짝으로 필요하다.

1. `join_code_active` — 가입 처리에서 반드시 검사. 등록 기간 끝나면 닫는다
2. `GET /api/teacher/students?sort=recent` + T-1의 `recentSignupCount` — 탐지
3. `DELETE /api/teacher/students/{studentId}` — 제거. **운영 기록 있으면 409**

셋 중 하나라도 빠지면 나머지가 무의미하다. 특히 3번은 `submissions`가 `NOT_SUBMITTED`로
미리 깔려 있어서 "행이 있으면 409"로 짜면 아무도 못 지운다. `status <> 'NOT_SUBMITTED'`로 봐라.

실제로 다닌 학생은 언제나 `withdraw`다. `DELETE`는 제3자 전용이고 되돌릴 수 없다.
학부모에게는 반 코드 경로를 주지 마라. 어느 학생의 부모인지 알 수 없다.

초기 비밀번호는 `0000`. **`must_change_password = true`인 토큰은 비밀번호 변경 외 전부 403
`PASSWORD_CHANGE_REQUIRED`로 막아라.** 전원이 아는 값이라 이게 없으면 남의 성적이 샌다.
허용 경로는 `PATCH /auth/password`, `GET /auth/me`, `POST /auth/logout` 셋뿐이다.

비밀번호 찾기 API는 없다. 선생님이 `POST /api/teacher/students/{studentId}/reset-password`로 초기화한다.

## 온라인 테스트

시험은 **종이**로 본다. 학생은 종이 시험지를 풀고 답만 웹에 입력한다. 문제지 파일은 저장하지 않는다.
서버가 가진 건 정답 배열과 해설지뿐이다.

**응시 화면 응답에 정답과 해설지 URL을 넣지 마라.** null로 비우지도 마라. 필드 자체를 빼라.
응시용 DTO와 결과용 DTO를 분리해라. 안 그러면 개발자 도구로 정답이 다 보인다.

채점은 `획득 배점 / 전체 배점 × 100`. **`100/문항수`를 문항마다 더하지 마라.** 30문항 만점이 99.99가 된다.
미체크는 오답, 감점 없음.

임시 저장은 서버에 한다(출석과 반대). 25문항에 20~30분 걸려서 브라우저 닫히면 다 날아간다.

`score_type`이 있으면 채점 결과가 `scores`로 자동 반영된다. 단어시험을 이걸로 치르면 이중 입력이 없어진다.

공개 후 정답 수정 금지. 이미 응시한 학생 점수가 소급 변경된다.

## 아직 안 정해진 것

- `V2__seed.sql`의 학교명이 `A고등학교`/`B고등학교`다. 실제 이름 받으면 바꿔라. 선생님 계정 번호도 마찬가지.
- 숙제 사진 보관 기간 미정. S3 수명주기 규칙 만들지 마라.
- **퇴원생 데이터 보관 기간 미정.** 지금은 무기한이다. 자동 삭제를 임의로 넣지 마라.
- **법정대리인 동의 절차 미정.** 지금 범위는 약관·처리방침 게시(C-3)까지다. 동의 체크박스나 이력 테이블을 만들지 마라.
- 영상 호스팅·결제·알림 수단·성적 범위가 계약상 미확정이다. `spec/requirements_for_client.md` 4장의
  「현재 가정」대로 진행 중이니, 다른 값을 전제로 코드를 짜지 마라.
- **클리닉 변경 사유 옵션 미정.** `clinic_change_requests.reason_code`는 `String`으로 두고
  CHECK 제약도 enum도 만들지 마라. 값을 그럴듯하게 지어내지 마라.
- **클리닉 1회 정원 미정.** `clinics.capacity`는 nullable이고 NULL이면 제한 없음이다.

정해지지 않은 걸 그럴듯하게 채우지 마라. 물어보는 게 낫다.
