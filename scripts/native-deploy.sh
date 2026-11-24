#!/usr/bin/env bash
# Deploy fanqie-web-reader on the VPS (ecs-user).
# Only fetches from github.com (no third-party git proxies).
# Optional: EXPECTED_SHA=<40-char sha> pins the deployed commit (used by Actions).
set -euo pipefail
APP_DIR="${HOME}/fanqie-web-reader"
cd "$APP_DIR"

export GIT_HTTP_LOW_SPEED_LIMIT=1000
export GIT_HTTP_LOW_SPEED_TIME=30

ORIGIN_URL="https://github.com/momijineko/fanqie-web-reader.git"
git remote set-url origin "$ORIGIN_URL"
git fetch --depth 1 origin main
git fetch --tags --force origin
git fetch --deepen=200 origin main || true

if [ -n "${EXPECTED_SHA:-}" ]; then
  case "$EXPECTED_SHA" in
    *[!0-9a-fA-F]*|"" )
      echo "REFUSING deploy: EXPECTED_SHA must be hex"
      exit 1
      ;;
  esac
  if [ "${#EXPECTED_SHA}" -lt 40 ]; then
    echo "REFUSING deploy: EXPECTED_SHA must be a full 40-char commit SHA"
    exit 1
  fi
  # Ensure object is present (depth-1 main tip may not include older pins)
  if ! git cat-file -e "${EXPECTED_SHA}^{commit}" 2>/dev/null; then
    git fetch --depth 1 origin "$EXPECTED_SHA"
  fi
  git reset --hard "$EXPECTED_SHA"
  GOT=$(git rev-parse HEAD)
  WANT=$(printf '%s' "$EXPECTED_SHA" | tr '[:upper:]' '[:lower:]')
  GOT_L=$(printf '%s' "$GOT" | tr '[:upper:]' '[:lower:]')
  if [ "$GOT_L" != "$WANT" ]; then
    echo "REFUSING deploy: HEAD $GOT != expected $EXPECTED_SHA"
    exit 1
  fi
else
  git reset --hard origin/main
fi

echo "Deploy commit: $(git rev-parse --short HEAD) ($(git rev-parse HEAD))"

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
# HTTPS mirror with TLS verification enabled (do not pass --trusted-host).
PIP_INDEX="https://pypi.tuna.tsinghua.edu.cn/simple"
python -m pip install -q -U pip -i "$PIP_INDEX"
python -m pip install -q -r requirements.txt -i "$PIP_INDEX"

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
if [ "$WEB_CODE" != "200" ]; then
  # Do not dump journals here — may contain secrets and can leak via public CI logs.
  echo "health check failed (fqapi=$(systemctl --user is-active fanqie-fqapi.service) web=$(systemctl --user is-active fanqie-web.service))"
  exit 1
fi
systemctl --user --no-pager is-active fanqie-fqapi.service fanqie-web.service
echo "Deployment completed successfully!"
