# Phase 0 — 프로젝트 설정 및 공통 기반

**선행 조건:** 없음
**목표:** 백엔드·프론트엔드가 로컬에서 실행되고, 공통 응답 포맷과 에러 처리가 동작한다.

---

## 1. 백엔드 초기화

### 1-1. Gradle 의존성

```kotlin
plugins {
    java
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
}

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
    implementation("software.amazon.awssdk:s3:2.29.9")
    runtimeOnly("org.postgresql:postgresql")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
}
```

### 1-2. application.yml

```yaml
spring:
  application:
    name: academy
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 10
  jpa:
    hibernate:
      ddl-auto: validate      # 절대 update/create로 바꾸지 말 것
    open-in-view: false
    properties:
      hibernate:
        format_sql: true
        jdbc.time_zone: Asia/Seoul
  flyway:
    enabled: true
    baseline-on-migrate: true
  jackson:
    time-zone: Asia/Seoul
    serialization:
      write-dates-as-timestamps: false
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 20MB

server:
  port: 8080
  forward-headers-strategy: native

app:
  jwt:
    secret: ${JWT_SECRET}
    access-token-validity-seconds: 1800        # 30분
    refresh-token-validity-seconds: 1209600    # 14일
  s3:
    bucket: ${S3_BUCKET}
    region: ${AWS_REGION:ap-northeast-2}
    presign-expiry-seconds: 300                # 5분
  cors:
    allowed-origins: ${CORS_ORIGINS:http://localhost:5173}
```

`ddl-auto: validate`는 엔티티와 실제 스키마가 어긋나면 애플리케이션이 부팅에 실패하게 만듭니다. 의도된 동작입니다. 부팅 실패 시 엔티티를 고치거나 새 마이그레이션을 추가하세요.

### 1-3. 환경변수

`.env.example`을 만들고 `.env`는 `.gitignore`에 추가합니다.

```
DB_URL=jdbc:postgresql://localhost:5432/academy
DB_USERNAME=academy
DB_PASSWORD=changeme
JWT_SECRET=최소-32바이트-이상의-임의-문자열
S3_BUCKET=academy-uploads
AWS_REGION=ap-northeast-2
CORS_ORIGINS=http://localhost:5173
```

**`.env`, AWS 키, JWT 시크릿을 커밋하지 마세요.**

---

## 1-4. Docker (로컬 개발)

**로컬에서는 PostgreSQL만 컨테이너로 띄웁니다.** 백엔드와 프론트엔드는 호스트에서 직접 실행하세요.

Spring Boot를 컨테이너에 넣으면 코드 한 줄 고칠 때마다 이미지를 다시 빌드해야 해서 개발 속도가 크게 떨어집니다. 컨테이너화의 이점(환경 일관성)은 배포 단계에서 챙기고, 개발 루프는 빠르게 유지하는 것이 맞습니다.

### docker-compose.yml

```yaml
services:
  db:
    image: postgres:16-alpine
    container_name: academy-db
    environment:
      POSTGRES_DB: academy
      POSTGRES_USER: academy
      POSTGRES_PASSWORD: changeme
      TZ: Asia/Seoul
    ports:
      - "5432:5432"
    volumes:
      - academy-pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U academy -d academy"]
      interval: 5s
      timeout: 3s
      retries: 10

volumes:
  academy-pgdata:
```

```bash
docker compose up -d      # DB 시작
docker compose down       # 중지 (데이터 유지)
docker compose down -v    # 데이터까지 삭제 후 초기화
```

**`down -v`는 볼륨을 지웁니다.** 마이그레이션을 처음부터 다시 검증할 때 쓰세요. 그 외에는 쓰지 마세요.

`healthcheck`가 있어야 DB가 완전히 뜨기 전에 애플리케이션이 붙어 실패하는 상황을 피할 수 있습니다.

### 배포용 Dockerfile

배포는 Phase 8이지만, **이미지 빌드는 지금 만들어 두고 동작을 확인하세요.** 배포 직전에 처음 만들면 반드시 문제가 생깁니다.

