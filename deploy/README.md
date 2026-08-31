# 배포 운영 문서

EC2 한 대 + Docker Compose. 컨테이너 4개(`web`, `web-reloader`, `backend`, `certbot`).
DB는 관리형(Supabase/RDS), 파일은 S3.

k8s·ECS·로드밸런서·오토스케일링·CDN·모니터링 스택을 넣지 마라. 학생 200명이다.

```
사용자 ─HTTPS(443)→ Route 53 → EC2 t3.small
                                 ├── web           nginx(PID 1) · 정적 + /api 프록시 + TLS
                                 ├── web-reloader  6시간마다 nginx에 HUP
                                 ├── backend       Spring Boot 8080 (expose만)
                                 └── certbot       12시간마다 갱신 확인
                                        ↓
                                 PostgreSQL (관리형) · S3
```

## 배포 경로가 둘이다

| | 무엇 | 어떻게 | 롤백 |
|---|---|---|---|
| **backend** | 도커 이미지 | 로컬 빌드 → 레지스트리 push → 서버 pull | `.env`의 `TAG` 되돌리기 |
| **web(화면)** | 이미지 없음 | 로컬 `npm run build` → `dist`를 rsync → 심볼릭 링크 전환 | `current` 링크 되돌리기 |

**화면만 고쳤으면 `deploy/deploy-web.sh` 하나면 끝난다.** 이미지도 레지스트리도 안 거치고
`docker compose`도 안 건드린다. nginx는 요청마다 `current` 링크를 다시 풀기 때문에 reload도 없다.

`web` 컨테이너는 공식 `nginx:1.27-alpine`을 그대로 쓴다. 설정(`nginx/templates`)과
화면(`web/`)이 전부 마운트라 굽는 이미지가 없다.

---

## 1. 최초 1회 — AWS 콘솔 작업

### 1-1. EC2

**t2.small (1 vCPU / 2GB, x86_64)**, Ubuntu 24.04 LTS, 볼륨 30GB gp3.
AMI 아키텍처는 기본값 `64비트(x86)` 그대로 둔다.

**`고급 세부 정보` → `크레딧 사양`을 `무제한`으로 바꿔라.** t2는 기본이 `표준`이라
CPU 크레딧이 바닥나면 베이스라인으로 **강제 스로틀**된다 — 수업 후 피크에 이게 걸리면
학부모 화면이 멈춘다. 무제한이면 스로틀 대신 소액이 과금된다.

**메모리를 아끼지 마라.** `micro`(1GB) 계열은 JVM 힙만으로도 부족해서 피크에 OOM Kill이 난다.

보안 그룹 인바운드는 **22, 80, 443만.** 8080·5432를 열지 마라. SSH 소스는 `0.0.0.0/0`이 아니라 내 IP다.

퍼블릭 IPv4는 연결 여부와 무관하게 시간당 과금된다(월 $3~4). 어차피 내는 값이니
**탄력적 IP를 할당해 연결해라** — 중지·시작으로 IP가 바뀌면 도메인과 인증서 갱신이 같이 죽는다.

```bash
sudo apt-get update && sudo apt-get install -y ca-certificates curl
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER   # 재로그인 필요
docker compose version
```

### 1-2. IMDSv2 홉 제한 (먼저 하라)

브리지 네트워크의 컨테이너에서 IAM Role을 쓰려면 홉 제한이 2여야 한다.
**기본값 1이면 S3 업로드가 자격증명 오류로 실패하고, 원인을 찾기 어렵다.**

```bash
aws ec2 modify-instance-metadata-options \
  --instance-id {인스턴스ID} \
  --http-put-response-hop-limit 2 \
  --http-tokens required
```

대안으로 액세스 키를 넣고 싶어질 텐데, 그러지 마라.

### 1-3. IAM Role → EC2에 연결

