# Phase 2 — 인증 및 권한

**선행 조건:** Phase 1
**목표:** 세 역할이 각각 로그인해 자기 영역에 진입하고, 학생이 반 코드로 가입해 자동 배정되고, 학부모가 초대코드로 자녀와 연결된다. 권한 검증 공통 컴포넌트가 완성된다.
**화면:** C-1, C-2, C-3, C-4, P-5

**이 Phase가 이후 모든 Phase의 보안 기반입니다.** 여기서 만드는 `StudentAccessGuard`를 이후 모든 API가 사용합니다.

---

## 1. JWT 설계

| 토큰 | 유효기간 | 저장 위치 | 담는 정보 |
|---|---|---|---|
| Access | 30분 | 메모리 (JS 변수) | `userId`, `role` |
| Refresh | 14일 | `HttpOnly` 쿠키 | `userId`, 토큰 ID |

Access 토큰을 `localStorage`에 저장하지 마세요. XSS로 탈취됩니다. 메모리에 두고 새로고침 시 Refresh로 복구합니다.

Refresh 토큰은 `refresh_tokens` 테이블에 저장해 폐기 가능하게 합니다. 마이그레이션 `V3__refresh_token.sql`을 추가하세요.

```sql
CREATE TABLE refresh_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users(id),
    token_hash VARCHAR(100) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ  NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
```

**토큰 원문을 저장하지 말고 해시를 저장합니다.** DB가 유출되어도 토큰을 재사용할 수 없습니다.

---

## 2. Spring Security 설정

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login", "/api/auth/refresh",
                                 "/api/auth/signup",
                                 "/api/health").permitAll()
                .requestMatchers("/api/teacher/**").hasRole("TEACHER")
                .requestMatchers("/api/student/**").hasRole("STUDENT")
                .requestMatchers("/api/parent/**").hasRole("PARENT")
                .anyRequest().authenticated())
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(e -> e
                .authenticationEntryPoint(jsonAuthEntryPoint())
                .accessDeniedHandler(jsonAccessDeniedHandler()))
            .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

**인증 실패 응답도 `ApiResponse` 포맷으로 통일하세요.** 기본 Spring Security 응답은 HTML이거나 형식이 달라 프론트에서 처리가 갈립니다. `authenticationEntryPoint`와 `accessDeniedHandler`를 직접 구현해 JSON을 반환합니다.

경로 접두사로 역할을 나눠뒀으므로 `/api/teacher/**`에 학생 토큰으로 접근하면 403 `ROLE_NOT_ALLOWED`가 나옵니다.

---

## 3. 권한 검증 공통 컴포넌트

**이 절이 Phase 2에서 가장 중요합니다.**

학부모가 URL의 `studentId`만 바꿔 다른 아이의 성적·출석·숙제를 조회하는 것이 이 서비스에서 가장 흔하고 위험한 사고입니다. 컨트롤러마다 검증을 구현하면 반드시 한두 곳을 빠뜨립니다.

### 3-1. StudentAccessGuard

```java
package com.njwenglish.common.security;

@Component
@RequiredArgsConstructor
public class StudentAccessGuard {

    private final StudentRepository studentRepository;

    /**
     * 현재 로그인 사용자가 studentId에 접근 가능한지 검증하고 Student를 반환한다.
     * 불가하면 STUDENT_NOT_ACCESSIBLE 예외를 던진다.
     *
     * studentId를 파라미터로 받는 모든 서비스는 반드시 이 메서드를 통과해야 한다.
     */
    public Student requireAccessible(Long studentId) {
        AuthUser me = CurrentUser.get();
        Student student = studentRepository.findById(studentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        // student.getUser()는 회원가입 전이면 null이다. 반드시 검사할 것
        boolean allowed = switch (me.role()) {
            case TEACHER -> true;
            case STUDENT -> student.getUser() != null
                         && student.getUser().getId().equals(me.userId());
            case PARENT  -> student.getParent() != null
                         && student.getParent().getUser().getId().equals(me.userId());
        };

        if (!allowed) throw new BusinessException(ErrorCode.STUDENT_NOT_ACCESSIBLE);
        return student;
    }

    /** 학생 본인의 Student 엔티티를 반환 (STUDENT 역할 전용 API에서 사용) */
    public Student requireSelf() {
        AuthUser me = CurrentUser.get();
        return studentRepository.findByUserId(me.userId())
            .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_NOT_ACCESSIBLE));
    }
}
```