`backend/Dockerfile`

```dockerfile
# --- build ---
FROM gradle:8.10-jdk21-alpine AS build
WORKDIR /src
# 의존성 레이어 캐싱: 소스보다 빌드 스크립트를 먼저 복사한다
COPY build.gradle.kts settings.gradle.kts gradle.properties* ./
COPY gradle ./gradle
RUN gradle dependencies --no-daemon || true
COPY src ./src
RUN gradle bootJar --no-daemon -x test

# --- runtime ---
FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache tzdata curl && \
    cp /usr/share/zoneinfo/Asia/Seoul /etc/localtime && \
    echo "Asia/Seoul" > /etc/timezone && \
    addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /src/build/libs/*.jar app.jar
USER app
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -Duser.timezone=Asia/Seoul"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

**주의할 점 세 가지**

1. **`-Xmx` 대신 `-XX:MaxRAMPercentage`를 쓰세요.** 컨테이너 메모리 한도가 바뀌어도 JVM이 따라갑니다. 고정값을 쓰면 컨테이너 한도보다 크게 잡혀 OOM Kill이 납니다.
2. **타임존을 반드시 설정하세요.** Alpine 기본은 UTC라, 안 하면 마감 시각과 출석 날짜가 9시간 어긋납니다.
3. **`USER app`으로 비루트 실행.** 컨테이너를 root로 돌릴 이유가 없습니다.

`frontend/Dockerfile`

```dockerfile
FROM node:22-alpine AS build
WORKDIR /src
COPY package*.json ./
RUN npm ci
COPY . .
ARG VITE_API_BASE_URL=/api
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /src/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

Vite 환경변수는 **빌드 시점에 번들에 박힙니다.** 런타임 주입이 안 되므로 `ARG`로 받아야 합니다.

### .dockerignore

두 디렉터리 각각에 만드세요. 없으면 `node_modules`와 `build`가 통째로 빌드 컨텍스트에 들어가 빌드가 몇 배 느려집니다.

```
# backend/.dockerignore
build/
.gradle/
.env
*.log

# frontend/.dockerignore
node_modules/
dist/
.env
```

---

## 2. 공통 응답 (`common/response`)

### ApiResponse

```java
package com.njwenglish.common.response;

public record ApiResponse<T>(boolean success, T data, ApiError error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null);
    }

    public static ApiResponse<Void> fail(ErrorCode code) {
        return new ApiResponse<>(false, null,
            new ApiError(code.name(), code.getMessage()));
    }
}

public record ApiError(String code, String message) {}
```

### PageResponse

```java
public record PageResponse<T>(
    List<T> items, int page, int size,
    long totalElements, int totalPages
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
            page.getContent(), page.getNumber(), page.getSize(),
            page.getTotalElements(), page.getTotalPages());
    }
}
```

컨트롤러는 항상 `ApiResponse`로 감싸 반환합니다.

```java
@GetMapping("/students")
public ApiResponse<PageResponse<StudentSummaryResponse>> list(...) {
    return ApiResponse.ok(PageResponse.from(studentService.search(...)));
}
```

---

## 3. 에러 처리 (`common/error`)

### ErrorCode