정책은 해당 버킷 하나에 세 액션만. `s3:*`를 주지 마라.

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"],
    "Resource": "arn:aws:s3:::{버킷명}/*"
  }]
}
```

### 1-4. S3 버킷 (ap-northeast-2)

| 항목 | 값 |
|---|---|
| Block all public access | **ON** (전면 차단) |
| Versioning | **ON** — 실수 삭제 복구용, 비용 증가는 미미 |
| 수명 주기 | 아래 규칙 (제출물 12개월) |

읽기·쓰기는 전부 presigned URL이다. 버킷 정책으로 공개 읽기를 허용하지 마라.

**버킷은 반드시 `AWS_REGION`과 같은 리전(ap-northeast-2)에 만들어라.** 다른 리전이면
presigned URL이 서명 불일치로 실패한다.

### 프리픽스 3개 (코드 기준)

| 프리픽스 | 내용 | 자동 삭제 |
|---|---|---|
| `submissions/{y}/{m}/` | 숙제 제출 사진·영상 | **12개월** |
| `materials/{y}/{m}/` | 자료실 (선생님 교재) | **안 함** |
| `online-tests/{y}/{m}/` | 온라인 테스트 해설지 | **안 함** |

`09_phase8_deploy.md` 4절에는 앞의 두 개만 적혀 있다. `online-tests/`가 빠져 있으니
"오래된 것 전부 삭제" 같은 규칙을 걸면 해설지가 같이 날아간다.

### 수명 주기 규칙 — 제출물 12개월

```json
{
  "Rules": [
    {
      "ID": "submissions-expire-12mo",
      "Status": "Enabled",
      "Filter": { "Prefix": "submissions/" },
      "Expiration": { "Days": 365 },
      "NoncurrentVersionExpiration": { "NoncurrentDays": 30 },
      "AbortIncompleteMultipartUpload": { "DaysAfterInitiation": 7 }
    },
    {
      "ID": "submissions-clean-delete-markers",
      "Status": "Enabled",
      "Filter": { "Prefix": "submissions/" },
      "Expiration": { "ExpiredObjectDeleteMarker": true }
    }
  ]
}
```

```bash
aws s3api put-bucket-lifecycle-configuration \
  --bucket {버킷명} --lifecycle-configuration file://lifecycle.json
```

세 가지를 틀리기 쉽다.

- **`Prefix`는 `submissions/`만.** `materials/`는 선생님 교재이고 개인정보가 아니다. 지우면 복구되지 않는다.
- **버전 관리가 켜져 있으므로 `NoncurrentVersionExpiration`이 없으면 용량이 줄지 않는다.**
  `Expiration`만 걸면 현재 버전에 삭제 마커만 씌워지고 이전 버전은 그대로 과금된다.
- `Expiration.Days`와 `ExpiredObjectDeleteMarker`는 **한 규칙에 같이 못 쓴다.** 그래서 규칙이 2개다.

`AbortIncompleteMultipartUpload`는 실패한 영상 업로드 조각이 조용히 과금되는 것을 막는다.

**아직 알려진 빈틈이 있다.** 12개월이 지나면 S3 객체는 사라지지만 `submission_photos` 행은
남으므로, 학생의 숙제 상세(S-4)에서 presigned GET이 404가 되고 이미지가 깨진다.
만료된 제출물을 화면에서 어떻게 보여줄지 정한 뒤 처리해야 한다.

CORS (`AllowedOrigins`에 `*`를 넣지 마라):

```json
[{
  "AllowedHeaders": ["*"],
  "AllowedMethods": ["PUT", "GET"],
  "AllowedOrigins": ["https://{도메인}"],
  "ExposeHeaders": ["ETag"],
  "MaxAgeSeconds": 3000
}]
```

### 1-5. DB (Supabase 또는 RDS)

컨테이너로 띄우지 마라. 인스턴스가 죽으면 데이터가 같이 간다.

- 자동 백업: 매일 1회, 최소 7일 보관
- **복원을 한 번 실제로 테스트하라.** 백업이 있다고 믿었는데 복원이 안 되는 경우가 있다
- 접속 URL에 `?sslmode=require`
- 무료 티어로 시작해도 되지만, 오픈 후에는 유료 플랜이나 RDS로 옮겨라 (예고 없는 일시 정지)

### 1-6. Route 53

A 레코드 → EC2 퍼블릭 IP(가능하면 Elastic IP). 인증서 발급 전에 DNS가 먼저 붙어 있어야 한다.

### 1-7. 이미지 레지스트리

ECR 또는 GHCR에 **`academy-backend` 하나만** 만든다. 화면은 이미지가 아니라 rsync라
`academy-frontend` 리포지토리는 필요 없다.

**t3.small에서 빌드하면 메모리 부족으로 자주 실패한다.** 로컬/CI에서 빌드해 올린다.

---

## 2. 최초 1회 — 서버 설정

```bash
# 호스트 시간대. 컨테이너는 각자 TZ를 갖고 있어서 이게 없어도 서비스는 정상이지만,
# `docker ps`나 dmesg를 볼 때 시각이 UTC로 나와 사고 조사 때 헷갈린다.
sudo timedatectl set-timezone Asia/Seoul