### 3-2. 사용 규칙

```java
// 올바른 예 — 학부모 API
@Transactional(readOnly = true)
public AttendanceCalendarResponse getCalendar(Long studentId, int year, int month) {
    Student student = studentAccessGuard.requireAccessible(studentId);  // 반드시 첫 줄
    return attendanceRepository.findCalendar(student.getId(), year, month);
}

// 잘못된 예 — 검증 없이 studentId 사용
public AttendanceCalendarResponse getCalendar(Long studentId, int year, int month) {
    return attendanceRepository.findCalendar(studentId, year, month);   // 취약점
}
```

**규칙: `studentId`를 받는 서비스 메서드의 첫 줄은 `requireAccessible` 호출이다.** 예외 없습니다.

간접 접근에도 적용됩니다. `submissionId`, `lessonId`, `scoreId`로 조회할 때도 해당 리소스가 속한 학생을 찾아 검증하세요.

```java
public SubmissionDetailResponse getSubmission(Long submissionId) {
    Submission submission = submissionRepository.findById(submissionId)
        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    studentAccessGuard.requireAccessible(submission.getStudent().getId());  // 필수
    return SubmissionDetailResponse.from(submission);
}
```

### 3-3. CurrentUser

```java
public record AuthUser(Long userId, UserRole role) {}

public final class CurrentUser {
    public static AuthUser get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthUser user)) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        return user;
    }
}
```

---

## 4. API 명세

### POST `/api/auth/login`

**로그인 아이디는 전화번호입니다** (하이픈 없는 숫자). 프론트는 입력값에서 하이픈·공백을
제거해 보내고, 서버도 한 번 더 정규화한 뒤 조회하세요. 학부모가 `010-1234-5678`로 입력합니다.

```json
// Request
{ "loginId": "01012345678", "password": "********" }

// Response
{
  "success": true,
  "data": {
    "accessToken": "eyJ...",
    "user": { "id": 12, "name": "홍길동", "role": "PARENT" }
  }
}
```

Refresh 토큰은 응답 본문이 아니라 `Set-Cookie`로 내려갑니다.

```
Set-Cookie: refreshToken=eyJ...; HttpOnly; Secure; SameSite=Lax; Path=/api/auth; Max-Age=1209600
```

로컬 개발에서는 `Secure`를 끄되, **운영에서는 반드시 켜세요.**

실패 시 401 `INVALID_CREDENTIALS`. **아이디가 없는 경우와 비밀번호가 틀린 경우를 구분하지 마세요.** 계정 존재 여부가 노출됩니다.

### POST `/api/auth/refresh`

쿠키의 Refresh 토큰으로 새 Access 토큰을 발급합니다. 요청 본문 없음.

```json
{ "success": true, "data": { "accessToken": "eyJ..." } }
```

만료·폐기된 토큰이면 401 `TOKEN_EXPIRED`. 프론트는 이때 로그인 화면으로 보냅니다.

### POST `/api/auth/logout`

Refresh 토큰의 `revoked_at`을 채우고 쿠키를 만료시킵니다.

### GET `/api/auth/me`

```json
{
  "success": true,
  "data": {
    "id": 12, "name": "홍길동", "role": "PARENT",
    "phone": "010-****-1234",
    "mustChangePassword": false
  }
}
```

프론트는 앱 시작 시 이것을 호출해 로그인 상태를 복구합니다.

`mustChangePassword`가 `true`면 비밀번호 변경 화면으로 강제 이동시킵니다. 선생님이 발급한 초기 비밀번호를 쓰는 계정에 해당합니다. `users`에 `must_change_password BOOLEAN NOT NULL DEFAULT false` 컬럼을 `V3__refresh_token.sql`에 함께 추가하세요.

### PATCH `/api/auth/password`

```json
{ "currentPassword": "********", "newPassword": "********" }
```

성공 시 해당 사용자의 모든 Refresh 토큰을 폐기합니다. 다른 기기의 세션을 끊는 것이 맞습니다.