```java
package com.njwenglish.common.error;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "인증이 만료되었습니다. 다시 로그인해 주세요."),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "유효하지 않은 인증 정보입니다."),
    ROLE_NOT_ALLOWED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    STUDENT_NOT_ACCESSIBLE(HttpStatus.FORBIDDEN, "해당 학생 정보에 접근할 수 없습니다."),
    PASSWORD_CHANGE_REQUIRED(HttpStatus.FORBIDDEN, "비밀번호를 먼저 변경해 주세요."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 정보를 찾을 수 없습니다."),
    ALREADY_CONFIRMED(HttpStatus.CONFLICT, "이미 확정된 출석입니다."),
    DUE_DATE_PASSED(HttpStatus.CONFLICT, "마감 시간이 지났습니다."),
    PHOTO_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "사진은 최대 10장까지 첨부할 수 있습니다."),
    CLINIC_CAPACITY_EXCEEDED(HttpStatus.CONFLICT, "정원이 모두 찼습니다."),
    SUBMISSION_EXISTS(HttpStatus.CONFLICT, "제출한 학생이 있어 삭제할 수 없습니다."),
    STUDENT_HAS_RECORDS(HttpStatus.CONFLICT, "운영 기록이 있어 삭제할 수 없습니다. 퇴원 처리를 사용해 주세요."),
    INVITE_CODE_INVALID(HttpStatus.BAD_REQUEST, "초대코드가 유효하지 않습니다."),
    INVITE_CODE_USED(HttpStatus.BAD_REQUEST, "이미 사용된 초대코드입니다."),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "이미 존재하는 데이터입니다."),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "파일 용량이 너무 큽니다."),
    UNSUPPORTED_FILE_TYPE(HttpStatus.BAD_REQUEST, "지원하지 않는 파일 형식입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;
}
```

### BusinessException + 핸들러

```java
@Getter
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        return ResponseEntity.status(e.getErrorCode().getStatus())
            .body(ApiResponse.fail(e.getErrorCode()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(ErrorCode.VALIDATION_FAILED));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.internalServerError()
            .body(ApiResponse.fail(ErrorCode.INTERNAL_ERROR));
    }
}
```

**예외 메시지에 내부 정보를 담지 마세요.** 스택트레이스, SQL, 파일 경로가 응답에 노출되면 안 됩니다. 로그에만 남깁니다.

### 409를 전부 `DUPLICATE_RESOURCE`로 처리하지 마세요

**같은 상태 코드라도 사용자에게 보여줄 문구가 다르면 코드도 달라야 합니다.**
프론트가 `error.code`로 분기하는데, 전부 하나로 뭉치면 문구를 고를 수 없습니다.

| 상황 | code | 화면 문구 |
|---|---|---|
| 클리닉 정원 초과 | `CLINIC_CAPACITY_EXCEEDED` | "정원이 모두 찼습니다" |
| 같은 클리닉 중복 신청 | `DUPLICATE_RESOURCE` | "이미 신청하셨습니다" |
| 제출물이 있는 숙제 삭제 | `SUBMISSION_EXISTS` | "제출한 학생이 있어 삭제할 수 없습니다" |
| 전화번호·수업일·시험일정 중복 | `DUPLICATE_RESOURCE` | "이미 존재합니다" |

`DUPLICATE_RESOURCE`의 메시지는 "이미 존재하는 데이터입니다"입니다.
정원 초과에 붙이면 뜻이 맞지 않습니다.

`ALREADY_CONFIRMED`와 `DUE_DATE_PASSED`는 **정의만 하고 던지지 않습니다.**
출석 재확정과 지각 제출은 정상 흐름입니다. 지우지는 마세요 — 정책이 바뀔 여지를 남긴 것입니다.

---

## 4. 공통 엔티티 (`common/entity`)

**상위 클래스가 두 개입니다.** 테이블마다 감사 컬럼 구성이 달라서, 하나로 통일하면
`ddl-auto: validate`가 부팅 시 실패합니다.

```java
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseCreatedEntity {

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;
}

@Getter
@MappedSuperclass
public abstract class BaseTimeEntity extends BaseCreatedEntity {

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
```

`@EnableJpaAuditing`을 설정 클래스에 추가합니다.

### 4-1. 어느 엔티티가 무엇을 상속하는가

Phase 1의 DDL과 1:1로 대응합니다. **임의로 바꾸면 부팅이 실패합니다.**