sudo mkdir -p /opt/academy/{nginx/templates,web/releases} /etc/academy
# 배포 계정이 web/ 아래에 rsync 할 수 있어야 한다
sudo chown -R "$USER":"$USER" /opt/academy

# 시크릿
sudo cp deploy/academy.env.example /etc/academy/env
sudo vi /etc/academy/env          # 값 채우기
sudo chmod 600 /etc/academy/env

# compose 변수. TAG는 backend 전용이다 — 화면은 TAG를 쓰지 않는다.
cd /opt/academy
cat > .env <<'EOF'
DOMAIN=academy.example.com
REGISTRY=ghcr.io/your-account
TAG=20260730-1420
EOF
```

리포에서 서버로 올릴 파일 둘 (로컬에서):

```bash
scp docker-compose.prod.yml {user}@{host}:/opt/academy/
scp deploy/nginx/templates/default.conf.template \
    {user}@{host}:/opt/academy/nginx/templates/
```

**`nginx/templates/`가 비어 있으면 nginx가 기본 환영 페이지를 띄운다.** 인증서도 프록시도
없는 상태라 `/api`가 통째로 404다 — 첫 배포에서 가장 흔한 사고다.

### HTTPS 최초 발급 (닭과 달걀)

nginx는 인증서가 없으면 443 설정에서 기동에 실패하고, 인증서는 nginx가 떠 있어야 발급된다.
**최초 1회만 standalone으로 받는다.**

```bash
# 볼륨을 먼저 만든다 (프로젝트 접두사 확인: docker volume ls)
docker volume create academy_certbot-etc
docker volume create academy_certbot-www

docker run --rm -p 80:80 \
  -v academy_certbot-etc:/etc/letsencrypt \
  -v academy_certbot-www:/var/www/certbot \
  certbot/certbot certonly --standalone \
  -d {도메인} --agree-tos -m {이메일} --no-eff-email
```

이후 갱신은 `certbot` 컨테이너가 12시간마다 확인하고, `web-reloader`가 6시간마다 반영한다.

```bash
docker compose -f docker-compose.prod.yml exec certbot certbot renew --dry-run
```

**갱신 테스트를 반드시 돌려라.** 90일 뒤 만료되면 학부모가 접속하지 못한다.

---

## 3. 배포

**둘은 서로 독립이다.** 화면만 고쳤으면 3-1만, API만 고쳤으면 3-2만 돌린다.
둘 다 바뀌었으면 **3-2(backend) 먼저**다 — 새 화면이 아직 없는 API를 부르는 것보다
옛 화면이 이미 있는 API를 부르는 쪽이 안전하다.

### 3-1. 화면 (web)

```bash
deploy/deploy-web.sh {user}@{host}
# 키 파일이 필요하면:  deploy/deploy-web.sh {user}@{host} -i ~/.ssh/academy.pem
```

스크립트가 빌드 → `releases/{타임스탬프}/`로 rsync → `current` 링크 전환 → 오래된 릴리스 정리
순으로 돈다. 링크를 바꾸는 순간부터 새 화면이고, **반쯤 복사된 상태가 서비스되는 구간이 없다.**
`docker compose`도 nginx reload도 필요 없다.

`VITE_API_BASE_URL`을 손으로 넘기지 마라. `frontend/.env.production`이 `/api`로 고정한다.
그 파일이 없으면 개발용 `frontend/.env`가 이겨서 **번들에 `http://localhost:8080/api`가 박힌다** —
빌드도 배포도 성공하고 학부모 폰에서만 빈 화면이 뜬다.

nginx 설정(`deploy/nginx/templates/`)을 고쳤으면 그건 별개다. 서버에 다시 올린 뒤
`docker compose -f docker-compose.prod.yml up -d --force-recreate web`으로 **재생성**해야 한다.
`${DOMAIN}` 치환이 컨테이너 시작 때 한 번만 일어나므로 reload로는 반영되지 않는다.

### 3-2. API (backend)

**`--platform linux/amd64`를 반드시 붙여라.** 서버가 t2(x86_64)인데 Apple Silicon 맥의
기본 빌드 결과물은 arm64다. 빼먹으면 서버의 `docker pull`이 `no matching manifest`로 실패한다.

