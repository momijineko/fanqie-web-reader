#!/usr/bin/env bash
# Same steps as server auto-deploy / GitHub Actions SSH script.
# Intended to run on the VPS as ecs-user.
set -euo pipefail
APP_DIR="${HOME}/fanqie-web-reader"
cd "$APP_DIR"

export GIT_HTTP_LOW_SPEED_LIMIT=1000
export GIT_HTTP_LOW_SPEED_TIME=20
OK=0
for url in \
  "https://github.com/momijineko/fanqie-web-reader.git" \
  "https://kkgithub.com/momijineko/fanqie-web-reader.git" \
  "https://gitclone.com/github.com/momijineko/fanqie-web-reader.git"
do
  echo "try fetch $url"
  git remote set-url origin "$url"
  if git fetch --depth 1 origin main \
    && git fetch --tags --force origin \
    && git fetch --deepen=200 origin main; then
    OK=1
    break
  fi
done
test "$OK" = "1"
git reset --hard origin/main
echo "Deploy commit: $(git rev-parse --short HEAD)"

if [ ! -f .env ]; then
  cp .env.example .env
fi
grep -q '^FQAPI_API=' .env || echo 'FQAPI_API=http://127.0.0.1:18921' >> .env
grep -q '^CORS_ORIGINS=' .env || echo 'CORS_ORIGINS=https://fanqie.momijineko.cn,http://127.0.0.1:8199' >> .env

# APP_VERSION: shallow clones without tag ancestry fall back to 0.0.0-dev
VER=$(git describe --tags 2>/dev/null \
  || git describe --tags --abbrev=0 2>/dev/null \
  || true)
if [ -z "${VER}" ]; then
  LATEST=$(git tag -l 'v*' --sort=-v:refname | head -1 || true)
  if [ -n "${LATEST}" ]; then
    VER="${LATEST}+$(git rev-parse --short HEAD)"
  else
    VER="0.0.0-dev+$(git rev-parse --short HEAD)"
  fi
fi
if grep -q '^APP_VERSION=' .env; then
  sed -i "s|^APP_VERSION=.*|APP_VERSION=${VER}|" .env
else
  printf '\nAPP_VERSION=%s\n' "$VER" >> .env
fi
echo "APP_VERSION=${VER}"

if [ ! -x .venv/bin/python ]; then
  python3.11 -m venv .venv
fi
# shellcheck disable=SC1091
. .venv/bin/activate
python -m pip install -q -U pip -i https://pypi.tuna.tsinghua.edu.cn/simple --trusted-host pypi.tuna.tsinghua.edu.cn
python -m pip install -q -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple --trusted-host pypi.tuna.tsinghua.edu.cn

systemctl --user daemon-reload
systemctl --user restart fanqie-fqapi.service || true
systemctl --user restart fanqie-web.service

for i in $(seq 1 40); do
  if curl -fsS --noproxy '*' --connect-timeout 3 http://127.0.0.1:18921/readyz >/dev/null 2>&1; then
    echo "fqapi READY"
    break
  fi
  sleep 3
done
WEB_CODE=$(curl -s --noproxy '*' -o /dev/null -w "%{http_code}" --connect-timeout 5 http://127.0.0.1:8199/api/health || echo 000)
echo "Web reader HTTP $WEB_CODE"
test "$WEB_CODE" = "200"
systemctl --user --no-pager is-active fanqie-fqapi.service fanqie-web.service
echo "Deployment completed successfully!"