| 상속 | 엔티티 | 근거 |
|---|---|---|
| `BaseTimeEntity`<br>(15개) | `User` `Student` `ClassRoom` `Lesson` `HomeworkTemplate` `Homework`<br>`Submission` `Feedback` `ExamSchedule` `Score` `Notice` `Clinic` `ClinicReservation` `OnlineTest` `OnlineTestSubmission` | `created_at`·`updated_at` 둘 다 `NOT NULL DEFAULT now()` |
| `BaseCreatedEntity`<br>(8개) | `School` `Teacher` `Parent` `SignupCode` `Enrollment`<br>`SubmissionPhoto` `Material` `ClinicChangeRequest` | `created_at`만 존재 |
| `BaseCreatedEntity`<br>+ 직접 선언 (1개) | `Attendance` | 아래 참조 |
| 상속 없음 (1개) | `LessonView` | `first_viewed_at`·`last_viewed_at`만 있고 감사 컬럼이 없음 |

합이 **25개**로 Phase 1의 테이블 수와 일치해야 합니다. 숫자가 안 맞으면 빠진 엔티티가 있는 것입니다.
Phase 2에서 추가되는 `RefreshToken`(`created_at`만)은 `BaseCreatedEntity`이며 이 표에 없습니다.

**`Attendance`는 `BaseTimeEntity`를 상속하면 안 됩니다.**

`attendances.updated_at`은 감사 타임스탬프가 아니라 **출석 정정 시각**입니다. `updated_by`와 한 쌍이고,
정정이 없으면 `NULL`입니다(컬럼도 nullable). `@LastModifiedDate`를 붙이면 저장할 때마다 자동으로 채워져
"정정된 적 없음"을 표현할 수 없게 되고, "누가 언제 고쳤는지"라는 원래 의미가 사라집니다.

```java
@Entity
@Table(name = "attendances")
public class Attendance extends BaseCreatedEntity {

    @Column(name = "updated_at")          // nullable. 자동 갱신 금지
    private OffsetDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private Teacher updatedBy;

    /** 정정할 때만 호출한다. */
    public void correct(AttendanceStatus status, String memo, Teacher teacher) {
        this.status = status;
        this.memo = memo;
        this.updatedBy = teacher;
        this.updatedAt = OffsetDateTime.now();
    }
}
```

`OffsetDateTime` ↔ `TIMESTAMPTZ` 매핑입니다. `LocalDateTime`을 쓰면 오프셋이 날아갑니다.

---

## 5. CORS 설정

```java
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOrigins(allowedOrigins)
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true)
            .maxAge(3600);
    }
}
```

`allowedOrigins("*")`와 `allowCredentials(true)`는 함께 쓸 수 없습니다. 환경변수로 명시적 도메인을 지정하세요.

---

## 6. 프론트엔드 초기화

### 6-1. 패키지

```json
{
  "dependencies": {
    "react": "^18.3.1",
    "react-dom": "^18.3.1",
    "react-router-dom": "^6.28.0",
    "@tanstack/react-query": "^5.59.0",
    "axios": "^1.7.7",
    "date-fns": "^4.1.0"
  },
  "devDependencies": {
    "typescript": "^5.6.0",
    "vite": "^5.4.0",
    "@vitejs/plugin-react": "^4.3.0",
    "tailwindcss": "^3.4.0",
    "autoprefixer": "^10.4.0",
    "postcss": "^8.4.0"
  }
}
```

### 6-2. 라우팅 구조

```tsx
// src/router.tsx
export const router = createBrowserRouter([
  { path: "/login", element: <LoginPage /> },
  { path: "/terms", element: <TermsPage /> },
  { path: "/privacy", element: <PrivacyPage /> },
  {
    path: "/student",
    element: <RoleGuard role="STUDENT"><StudentLayout /></RoleGuard>,
    children: [ /* Phase 5, 6, 7에서 추가 */ ],
  },
  {
    path: "/parent",
    element: <RoleGuard role="PARENT"><ParentLayout /></RoleGuard>,
    children: [ /* ... */ ],
  },
  {
    path: "/teacher",
    element: <RoleGuard role="TEACHER"><TeacherLayout /></RoleGuard>,
    children: [ /* ... */ ],
  },
  { path: "/", element: <RoleRedirect /> },
]);
```