비밀번호 정책: 8자 이상. 학부모 연령대를 고려해 특수문자 강제 등 과한 제약은 넣지 마세요.

### 비밀번호 찾기 — 자동 재설정 경로는 없습니다

이메일 발송과 SMS 인증이 **둘 다 범위 밖**이라, 본인 확인 후 자동으로 재설정하는 경로를 만들 수 없습니다.
`POST /api/auth/password/reset-request` 같은 엔드포인트를 만들지 마세요.

분실 시 복구는 선생님이 합니다.

```
학부모·학생이 선생님에게 연락
  → 선생님이 T-2 학생 상세에서 "비밀번호 초기화"
  → POST /api/teacher/students/{studentId}/reset-password
  → 임시 비밀번호를 화면에 1회 표시 → 구두·문자로 전달
  → 당사자가 로그인하면 must_change_password=true라 변경 화면으로 강제 이동
```

C-2 화면은 **로그인 상태에서의 "비밀번호 변경"만** 담당합니다. 로그인 화면의 "비밀번호를 잊으셨나요?"는
재설정 폼이 아니라 **"선생님께 문의해 주세요"** 안내로 처리하세요.

### POST `/api/auth/signup`

**세 가지 가입이 이 엔드포인트 하나를 씁니다.** 비로그인 상태에서 호출됩니다.

| 코드 | 누가 | 어디서 왔나 |
|---|---|---|
| **반 코드** (`class_rooms.join_code`) | 학생 | 선생님이 수업에서 반 전체에 구두 전달 |
| 개인 코드 `target_role=STUDENT` | 학생 | 선생님이 T-2에서 개별 발급 (보조 경로) |
| 개인 코드 `target_role=PARENT` | 학부모 | 자녀 가입 시 자동 발급된 것을 선생님이 전달 |

**서버가 코드를 보고 판별합니다.** `signup_codes.code`를 먼저 찾고, 없으면
`class_rooms.join_code`를 찾습니다. 두 값은 서로 겹치지 않게 발급됩니다(Phase 3).

**`loginId`와 비밀번호를 받지 않습니다.** `phone`이 로그인 아이디가 되고,
비밀번호는 **초기값 `0000`으로 생성**됩니다.

---

#### 경로 1 — 반 코드 (학생 자가 가입, 주 경로)

```json
// Request
{
  "code": "HK7F2Q",
  "name": "서동환",
  "phone": "01011112222",
  "parentPhone": "01098765432"
}

// Response
{
  "success": true,
  "data": {
    "role": "STUDENT",
    "loginId": "01011112222",
    "studentName": "서동환",
    "classRoomName": "고2 심화반",
    "initialPassword": "0000"
  }
}
```

**처리 (하나의 트랜잭션)**

1. `class_rooms`에서 `join_code` 조회. 없으면 400 `INVITE_CODE_INVALID`
2. `join_code_active = false`거나 `status = 'CLOSED'`면 400 `INVITE_CODE_INVALID`
3. 두 번호 정규화(숫자만). `phone == parentPhone`이면 409 `DUPLICATE_RESOURCE`
4. `phone`이 이미 `users.login_id`면 409 `DUPLICATE_RESOURCE`
5. `students` 생성 — `name`, `user_id = null`, `parent_id = null`
6. `users`(STUDENT, `login_id = phone`, `BCrypt("0000")`, `must_change_password = true`) 생성 후 `students.user_id` 연결
7. `enrollments` 생성 (`class_room_id` = 그 반, `joined_at = 오늘`)
8. `signup_codes`(PARENT, `phone = parentPhone`, 유효기간 7일) **1장 자동 발급**

**8번이 학부모 연결의 시작점입니다.** 선생님이 T-2 학생 상세에서 이 코드를 확인해
학부모에게 전달합니다. 학부모는 반 코드를 쓸 수 없습니다 — 반 코드로 가입하면
어느 학생의 부모인지 알 수 없기 때문입니다.

**`name`은 `students.name`에 저장합니다.** `users.name`에도 같은 값이 들어가지만
(NOT NULL이라) 화면에 쓰는 값은 언제나 `students.name`입니다.