맥에서는 QEMU 에뮬레이션으로 빌드되므로 네이티브보다 느리다(Gradle 단계가 특히 그렇다).
빌드가 오래 걸리는 것은 정상이며, 서버 성능과는 무관하다.

```bash
# 로컬
TAG=$(date +%Y%m%d-%H%M)
REGISTRY=ghcr.io/your-account
docker build --platform linux/amd64 -t $REGISTRY/academy-backend:$TAG ./backend
docker push $REGISTRY/academy-backend:$TAG

# 서버 — 배포 전 DB 백업부터. Flyway 마이그레이션은 롤백되지 않는다.
cd /opt/academy
sed -i "s/^TAG=.*/TAG=$TAG/" .env
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
docker compose -f docker-compose.prod.yml logs -f backend
docker image prune -f        # dangling만. -a 를 붙이면 롤백 대상까지 지운다
```

### 롤백

**화면** — 서버에서 링크만 되돌린다. 재빌드도 재시작도 없다.

```bash
cd /opt/academy/web
ls -1dt releases/*/                 # 최근 5개가 남아 있다
ln -sfn releases/{이전} current.tmp && mv -Tf current.tmp current
```

`ln -sfn` 하나로 끝내지 마라. 기존 링크를 지웠다가 다시 만드는 사이에 들어온 요청이 404가 된다.
임시 이름으로 만든 뒤 `mv -Tf`로 갈아끼우는 것이 원자적이다.

**API** — 태그를 되돌린다.

```bash
sed -i "s/^TAG=.*/TAG={이전값}/" .env
docker compose -f docker-compose.prod.yml up -d
```

스키마 마이그레이션이 포함된 배포는 이미지만 되돌려도 복구되지 않는다. DB 백업이 유일한 수단이다.

---

## 4. 점검

```bash
docker compose -f docker-compose.prod.yml ps                 # 4개 healthy/running
docker compose -f docker-compose.prod.yml exec web ps -o pid,comm   # PID 1 = nginx
docker compose -f docker-compose.prod.yml exec backend date          # KST
docker compose -f docker-compose.prod.yml exec backend id            # app (비루트)
docker stats --no-stream
df -h && docker system df
```

화면이 제대로 물렸는지 (web은 마운트로 도니까 이 셋이 전부다):

```bash
# 컨테이너가 보는 릴리스 = 서버의 current 와 같아야 한다
readlink /opt/academy/web/current
docker compose -f docker-compose.prod.yml exec web ls /srv/web/current/index.html

# 설정이 치환됐는지. ${DOMAIN}이 글자 그대로 남아 있으면 .env를 안 읽은 것이다
docker compose -f docker-compose.prod.yml exec web \
  grep -m2 server_name /etc/nginx/conf.d/default.conf
```

**배포한 화면이 실제로 떴는지는 브라우저 캐시 때문에 헷갈린다.** `index.html`은 캐시하지
않지만 `/assets/`는 1년 immutable이라, 파일명 해시가 바뀐 새 번들을 받는지로 확인하는 게 확실하다.

```bash
curl -s https://{도메인} | grep -o '/assets/index-[^"]*\.js'
```

**t2는 CPU 크레딧을 봐라.** CloudWatch의 `CPUCreditBalance`가 0에 붙어 있으면
버스트 여력이 없다는 뜻이다 — 응답이 느려지는데 로그에는 아무 이상이 없다.
반복되면 인스턴스 상향(t3.small 이상)을 검토해라.

nginx가 죽으면 컨테이너가 종료되고 자동 재시작되는지 확인:

```bash
docker compose -f docker-compose.prod.yml exec web nginx -s stop
docker compose -f docker-compose.prod.yml ps    # 재시작 확인
```

---

## 5. 백업

| 대상 | 방식 |
|---|---|
| DB | 관리형 자동 백업 (매일, 7일 이상) + **복원 테스트 완료** |
| S3 | 버전 관리 ON |
| 컨테이너 | 이미지 + compose 파일이 곧 서버 상태. 별도 백업 불필요 |
| `/etc/academy/env` | **Git 금지.** 안전한 곳에 따로 보관 |

`docker-compose.prod.yml`과 `frontend/templates/`는 Git에 있다. 서버에서 직접 편집하고
커밋하지 않으면 인스턴스가 죽을 때 함께 사라진다.