`RoleRedirect`는 로그인한 사용자의 역할에 따라 `/student`, `/parent`, `/teacher`로 보냅니다. 비로그인이면 `/login`입니다.

### 6-3. API 클라이언트

```ts
// src/shared/api/client.ts
export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? "/api",
});

api.interceptors.request.use((config) => {
  const token = getAccessToken();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// 401 → refresh 1회 재시도 → 실패 시 /login
api.interceptors.response.use(
  (res) => res,
  async (error) => {
    if (error.response?.status === 401 && !error.config._retried) {
      error.config._retried = true;
      if (await tryRefresh()) return api(error.config);
      redirectToLogin();
    }
    return Promise.reject(error);
  }
);
```

**재시도는 1회만.** `_retried` 플래그가 없으면 리프레시도 401을 받을 때 무한 루프에 빠집니다.

응답 언래핑 헬퍼를 만들어 `data.data`를 반복하지 않게 합니다.

```ts
export async function get<T>(url: string, params?: object): Promise<T> {
  const res = await api.get<ApiResponse<T>>(url, { params });
  return res.data.data;
}
```

### 6-4. 모바일 우선

**모든 화면을 모바일 기준으로 먼저 만듭니다.** 참고 디자인이 전부 모바일 화면이고, 2차 앱 전환 시 그대로 재사용하기 위함입니다. Tailwind 기본 스타일을 모바일로 작성하고 `md:` 이상에서 데스크톱을 보정합니다.

선생님 화면만 예외적으로 데스크톱 활용도가 높습니다 (숙제 확인 격자). 모바일에서도 동작하되 넓은 화면에서 열이 늘어나는 방식으로 만드세요.

---

## 7. 완료 조건 (DoD)

- [ ] `docker compose up -d`로 PostgreSQL 컨테이너가 뜨고 healthcheck가 통과한다
- [ ] `./gradlew bootRun`으로 백엔드가 8080에서 실행된다
- [ ] `npm run dev`로 프론트엔드가 5173에서 실행된다
- [ ] PostgreSQL 연결이 성공하고 Flyway가 초기화된다 (테이블은 Phase 1에서 생성)
- [ ] `docker compose down` 후 다시 `up` 했을 때 데이터가 유지된다
- [ ] `docker build`로 백엔드·프론트엔드 이미지가 각각 빌드된다
- [ ] 백엔드 컨테이너 안에서 `date`가 KST를 출력한다
- [ ] 두 디렉터리에 `.dockerignore`가 있다
- [ ] `GET /api/health`가 `{"success":true,"data":"ok"}`를 반환한다
- [ ] 존재하지 않는 경로 호출 시 `{"success":false,"error":{...}}` 형태로 응답한다
- [ ] `ddl-auto`가 `validate`로 설정되어 있다
- [ ] `.env`가 `.gitignore`에 포함되어 있다
- [ ] 프론트에서 백엔드 health 엔드포인트를 CORS 오류 없이 호출한다
- [ ] `/login`, `/student`, `/parent`, `/teacher` 경로가 각각 렌더링된다 (내용은 비어 있어도 됨)

---

## 8. 하지 말 것

- 도메인 로직을 구현하지 마세요. 이 Phase는 기반 설정만입니다.
- 로컬 compose에 백엔드·프론트엔드 컨테이너를 넣지 마세요. DB만입니다. 개발 루프가 느려집니다.
- 배포용 compose(`docker-compose.prod.yml`)를 지금 만들지 마세요. Phase 8입니다. 이미지 빌드 확인까지만 합니다.
- CI 파이프라인, 모니터링, 컨테이너 오케스트레이션(k8s, ECS)을 구성하지 마세요.
- Redis, 메시지 큐, 캐시 레이어를 넣지 마세요. 200명 규모에서 불필요합니다.
- Swagger/OpenAPI 자동 문서를 필수로 넣지 마세요. API 명세는 이 문서 세트가 기준입니다.
- 엔티티를 미리 만들지 마세요. Phase 1의 DDL을 먼저 확정한 뒤 그에 맞춰 작성합니다.