**학교·학년은 묻지 않습니다.** 시스템에 없는 개념입니다(`02_phase1_db_schema.md` 2-2).
학생을 묶는 단위는 반 하나뿐이고, 반은 코드로 이미 정해집니다.

> ⚠️ **반 코드에는 전화번호 대조가 없습니다.** 20명이 나눠 쓰는 값이라 특정인에게 묶을 수
> 없기 때문입니다. 즉 **코드를 아는 사람은 누구나 가입할 수 있습니다.**
>
> 등록 기간이 끝나면 선생님이 T-3에서 코드를 닫아야 합니다(`join_code_active = false`).
> 그것이 이 경로의 유일한 방어선입니다. 2번 검사를 빠뜨리면 방어선이 사라집니다.

---

#### 경로 2 — 개인 코드 (기존 방식, 보조 경로)

```json
// Request  (name은 학부모만 필수. 학생은 선생님이 등록한 이름을 씁니다)
{
  "code": "K7F2QX",
  "phone": "01012345678",
  "name": "홍길동"
}

// Response
{
  "success": true,
  "data": {
    "role": "PARENT",
    "loginId": "01012345678",
    "studentName": "서동환",
    "initialPassword": "0000"
  }
}
```

**공통 처리 (1~4)**

1. `signup_codes`에서 `code` 조회. 없으면 400 `INVITE_CODE_INVALID`
2. `used_at`이 있으면 400 `INVITE_CODE_USED`
3. `expires_at`이 지났으면 400 `INVITE_CODE_INVALID`
4. `phone`이 발급 시 등록된 번호와 다르면 400 `INVITE_CODE_INVALID`

코드와 전화번호를 **모두** 확인해야 합니다. 코드만 검증하면 6자리 무작위 대입으로 남의 계정을 만들 수 있습니다.

실패 원인을 응답에 세분화하지 마세요. 4번 실패를 "전화번호가 다릅니다"로 알려주면
코드만 가진 사람이 번호를 추측할 수 있습니다. 1·3·4를 모두 같은 `INVITE_CODE_INVALID`로 처리합니다.
**반 코드 실패(경로 1의 1·2번)도 같은 코드입니다.** 어느 종류의 코드가 틀렸는지 알려주지 마세요.

**`target_role = STUDENT`일 때 (5~7)**

5. `phone`이 이미 `login_id`로 존재하면 409 `DUPLICATE_RESOURCE`
6. `users`(STUDENT, `login_id = phone`, `password = BCrypt("0000")`, `must_change_password = true`) 생성
7. `students.user_id` 연결, `signup_codes.used_at` 기록

`students` 행과 이름은 선생님이 등록할 때 이미 만들어져 있습니다. 여기서는 계정만 붙입니다.

**`target_role = PARENT`일 때 (5~8)**

5. 같은 `phone`의 `PARENT` 계정이 이미 있으면 새로 만들지 않고 **기존 학부모에 자녀만 추가 연결** (다자녀)
6. 같은 `phone`이 `STUDENT`·`TEACHER` 역할로 존재하면 409 `DUPLICATE_RESOURCE`
7. 없으면 `users`(PARENT, `login_id = phone`, `password = BCrypt("0000")`, `must_change_password = true`)
   + `parents` 생성
8. `students.parent_id` 설정, `signup_codes.used_at` 기록

**PARENT 5번이 다자녀 처리의 핵심입니다.** 형제·자매가 있는 학부모는 계정 하나로 두 아이를 봅니다.
이 분기를 빼면 학부모가 아이마다 별도 계정을 만들게 됩니다.
5번(다자녀 연결)과 6번(충돌 오류)을 헷갈리지 마세요. **같은 번호라도 `PARENT` 역할이면 연결,
`STUDENT`·`TEACHER` 역할이면 오류**입니다.

**이미 가입한 학부모가 둘째 아이 코드를 쓸 때는 비밀번호를 `0000`으로 되돌리지 마세요.**
기존 계정에 자녀만 추가하는 경로라 비밀번호는 그대로입니다.

---

가입 완료 화면에 **"초기 비밀번호 0000으로 로그인한 뒤 바로 변경해 주세요"**를 크게 띄우세요.

### 초기 비밀번호 `0000` — 반드시 함께 넣을 방어책

