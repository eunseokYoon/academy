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

ECR 또는 GHCR에 `academy-backend`, `academy-frontend` 리포지토리를 만든다.
**t3.small에서 빌드하면 메모리 부족으로 자주 실패한다.** 로컬/CI에서 빌드해 올린다.

---

## 2. 최초 1회 — 서버 설정

```bash
sudo mkdir -p /opt/academy /etc/academy
# 리포에서 docker-compose.prod.yml 을 /opt/academy 로 복사

# 시크릿
sudo cp deploy/academy.env.example /etc/academy/env
sudo vi /etc/academy/env          # 값 채우기
sudo chmod 600 /etc/academy/env

# compose 변수
cd /opt/academy
cat > .env <<'EOF'
DOMAIN=academy.example.com
REGISTRY=ghcr.io/your-account
TAG=20260730-1420
EOF
```

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

**`--platform linux/amd64`를 반드시 붙여라.** 서버가 t2(x86_64)인데 Apple Silicon 맥의
기본 빌드 결과물은 arm64다. 빼먹으면 서버의 `docker pull`이 `no matching manifest`로 실패한다.

맥에서는 QEMU 에뮬레이션으로 빌드되므로 네이티브보다 느리다(특히 backend의 Gradle 단계).
빌드가 오래 걸리는 것은 정상이며, 서버 성능과는 무관하다.

```bash
# 로컬
TAG=$(date +%Y%m%d-%H%M)
REGISTRY=ghcr.io/your-account
docker build --platform linux/amd64 -t $REGISTRY/academy-backend:$TAG  ./backend
docker build --platform linux/amd64 -t $REGISTRY/academy-frontend:$TAG ./frontend \
  --build-arg VITE_API_BASE_URL=/api
docker push $REGISTRY/academy-backend:$TAG
docker push $REGISTRY/academy-frontend:$TAG

# 서버 — 배포 전 DB 백업부터. Flyway 마이그레이션은 롤백되지 않는다.
cd /opt/academy
sed -i "s/^TAG=.*/TAG=$TAG/" .env
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
docker compose -f docker-compose.prod.yml logs -f backend
docker image prune -f        # dangling만. -a 를 붙이면 롤백 대상까지 지운다
```

### 롤백

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
