# Phase 8 — 배포 및 운영 (Docker)

**선행 조건:** Phase 7
**목표:** EC2에서 Docker Compose로 서비스가 뜨고, 도메인 + HTTPS로 접속되며, 백업이 동작한다.

---

## 1. 구성

```
사용자
  ↓ HTTPS (443)
Route 53
  ↓
EC2 t3.small ─ Docker
  ├── web           nginx (PID 1). 정적 파일 + /api 리버스 프록시 + TLS 종료
  ├── web-reloader  6시간마다 nginx -s reload (갱신 인증서 반영)
  ├── backend       Spring Boot 8080 (외부 노출 없음)
  └── certbot       인증서 갱신 전용
       ↓
   PostgreSQL (Supabase 또는 RDS · 컨테이너 아님)
       ↓
   S3 (숙제 사진, 자료실)
```

| 항목 | 사양 | 근거 |
|---|---|---|
| EC2 | t3.small (2 vCPU, 2GB) | 학생 200명. 피크는 수업 후 1~2시간 |
| 컨테이너 | 4개 | web, web-reloader, backend, certbot |
| S3 | 표준 | 리사이즈 후 연 20~30GB |

`web-reloader`는 `web`과 같은 이미지를 쓰고 PID 네임스페이스만 공유합니다.
메모리는 수 MB이고 추가 빌드도 없습니다. **nginx를 PID 1로 유지하려고 분리한 것**이며,
이유는 6절에 있습니다.

**DB는 컨테이너로 띄우지 마세요.** 같은 EC2에 Postgres 컨테이너를 올리면 백업·복구를 직접 책임져야 하고, 인스턴스가 죽으면 데이터가 함께 갑니다. 관리형 DB를 쓰는 이유가 그것입니다.

**k8s, ECS, 로드밸런서, 오토스케일링을 쓰지 마세요.** Compose 한 파일로 충분합니다.

---

## 2. 서버 준비

```bash
# Docker + Compose 플러그인
sudo apt-get update
sudo apt-get install -y ca-certificates curl
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER   # 재로그인 필요

docker --version
docker compose version
```

### IMDSv2 홉 제한 (반드시 확인)

컨테이너에서 IAM Role을 쓰려면 EC2 메타데이터에 접근해야 하는데, **기본 홉 제한이 1이라 브리지 네트워크의 컨테이너에서는 차단됩니다.**

```bash
aws ec2 modify-instance-metadata-options \
  --instance-id {인스턴스ID} \
  --http-put-response-hop-limit 2 \
  --http-tokens required
```

이걸 안 하면 S3 업로드가 자격증명 오류로 실패합니다. 원인을 찾기 어려운 항목이라 먼저 처리하세요. 대안으로 액세스 키를 넣고 싶어질 텐데, 그러지 마세요.

---

## 3. DB

| 선택지 | 장점 | 단점 |
|---|---|---|
| Supabase Postgres | 무료 티어, 자동 백업 | 무료 티어 일시 정지 정책 |
| AWS RDS | 같은 VPC, 안정적 | 월 비용 |

Spring Boot를 쓰므로 Supabase의 Auth·자동 REST API·RLS는 사용하지 않습니다. DB만 JDBC로 붙입니다.

```
DB_URL=jdbc:postgresql://{host}:5432/postgres?sslmode=require
```

**운영에서는 `sslmode=require`를 반드시 넣으세요.**

무료 티어로 시작하되 오픈 후에는 유료 플랜이나 RDS로 옮기는 것을 권합니다. 학부모가 매일 보는 서비스라 예고 없는 정지는 곤란합니다.

---

## 4. S3

### 퍼블릭 액세스 전면 차단

숙제 사진에 학생 필기와 이름이 담깁니다. 읽기·쓰기는 전부 presigned URL로만 합니다.

```
Block all public access: ON
```

### IAM Role

EC2에 Role을 붙이고 액세스 키는 서버에 두지 않습니다. 권한은 해당 버킷의 `s3:PutObject`, `s3:GetObject`, `s3:DeleteObject`만. `s3:*`를 주지 마세요.

