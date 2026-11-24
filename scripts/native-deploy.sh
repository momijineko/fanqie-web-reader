#!/usr/bin/env bash
# Deploy fanqie-web-reader on the VPS (ecs-user).
#
# Trust model (mainland-friendly):
# 1. Resolve the target commit SHA from a trusted channel (EXPECTED_SHA from
#    Actions, else github.com / api.github.com).
# 2. Fetch git *objects* for that SHA from github.com, with ghproxy only as a
#    content-addressed fallback. A mirror cannot substitute different code for
#    the same SHA.
# 3. Refuse to continue unless HEAD == EXPECTED_SHA after reset.
set -euo pipefail
APP_DIR="${HOME}/fanqie-web-reader"
cd "$APP_DIR"

export GIT_HTTP_LOW_SPEED_LIMIT=1000
export GIT_HTTP_LOW_SPEED_TIME=45

REPO_PATH="momijineko/fanqie-web-reader"
GITHUB_URL="https://github.com/${REPO_PATH}.git"
# Content-addressed fetch fallback only (never used to decide what "main" is).
FETCH_URLS=(
  "$GITHUB_URL"
  "https://ghproxy.net/https://github.com/${REPO_PATH}.git"
)

resolve_main_sha() {
  local sha=""
  # Prefer git protocol to github.com
  sha=$(timeout 45 git ls-remote "$GITHUB_URL" refs/heads/main 2>/dev/null | awk '{print $1; exit}') || true
  if [ -n "$sha" ] && [ "${#sha}" -eq 40 ]; then
    printf '%s\n' "$sha"
    return 0
  fi
  # REST API as second trusted source
  sha=$(timeout 30 curl -fsS --connect-timeout 10 -m 30 \
    -H 'Accept: application/vnd.github+json' \
    -H 'User-Agent: fanqie-native-deploy' \
    "https://api.github.com/repos/${REPO_PATH}/commits/main" \
    | python3 -c 'import sys,json; print(json.load(sys.stdin)["sha"])' 2>/dev/null) || true
  if [ -n "$sha" ] && [ "${#sha}" -eq 40 ]; then
    printf '%s\n' "$sha"
    return 0
  fi
  return 1
}

fetch_sha() {
  local sha="$1"
  local url
  for url in "${FETCH_URLS[@]}"; do
    echo "try fetch $sha via $url"
    git remote set-url origin "$url"
    if timeout 90 git fetch --depth 1 origin "$sha"; then
      if git cat-file -e "${sha}^{commit}" 2>/dev/null; then
        return 0
      fi
    fi
  done
  return 1
}

# --- resolve pin ---
if [ -n "${EXPECTED_SHA:-}" ]; then
  case "$EXPECTED_SHA" in
    *[!0-9a-fA-F]* ) echo "REFUSING deploy: EXPECTED_SHA must be hex"; exit 1 ;;
  esac
  if [ "${#EXPECTED_SHA}" -ne 40 ]; then
    echo "REFUSING deploy: EXPECTED_SHA must be a full 40-char commit SHA"
    exit 1
  fi
else
  echo "Resolving origin/main SHA from github.com / api.github.com ..."
  EXPECTED_SHA=$(resolve_main_sha) || {
    echo "REFUSING deploy: could not resolve main SHA from trusted sources"
    exit 1
  }
fi
EXPECTED_SHA=$(printf '%s' "$EXPECTED_SHA" | tr '[:upper:]' '[:lower:]')
echo "EXPECTED_SHA=$EXPECTED_SHA"

# Keep remote URL on github.com for day-to-day metadata; fetches may temporarily switch.
git remote set-url origin "$GITHUB_URL"

if ! git cat-file -e "${EXPECTED_SHA}^{commit}" 2>/dev/null; then
  fetch_sha "$EXPECTED_SHA" || {
    echo "REFUSING deploy: failed to fetch $EXPECTED_SHA from github.com / ghproxy"
    exit 1
  }
fi