`0000`은 전원이 아는 값입니다. 가입만 하고 며칠 로그인하지 않으면 그동안 계정이 열려 있고,
**반 친구는 서로 전화번호를 압니다.** 남의 성적·피드백·출석이 그대로 노출됩니다.

**`must_change_password = true`인 토큰은 비밀번호 변경 외 어떤 API도 호출할 수 없게 막으세요.**

```java
// JwtAuthenticationFilter 뒤, 컨트롤러 진입 전
if (me.mustChangePassword() && !isPasswordChangeRequest(request)) {
    throw new BusinessException(ErrorCode.PASSWORD_CHANGE_REQUIRED);   // 403
}
```

허용 경로는 `PATCH /api/auth/password`, `GET /api/auth/me`, `POST /api/auth/logout` 셋뿐입니다.

이러면 `0000`으로 남이 로그인해도 **비밀번호 변경 화면 말고는 아무것도 못 봅니다.**
데이터 유출은 막힙니다. 다만 그 사람이 비밀번호를 바꿔버려 본인이 못 들어오는 상황은 남습니다.
그때는 선생님이 초기화해 주면 되고, 정상적인 사용자는 가입 직후 바로 바꾸므로 창이 매우 짧습니다.

**더 확실한 방법은 가입 화면에서 비밀번호를 직접 정하게 하는 것입니다.** `0000` 자체가 사라집니다.
운영 중 문제가 보이면 이쪽으로 바꾸세요. 요청·응답만 바뀌고 나머지 흐름은 그대로입니다.

### GET `/api/parent/children`

자녀 선택 드롭다운용입니다.

```json
{
  "success": true,
  "data": [
    { "studentId": 88, "name": "서동환" },
    { "studentId": 92, "name": "서동희" }
  ]
}
```

`status = 'ENROLLED'`인 자녀만 반환합니다.

### PATCH `/api/parent/me`

연락처 변경.

```json
{ "phone": "01098765432" }
```

**번호를 바꾸면 로그인 아이디도 함께 바뀝니다.** `login_id`는 `phone`의 정규화 값이라
`User.changePhone()` 한 곳에서 둘을 동시에 갱신하세요. 한쪽만 바꾸면 그 학부모는 로그인하지 못합니다.

새 번호가 이미 다른 계정의 `login_id`면 409 `DUPLICATE_RESOURCE`.
변경 성공 후에는 **"이제 새 번호로 로그인하세요"** 안내를 화면에 띄우세요.

---

## 5. 프론트엔드

### 5-1. 화면

| ID | 경로 | 내용 |
|---|---|---|
| C-1 | `/login` | **전화번호**·비밀번호 입력. 하단에 "처음이신가요? 회원가입" 링크 + "비밀번호를 잊으셨나요? 선생님께 문의" 안내 |
| C-4 | `/signup` | 회원가입. 반 코드 폼(기본) + 개인 코드 폼(보조) |
| C-2 | `/password` | 비밀번호 변경 |
| C-3 | `/terms`, `/privacy` | 정적 페이지 |
| P-5 | `/parent/me` | 자녀 목록, 연락처 변경 |

**C-4는 폼이 두 개입니다.** 코드 종류를 서버가 판별하더라도, 입력 항목이 달라서
화면은 나뉘어야 합니다.

```
[기본] 반 코드로 가입
  반 코드      [HK7F2Q]
  이름         [서동환]
  내 전화번호   [010-1111-2222]
  보호자 번호   [010-9876-5432]
  [가입하기]

  ※ 학교·학년 입력란을 두지 마세요. 시스템에 없는 개념입니다.

  ─────────────────────────
  선생님께 개인 코드를 받으셨나요?  → [개인 코드로 가입]
```

```
[보조] 개인 코드로 가입
  코드         [K7F2QX]
  전화번호      [010-...]
  이름         [홍길동]     ← 학부모만 표시. 코드 제출 후 서버 응답으로 판단하지 말고,
  [가입하기]                   항상 표시하되 선택 입력으로 두는 편이 단순합니다
```

**"코드가 유효한지 미리 확인해 주는 API"를 만들지 마세요.** 코드만으로 유효 여부를
알려주면 무작위 대입의 정답 판별기가 됩니다. 지금은 코드와 전화번호를 함께 제출해야
결과를 알 수 있고, 그 성질을 유지해야 합니다.