### CORS

```json
[{
  "AllowedHeaders": ["*"],
  "AllowedMethods": ["PUT", "GET"],
  "AllowedOrigins": ["https://{도메인}"],
  "ExposeHeaders": ["ETag"],
  "MaxAgeSeconds": 3000
}]
```

`AllowedOrigins`에 `*`를 넣지 마세요.

### 구조와 수명 주기

```
submissions/{year}/{month}/{uuid}.webp
materials/{year}/{month}/{uuid}.{ext}
```

사진 보관 기간이 확정되면 `submissions/` 프리픽스에 삭제 규칙을 겁니다. **미확정이면 규칙을 만들지 마세요.** 학생 제출물이 사라지면 되돌릴 수 없습니다.

버전 관리(Versioning)는 켜세요. 실수 삭제를 복구할 수 있고 비용 증가는 미미합니다.

---

## 5. nginx 설정

`frontend/nginx.conf`는 이미지에 포함됩니다. 도메인을 하드코딩하지 말고 nginx 공식 이미지의 템플릿 기능을 쓰세요. `/etc/nginx/templates/*.conf.template`에 두면 시작 시 `envsubst`로 치환됩니다.

`frontend/templates/default.conf.template`

```nginx
server {
    listen 80;
    server_name ${DOMAIN};

    location /.well-known/acme-challenge/ { root /var/www/certbot; }
    location /healthz { access_log off; return 200 "ok\n"; }
    location / { return 301 https://$host$request_uri; }
}

server {
    listen 443 ssl;
    http2 on;
    server_name ${DOMAIN};

    ssl_certificate     /etc/letsencrypt/live/${DOMAIN}/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/${DOMAIN}/privkey.pem;

    client_max_body_size 20M;

    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header Referrer-Policy "strict-origin-when-cross-origin" always;
    add_header Strict-Transport-Security "max-age=31536000" always;

    location /api/ {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 60s;
    }

    location / {
        root /usr/share/nginx/html;
        try_files $uri $uri/ /index.html;
    }
}
```

**`try_files ... /index.html`이 SPA 라우팅 처리입니다.** 없으면 `/parent/attendance`에서 새로고침할 때 404가 납니다.

`proxy_pass http://backend:8080`의 `backend`는 Compose 서비스 이름입니다. Docker 내부 DNS가 해석합니다.

`X-Forwarded-Proto`와 `server.forward-headers-strategy: native`가 함께 있어야 Spring이 HTTPS로 인식하고 `Secure` 쿠키를 정상 처리합니다.

Dockerfile에서 `COPY nginx.conf ...` 대신 `COPY templates /etc/nginx/templates`로 바꾸세요.

---

## 6. docker-compose.prod.yml

```yaml
services:
  backend:
    image: academy-backend:${TAG:-latest}
    container_name: academy-backend
    restart: always
    env_file: /etc/academy/env
    expose:
      - "8080"
    mem_limit: 1200m
    healthcheck:
      test: ["CMD", "curl", "-fsS", "http://localhost:8080/api/health"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 60s
    logging:
      driver: json-file
      options: { max-size: "20m", max-file: "5" }

  web:
    image: academy-frontend:${TAG:-latest}
    container_name: academy-web
    restart: always
    environment:
      DOMAIN: ${DOMAIN}
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - certbot-etc:/etc/letsencrypt:ro
      - certbot-www:/var/www/certbot:ro
    depends_on:
      - backend
    healthcheck:
      test: ["CMD", "curl", "-fsS", "-o", "/dev/null", "http://localhost/healthz"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 20s
    logging:
      driver: json-file
      options: { max-size: "20m", max-file: "5" }

  # 인증서 갱신분을 반영하기 위한 주기적 reload. web과 PID 네임스페이스를 공유한다
  web-reloader:
    image: academy-frontend:${TAG:-latest}
    container_name: academy-web-reloader
    restart: always
    pid: "service:web"
    volumes:
      - certbot-etc:/etc/letsencrypt:ro
    entrypoint: >
      sh -c "trap exit TERM;
             while :; do sleep 6h & wait $${!}; nginx -s reload; done"
    depends_on:
      - web
    logging:
      driver: json-file
      options: { max-size: "5m", max-file: "2" }

  certbot:
    image: certbot/certbot
    container_name: academy-certbot
    restart: always
    volumes:
      - certbot-etc:/etc/letsencrypt
      - certbot-www:/var/www/certbot
    entrypoint: >
      sh -c "trap exit TERM;
             while :; do certbot renew --webroot -w /var/www/certbot --quiet;
             sleep 12h & wait $${!}; done"

volumes:
  certbot-etc:
  certbot-www:
```

