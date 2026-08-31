#!/usr/bin/env bash
#
# 화면(프론트) 배포. 로컬에서 빌드해 서버로 올리고 심볼릭 링크만 바꾼다.
# 이미지를 굽지 않으므로 backend 배포와 완전히 분리돼 있다 — 디자인만 고친 날은 이것만 돌린다.
#
#   사용법:  deploy/deploy-web.sh ubuntu@academy.example.com
#            deploy/deploy-web.sh ubuntu@1.2.3.4 -i ~/.ssh/academy.pem
#
# 하는 일:
#   1. frontend를 빌드한다 (.env.production 덕분에 VITE_API_BASE_URL=/api 가 박힌다)
#   2. releases/{타임스탬프}/ 로 rsync 한다 — 아직 아무도 이 파일을 보지 않는다
#   3. current 링크를 원자적으로 바꾼다 — 이 순간부터 새 화면이다
#   4. 오래된 릴리스를 정리한다 (최근 5개만 남긴다)
#
# nginx reload는 필요 없다. root가 current를 지나가고 open_file_cache가 꺼져 있어
# 요청마다 경로를 다시 푼다.
set -euo pipefail

REMOTE="${1:-}"
if [ -z "$REMOTE" ]; then
  echo "사용법: $0 user@host [ssh 추가옵션...]" >&2
  exit 1
fi
shift
SSH_OPTS=("$@")

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_DIR=/opt/academy
WEB_DIR="$APP_DIR/web"
KEEP=5
REL="$(date +%Y%m%d-%H%M%S)"

echo "==> 1/4 빌드"
# npm ci가 아니라 npm run build다. 의존성이 바뀌었으면 먼저 npm ci를 직접 돌려라 —
# 배포 스크립트가 node_modules를 통째로 다시 깔면 디자인 한 줄 고칠 때마다 몇 분이 든다.
(cd "$ROOT/frontend" && npm run build)

DIST="$ROOT/frontend/dist"
# index.html이 없으면 빌드가 실패했는데 set -e를 빠져나간 것이다. 빈 디렉터리를 올려
# 서비스를 비우는 것보다 여기서 멈추는 게 낫다.
[ -f "$DIST/index.html" ] || { echo "빌드 산출물이 없다: $DIST/index.html" >&2; exit 1; }

echo "==> 2/4 업로드  $REL"
# rsync는 목적지의 마지막 한 칸만 만든다. releases/ 가 없으면 그냥 실패하므로 먼저 만든다.
ssh "${SSH_OPTS[@]}" "$REMOTE" "mkdir -p '$WEB_DIR/releases/$REL'"
# --delete는 쓰지 않는다. 매번 빈 새 디렉터리로 보내므로 지울 것이 없고,
# 경로를 잘못 짚었을 때 남의 디렉터리를 비우는 사고만 남는다.
#
# 옵션은 -avz 까지만 쓴다. macOS는 GNU rsync가 아니라 openrsync를 싣고 있어서
# --info=stats1 같은 건 "unrecognized option"으로 죽는다. 여기 뭔가 더 붙일 거면
# 맥에서 먼저 확인해라 — 이 스크립트는 맥에서 도는 게 정상 경로다.
rsync -avz -e "ssh ${SSH_OPTS[*]}" \
  "$DIST/" "$REMOTE:$WEB_DIR/releases/$REL/"

echo "==> 3/4 전환"
# 링크는 반드시 상대 경로다. 절대 경로로 걸면 컨테이너 안에서 /opt/academy가 없어 깨진다.
# ln -sfn 대신 심볼릭 링크를 임시 이름으로 만든 뒤 mv -T로 갈아끼운다 — 이쪽이 원자적이라
# "링크가 잠깐 없는" 순간이 생기지 않는다(그 순간 요청은 404다).
#
# -T가 핵심이다. 없으면 mv가 current(디렉터리를 가리키는 링크)를 따라가서 링크를 그 디렉터리
# 안으로 옮겨 버린다 — releases/{옛것}/current.tmp 가 생기고 current는 그대로다.
# 배포가 성공했다고 나오는데 화면이 안 바뀐다. 서버는 Ubuntu(GNU coreutils)라 -T가 있다.
ssh "${SSH_OPTS[@]}" "$REMOTE" bash -euo pipefail -s -- "$WEB_DIR" "$REL" "$KEEP" <<'REMOTE_SCRIPT'
WEB_DIR="$1"; REL="$2"; KEEP="$3"
cd "$WEB_DIR"
[ -f "releases/$REL/index.html" ] || { echo "업로드가 덜 됐다: releases/$REL/index.html 없음" >&2; exit 1; }
ln -sfn "releases/$REL" current.tmp
mv -Tf current.tmp current
echo "current -> $(readlink current)"

echo "--- 4/4 정리 (최근 $KEEP개 유지) ---"
ls -1dt releases/*/ | tail -n "+$((KEEP + 1))" | xargs -r rm -rf
ls -1dt releases/*/ | sed 's|releases/||;s|/$||'
REMOTE_SCRIPT

echo
echo "완료. 롤백은 서버에서:"
echo "  cd $WEB_DIR && ls -1dt releases/*/          # 이전 릴리스 확인"
echo "  ln -sfn releases/{이전} current.tmp && mv -Tf current.tmp current"