**학부모 번호는 학생 가입 시 받습니다.** 이 값으로 학부모용 개인 코드가 자동 발급되고,
선생님이 T-2에서 확인해 전달합니다. 입력란 옆에 "보호자께 안내 문자를 드릴 때 쓰는
번호입니다. 학생 본인 번호와 달라야 합니다"를 적어 두세요.

### 5-2. 인증 상태 관리

```ts
// src/shared/auth/authStore.ts
let accessToken: string | null = null;   // 메모리만

export const getAccessToken = () => accessToken;
export const setAccessToken = (t: string | null) => { accessToken = t; };
```

앱 부팅 흐름:

```
1) POST /api/auth/refresh 시도
2) 성공 → accessToken 저장 → GET /api/auth/me → 역할별 경로로 이동
3) 실패 → /login
```

이 과정이 끝나기 전에는 로딩 화면을 보여주세요. 판정 전에 라우팅하면 로그인 화면이 잠깐 깜빡입니다.

### 5-3. RoleGuard

```tsx
function RoleGuard({ role, children }: { role: UserRole; children: ReactNode }) {
  const { user, loading } = useAuth();
  if (loading) return <FullScreenLoader />;
  if (!user) return <Navigate to="/login" replace />;
  if (user.role !== role) return <Navigate to={homePathOf(user.role)} replace />;
  if (user.mustChangePassword) return <Navigate to="/password" replace />;
  return <>{children}</>;
}
```

### 5-4. 자녀 선택 컨텍스트

학부모 화면 대부분이 `studentId`를 필요로 합니다. 화면마다 드롭다운을 따로 두지 말고 전역 컨텍스트로 관리하세요.

```tsx
// 선택된 자녀 ID를 컨텍스트로 보관, sessionStorage에 유지
const { selectedStudentId, setSelectedStudentId, children } = useSelectedChild();
```

자녀가 1명이면 드롭다운을 숨기고 자동 선택합니다. 200명 중 다자녀 학부모는 소수이므로, 대부분의 사용자에게 불필요한 UI를 보이지 않는 게 좋습니다.

---

## 6. 완료 조건 (DoD)

- [ ] 시드 선생님 계정(전화번호)으로 로그인해 `/teacher`에 진입한다
- [ ] `010-1234-5678`처럼 하이픈을 넣어 입력해도 로그인된다 (정규화 확인)
- [ ] 학부모가 번호를 바꾸면 `phone`과 `login_id`가 함께 갱신되어 새 번호로 로그인된다
- [ ] 이미 쓰이는 번호로 학부모 연결을 시도하면 409 `DUPLICATE_RESOURCE`가 반환된다
- [ ] 잘못된 비밀번호로 401 `INVALID_CREDENTIALS`가 반환되고, 아이디 존재 여부가 노출되지 않는다
- [ ] Access 토큰 만료 후 자동으로 refresh되어 요청이 재시도된다
- [ ] Refresh 실패 시 `/login`으로 이동한다
- [ ] Access 토큰이 `localStorage`에 저장되지 않는다 (개발자 도구로 확인)
- [ ] 학생 토큰으로 `/api/teacher/**` 호출 시 403 `ROLE_NOT_ALLOWED`가 반환된다
- [ ] **학생이 반 코드로 가입하면 `students`(name 포함) + `users`(STUDENT) + `enrollments`가 한 트랜잭션으로 생성된다**
- [ ] **반 코드로 가입한 학생이 그 반 명단에 즉시 나타난다** (선생님 배정 없이)
- [ ] **반 코드 가입 시 `signup_codes`(PARENT) 1장이 자동 발급되고 T-2에서 확인된다**
- [ ] **`join_code_active = false`인 코드로 가입하면 400 `INVITE_CODE_INVALID`가 반환된다**
- [ ] **`CLOSED` 상태인 반의 코드로 가입하면 400이 반환된다**
- [ ] **반 코드 가입에서 학생 번호와 보호자 번호가 같으면 409가 반환된다**
- [ ] **`students.name`에 가입 폼의 이름이 저장되고, T-2 명단에 그 이름이 보인다**
- [ ] **학부모가 반 코드로 가입을 시도하면 학생 계정이 만들어진다** (반 코드는 항상 STUDENT)
- [ ] **코드 유효성만 미리 확인하는 엔드포인트가 존재하지 않는다**
- [ ] **반 코드 실패와 개인 코드 실패가 같은 `INVITE_CODE_INVALID`로 반환된다**
- [ ] 학생이 개인 코드 + 본인 번호로 회원가입하면 `users`(STUDENT)가 생성되고 `students.user_id`가 연결된다
- [ ] 학부모가 코드 + 본인 번호로 회원가입하면 계정이 생성되고 자녀가 연결된다
- [ ] 개인 코드 화면에서 역할을 고르지 않아도 `target_role`로 학생/학부모가 갈린다
- [ ] **같은 학부모가 두 번째 코드를 사용하면 새 계정이 아니라 기존 계정에 자녀가 추가된다**
- [ ] 다자녀 추가 연결 시 기존 비밀번호가 `0000`으로 초기화되지 않는다
- [ ] 사용된 코드 재사용 시 400 `INVITE_CODE_USED`가 반환된다
- [ ] 전화번호 불일치 시 실패 사유가 코드 오류와 구분되지 않는다
- [ ] 가입 직후 계정 비밀번호가 `0000`이고 `must_change_password = true`다
- [ ] **`must_change_password = true` 상태에서 비밀번호 변경 외 API를 호출하면 403이 반환된다**
- [ ] 미가입 학생(`user_id IS NULL`)이 섞인 목록 조회에서 NPE가 나지 않는다
- [ ] **학부모 A가 학부모 B의 자녀 `studentId`로 API를 호출하면 403 `STUDENT_NOT_ACCESSIBLE`이 반환된다** (수동 테스트 필수)
- [ ] 비밀번호 변경 시 기존 Refresh 토큰이 모두 폐기된다
- [ ] 인증 실패 응답이 `ApiResponse` JSON 포맷이다