**`logging` 설정을 빼지 마세요.** Docker 기본 json-file 드라이버는 로그를 무한히 쌓습니다. t3.small 디스크가 로그로 가득 차서 서비스가 멈추는 것은 흔한 사고입니다.

`mem_limit: 1200m`과 Dockerfile의 `MaxRAMPercentage=70`이 짝입니다. 2GB 중 backend에 1.2GB를 주고 JVM은 그 70%인 약 840MB를 힙으로 씁니다. 나머지는 nginx와 OS 몫입니다.

`restart: always`로 서버 재부팅 시 자동 복구됩니다. systemd 설정이 따로 필요 없습니다.

### nginx를 PID 1로 두세요 — reload 루프는 별도 컨테이너로

**`web` 컨테이너 안에서 nginx를 백그라운드로 돌리지 마세요.**

```yaml
# ❌ 이렇게 하면 nginx가 죽어도 컨테이너는 살아 있습니다
command: >
  sh -c "nginx -g 'daemon off;' &
         while :; do sleep 6h; nginx -s reload; done"
```

`&`로 백그라운드에 보내면 PID 1은 while 루프입니다. **nginx가 죽어도 루프가 계속 돌아
컨테이너는 `running` 상태를 유지합니다.** `restart: always`가 발동하지 않고,
`docker compose ps`는 멀쩡해 보이는데 사이트는 내려가 있습니다.

nginx를 PID 1(`ENTRYPOINT` 기본값)로 두면 nginx가 죽는 즉시 컨테이너가 종료되고
Docker가 재시작합니다. reload 루프는 `pid: "service:web"`으로 네임스페이스를 공유하는
별도 컨테이너에서 돌립니다. 같은 이미지를 쓰므로 추가 빌드는 없습니다.

**`web`에 healthcheck를 붙이는 것이 핵심입니다.** 프로세스가 살아 있는 것과
요청에 응답하는 것은 다릅니다. 설정 오류로 nginx가 뜬 채 502만 뱉는 상태도 잡아야 합니다.

`web`은 `restart: always` + healthcheck 조합이라, 컨테이너가 unhealthy가 되어도
자동으로 재시작하지는 않습니다(Compose 기본 동작). 상태 확인용이며,
`docker compose ps`에서 unhealthy가 보이면 사람이 개입합니다. 200명 규모에서
자동 복구 오케스트레이션을 넣을 이유는 없습니다.

---

## 7. HTTPS 최초 발급

**닭과 달걀 문제가 있습니다.** nginx는 인증서가 없으면 443 설정에서 기동에 실패하고, 인증서는 nginx가 떠 있어야 발급됩니다. 최초 1회만 standalone으로 받으세요.

```bash
# 1) 80 포트를 비운 상태에서 최초 발급
docker run --rm -p 80:80 \
  -v academy_certbot-etc:/etc/letsencrypt \
  -v academy_certbot-www:/var/www/certbot \
  certbot/certbot certonly --standalone \
  -d {도메인} --agree-tos -m {이메일} --no-eff-email

# 2) 이후 정상 기동
docker compose -f docker-compose.prod.yml up -d
```

볼륨 이름 앞의 `academy_`는 Compose 프로젝트 접두사입니다. `docker volume ls`로 실제 이름을 확인하세요.

발급 후 갱신은 `certbot` 컨테이너가 12시간마다 자동으로 확인합니다. `web` 컨테이너가 6시간마다 reload하므로 갱신분이 반영됩니다.