# Only fetch SemVer-looking tags (avoids hostile tag names from mirrors).
fetch_semver_tags() {
  local url="$1"
  timeout 60 git fetch --force "$url" 'refs/tags/v*:refs/tags/v*'
}
git remote set-url origin "$GITHUB_URL"
fetch_semver_tags origin || fetch_semver_tags "https://ghproxy.net/https://github.com/${REPO_PATH}.git" || true
# Deepen for git describe when needed (best-effort)
timeout 60 git fetch --deepen=200 origin main || true

git reset --hard "$EXPECTED_SHA"
GOT=$(git rev-parse HEAD | tr '[:upper:]' '[:lower:]')
if [ "$GOT" != "$EXPECTED_SHA" ]; then
  echo "REFUSING deploy: HEAD $GOT != expected $EXPECTED_SHA"
  exit 1
fi
git remote set-url origin "$GITHUB_URL"
echo "Deploy commit: $(git rev-parse --short HEAD) ($GOT)"

if [ ! -f .env ]; then
  cp .env.example .env
fi
grep -q '^FQAPI_API=' .env || echo 'FQAPI_API=http://127.0.0.1:18921' >> .env
grep -q '^CORS_ORIGINS=' .env || echo 'CORS_ORIGINS=https://fanqie.momijineko.cn,http://127.0.0.1:8199' >> .env

# Version string must be a safe SemVer-ish token — never feed raw tag names to sed.
VER=$(git describe --tags --match 'v[0-9]*' 2>/dev/null \
  || git describe --tags --abbrev=0 --match 'v[0-9]*' 2>/dev/null \
  || true)
if [ -z "${VER}" ]; then
  LATEST=$(git tag -l 'v[0-9]*' --sort=-v:refname | head -1 || true)
  if [ -n "${LATEST}" ]; then
    VER="${LATEST}-g$(git rev-parse --short HEAD)"
  else
    VER="0.0.0-dev-g$(git rev-parse --short HEAD)"
  fi
fi
case "$VER" in
  ''|*[!A-Za-z0-9._+-]* )
    echo "REFUSING deploy: unsafe APP_VERSION from tags: $VER"
    exit 1
    ;;
esac
# Rewrite APP_VERSION without sed (tag names must not reach the shell as code).
python3 - "$VER" <<'PY'
import pathlib, sys
ver = sys.argv[1]
path = pathlib.Path(".env")
lines = path.read_text(encoding="utf-8").splitlines() if path.exists() else []
out, found = [], False
for line in lines:
    if line.startswith("APP_VERSION="):
        out.append(f"APP_VERSION={ver}")
        found = True
    else:
        out.append(line)
if not found:
    out.append(f"APP_VERSION={ver}")
path.write_text("\n".join(out) + "\n", encoding="utf-8")
print(f"APP_VERSION={ver}")
PY

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

FQAPI_READY=0
for i in $(seq 1 40); do
  if curl -fsS --noproxy '*' --connect-timeout 3 http://127.0.0.1:18921/readyz >/dev/null 2>&1; then
    echo "fqapi READY"
    FQAPI_READY=1
    break
  fi
  sleep 3
done
if [ "$FQAPI_READY" != "1" ]; then
  echo "fqapi never became ready (unit=$(systemctl --user is-active fanqie-fqapi.service))"
  exit 1
fi

WEB_CODE=$(curl -s --noproxy '*' -o /dev/null -w "%{http_code}" --connect-timeout 5 http://127.0.0.1:8199/api/health || echo 000)
echo "Web reader HTTP $WEB_CODE"
if [ "$WEB_CODE" != "200" ]; then
  echo "health check failed (fqapi=$(systemctl --user is-active fanqie-fqapi.service) web=$(systemctl --user is-active fanqie-web.service))"
  exit 1
fi

FQAPI_STATE=$(systemctl --user is-active fanqie-fqapi.service || true)
WEB_STATE=$(systemctl --user is-active fanqie-web.service || true)
echo "fqapi=$FQAPI_STATE web=$WEB_STATE"
if [ "$FQAPI_STATE" != "active" ] || [ "$WEB_STATE" != "active" ]; then
  echo "REFUSING success: both fanqie-fqapi and fanqie-web must be active"
  exit 1
fi
echo "Deployment completed successfully!"