### 권한 테스트 (반드시 작성)

```java
@Test
void 학부모는_다른_학부모의_자녀에_접근할_수_없다() { }

@Test
void 학생은_다른_학생의_데이터에_접근할_수_없다() { }

@Test
void 선생님은_모든_학생에_접근할_수_있다() { }

@Test
void 학부모_연결이_없는_학생은_학부모가_접근할_수_없다() { }
```

이 네 개는 이후 Phase에서 회귀 방지용으로 계속 돌아갑니다.

---

## 7. 하지 말 것

- 소셜 로그인(카카오·구글)을 붙이지 마세요. 학원 발급 계정만 사용합니다.
- 이메일 인증, 이메일 발송을 구현하지 마세요.
- SMS 본인인증을 구현하지 마세요. 초대코드 + 전화번호 일치 확인으로 충분하고, SMS는 과금 대상입니다.
- 코드 없는 자유 회원가입 화면을 만들지 마세요. 학생은 반 코드 또는 개인 코드, 학부모는 개인 코드만입니다.
- **학부모에게 반 코드로 가입할 경로를 주지 마세요.** 어느 학생의 부모인지 알 수 없습니다.
- **반 코드에 전화번호 대조를 억지로 붙이려 하지 마세요.** 여러 명이 쓰는 값이라 묶을 대상이 없습니다. 방어는 `join_code_active`입니다.
- **코드 유효성만 확인해 주는 엔드포인트를 만들지 마세요.** 무작위 대입의 정답 판별기가 됩니다.
- 별도의 관리자(ADMIN) 역할을 만들지 마세요. 강사 1인 운영이라 `TEACHER`로 통합합니다.
- 컨트롤러에서 `studentId` 소유권을 직접 검사하지 마세요. `StudentAccessGuard`만 사용합니다.
- 권한 검증을 프론트엔드에만 두지 마세요. 화면을 숨기는 것은 UX이고, 실제 차단은 서버에서만 유효합니다.
- **법정대리인 동의 절차를 임의로 만들지 마세요.** 방식이 미확정입니다 (`spec/requirements_for_client.md` 5-1).
  현재 범위는 약관·처리방침 **게시(C-3)까지**입니다. 확정 전에 동의 체크박스나 이력 테이블을 넣지 마세요.