```bash
docker compose exec certbot certbot renew --dry-run
```

**갱신 테스트를 반드시 돌리세요.** 90일 뒤 인증서가 만료되면 학부모가 접속하지 못합니다.

---

## 8. 환경변수

`/etc/academy/env`에 두고 권한을 제한합니다. **이미지에 굽지 마세요.**

```
DB_URL=jdbc:postgresql://{host}:5432/postgres?sslmode=require
DB_USERNAME=
DB_PASSWORD=
JWT_SECRET=
S3_BUCKET=
AWS_REGION=ap-northeast-2
CORS_ORIGINS=https://{도메인}
```

```bash
sudo chmod 600 /etc/academy/env
```

Compose용 `DOMAIN`과 `TAG`는 배포 디렉터리의 `.env`에 둡니다.

```
DOMAIN=academy.example.com
TAG=2026-07-27
```

**운영 `JWT_SECRET`은 개발용과 다른 값을 쓰세요.** 개발 시크릿이 커밋 이력에 남아 있으면 토큰을 위조할 수 있습니다.

AWS 액세스 키는 넣지 않습니다 (IAM Role).

---

## 9. 배포 절차

t3.small에서 이미지를 빌드하면 메모리가 부족해 자주 실패합니다. **로컬이나 CI에서 빌드해 레지스트리로 올리는 방식을 권합니다** (ECR 또는 GHCR).

```bash
# 로컬
TAG=$(date +%Y%m%d-%H%M)
docker build -t {registry}/academy-backend:$TAG  ./backend
docker build -t {registry}/academy-frontend:$TAG ./frontend \
  --build-arg VITE_API_BASE_URL=/api
docker push {registry}/academy-backend:$TAG
docker push {registry}/academy-frontend:$TAG

# 서버
ssh ec2
cd /opt/academy
echo "TAG=$TAG" >> .env     # 또는 값 교체
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
docker image prune -f
```

**`latest` 태그를 쓰지 마세요.** 롤백할 때 어떤 이미지로 돌아가야 하는지 알 수 없습니다. 날짜나 커밋 해시를 태그로 쓰면 `TAG`만 이전 값으로 바꿔 재기동하면 됩니다.

Flyway가 backend 기동 시 마이그레이션을 실행합니다. **배포 전 DB 백업을 먼저 하세요.** 마이그레이션은 롤백되지 않습니다.

```bash
docker compose logs -f backend       # 기동 확인
docker compose ps                    # 헬스 상태 확인
```

---

## 10. 백업

### DB

| 항목 | 값 |
|---|---|
| 주기 | 매일 1회 |
| 보관 | 최소 7일 |

Supabase·RDS 자동 백업을 켜되 **복원을 한 번 실제로 테스트하세요.** 백업이 있다고 믿었는데 복원이 안 되는 경우가 실제로 있습니다.

### S3

버전 관리를 켜둡니다. 실수로 삭제한 제출 사진을 복구할 수 있습니다.

### 컨테이너

이미지와 Compose 파일이 곧 서버 상태이므로 별도 백업이 필요 없습니다. **Compose 파일과 nginx 템플릿은 Git에 두세요.** 서버에서 직접 편집하고 커밋하지 않으면 인스턴스가 죽을 때 함께 사라집니다.

`/etc/academy/env`는 Git에 올리면 안 됩니다. 별도 안전한 곳에 보관하세요.

---

## 11. 로그

애플리케이션은 **파일이 아니라 표준출력**으로 로그를 냅니다. 컨테이너 환경에서는 이게 맞고, Docker가 수집합니다.

```yaml
logging:
  level:
    root: INFO
    com.njwenglish: INFO
    org.hibernate.SQL: WARN
```

`logging.file.name` 설정을 넣지 마세요. 컨테이너 안 파일은 재시작하면 사라집니다.

```bash
docker compose logs -f backend
docker compose logs --since 1h backend
```

**로그에 개인정보를 남기지 마세요.** 학생 이름, 전화번호, 비밀번호, 토큰이 출력되는 코드가 없는지 배포 전 확인하세요. 디버깅 중 넣은 `log.info(request)` 같은 코드가 남아 있기 쉽습니다.

`org.hibernate.SQL`을 `DEBUG`로 두면 디스크가 빠르게 찹니다. 운영은 `WARN`입니다.

---

## 12. 완료 조건 (DoD) — 오픈 전 점검

### 기능

- [ ] 세 역할로 로그인해 전 화면을 순회했을 때 오류가 없다
- [ ] 실제 휴대폰(iOS·Android)에서 정상 동작한다
- [ ] 숙제 사진 업로드가 모바일 데이터 환경에서 성공한다
- [ ] YouTube 영상이 모바일에서 재생된다
- [ ] 새로고침 시 로그인 상태가 유지된다
- [ ] SPA 경로를 직접 입력해 새로고침해도 404가 나지 않는다

### Docker

- [ ] `docker compose ps`에서 4개 컨테이너가 모두 healthy/running이다
- [ ] **`web`에 healthcheck가 있고 `/healthz`가 200을 반환한다**
- [ ] **`web` 컨테이너에서 nginx가 PID 1이다** (`docker compose exec web ps -o pid,comm` 확인)
- [ ] **nginx를 죽이면 `web` 컨테이너가 종료되고 자동 재시작된다**
      (`docker compose exec web nginx -s stop` 후 `docker compose ps`로 확인)
- [ ] `docker compose down && up -d` 후 정상 복구된다
- [ ] **EC2 재부팅 후 컨테이너가 자동 기동된다** (`restart: always` 확인)
- [ ] backend 컨테이너에서 `date`가 KST를 출력한다
- [ ] backend 컨테이너가 비루트(`app`)로 실행된다
- [ ] `logging` 옵션이 적용되어 로그 파일이 무한히 커지지 않는다
- [ ] 이미지 태그가 `latest`가 아니라 날짜/해시다
- [ ] IMDSv2 홉 제한이 2로 설정되어 컨테이너에서 S3 업로드가 성공한다
- [ ] 이미지에 시크릿이 포함되지 않았다 (`docker history`로 확인)

### 보안

- [ ] HTTPS로 접속되고 HTTP가 리다이렉트된다
- [ ] backend가 `expose`만 되어 있고 호스트 포트로 노출되지 않았다
- [ ] 보안 그룹에서 22, 80, 443만 열려 있다
- [ ] S3 버킷이 완전히 비공개다
- [ ] EC2와 컨테이너에 AWS 액세스 키가 없다
- [ ] 운영 `JWT_SECRET`이 개발용과 다르다
- [ ] Refresh 쿠키에 `Secure`, `HttpOnly`가 설정되어 있다
- [ ] `.env`, 시크릿이 Git에 커밋되지 않았다 (이력 포함)
- [ ] **학부모 A의 토큰으로 학부모 B의 자녀 데이터를 조회해 403을 확인했다**
- [ ] 로그에 개인정보가 남지 않는다
- [ ] 에러 응답에 스택트레이스가 노출되지 않는다

### 운영

- [ ] DB 백업이 동작하고 **복원 테스트를 완료했다**
- [ ] S3 버전 관리가 켜져 있다
- [ ] 인증서 자동 갱신이 확인되었다 (`renew --dry-run`)
- [ ] Compose 파일과 nginx 템플릿이 Git에 있다
- [ ] 롤백 절차를 한 번 연습했다 (`TAG` 이전 값으로 재기동)

### 데이터

- [ ] 선생님이 `POST /api/teacher/schools`로 실제 학교를 등록했다 (시드에는 학교가 없다)
- [ ] 학교를 먼저 등록한 뒤 반을 만들었다 (`class_rooms.school_id`가 필수)
- [ ] 개발용 테스트 데이터가 운영 DB에 없다
- [ ] 선생님 계정 비밀번호가 변경되었다
- [ ] 개인정보처리방침·이용약관이 실제 내용으로 게시되었다
- [ ] **법정대리인 동의 방식이 확정되었고, 확정안대로 처리되고 있다** (`spec/requirements_for_client.md` 5-1)
- [ ] **퇴원생 데이터 보관 기간이 확정되었다** (5-2). 현재는 무기한 보관이며 자동 삭제가 없다

### 학원장 확정 사항

- [ ] 결제 기능이 구현되지 않았다
- [ ] 영상이 YouTube 링크 방식으로 동작한다
- [ ] SMS·알림톡 발송 코드가 없다
- [ ] 공부 시간 기록·랭킹 기능이 없다
- [ ] 사진 보관 기간이 확정되었다면 S3 수명 주기에 반영되었다

---

## 13. 오픈 후 관찰

| 항목 | 확인 | 대응 |
|---|---|---|
| 학부모 연결률 | T-1의 `unlinkedParentCount` | 미연결 학생 코드 재발급 |
| S3 용량 증가 | 월별 사용량 | 예상보다 빠르면 리사이즈 설정 재확인 |
| 컨테이너 메모리 | `docker stats` | 부족하면 `mem_limit` 조정 또는 인스턴스 상향 |
| 디스크 | `df -h`, `docker system df` | `docker image prune` 주기 실행 |
| 재시작 반복 | `docker compose ps`의 restart count | 헬스체크 실패 원인 로그 확인 |

`docker system prune -a`는 사용 중이 아닌 이미지를 전부 지웁니다. **롤백 대상 이미지까지 사라지므로 배포 직후에는 쓰지 마세요.** `docker image prune -f`(dangling만)로 충분합니다.

---

## 14. 하지 말 것

- **운영 DB를 컨테이너로 띄우지 마세요.** 인스턴스가 죽으면 데이터가 함께 갑니다. 관리형 DB만 씁니다.
- k8s, ECS, 로드밸런서, 오토스케일링, CDN을 넣지 마세요. 200명에 Compose 한 파일이면 충분합니다.
- 이미지 태그에 `latest`를 쓰지 마세요. 롤백할 대상이 사라집니다.
- EC2에 AWS 액세스 키를 두지 마세요. IAM Role만 씁니다.
- `docker system prune -a`를 배포 직후에 실행하지 마세요. 롤백 대상 이미지까지 지웁니다.
- `logging` 옵션을 빼지 마세요. 로그가 디스크를 채워 서비스가 멈춥니다.
- **`web` 컨테이너에서 nginx를 `&`로 백그라운드에 보내지 마세요.** nginx가 죽어도 컨테이너가 살아 있어서 `restart: always`가 발동하지 않습니다. 사이트는 내려갔는데 `docker compose ps`는 정상으로 보입니다.
- JVM에 `-Xmx` 고정값을 주지 마세요. `-XX:MaxRAMPercentage`를 씁니다.
- 사진 보관 기간이 확정되기 전에 S3 수명 주기 삭제 규칙을 만들지 마세요. 제출물은 복구되지 않습니다.
- APM, ELK, Prometheus 같은 모니터링 스택을 올리지 마세요. `docker stats`와 로그로 충분합니다.
- 스테이징 환경을 따로 만들지 마세요. 로컬 Compose가 그 역할입니다.

---

## 15. 2차(앱) 이관 참고

웹을 모바일 우선으로 만들었으므로 백엔드 API 전체와 화면 구조가 재사용됩니다. 컨테이너 구성도 그대로 유지됩니다.

앱에서 추가 검토할 항목입니다.

| 항목 | 내용 |
|---|---|
| 공부 시간 자동 기록 | 백그라운드 측정 가능. 도입 여부 재검토 |
| 숙제 미제출 독려 알림 | 푸시. `submissions` 미제출 목록을 그대로 대상자로 사용 |
| 출석·피드백 알림 | 푸시 |
| 반 친구 익명 랭킹 | 공부 시간 도입 시 함께 검토 |

**웹 단계에서 이 기능들을 위한 테이블이나 코드를 미리 만들지 마세요.** 앱 요구사항이 확정된 뒤 설계하는 것이 맞습니다.
