#!/usr/bin/env bash
# Same steps as server auto-deploy / GitHub Actions SSH script.
# Intended to run on the VPS as ecs-user.
set -euo pipefail
APP_DIR="${HOME}/fanqie-web-reader"
cd "$APP_DIR"

git remote set-url origin https://kkgithub.com/momijineko/fanqie-web-reader.git || true
if ! git fetch --depth 1 origin main; then
  git remote set-url origin https://github.com/momijineko/fanqie-web-reader.git
  git fetch --depth 1 origin main
fi
git reset --hard origin/main
echo "Deploy commit: $(git rev-parse --short HEAD)"

if [ ! -f .env ]; then
  cp .env.example .env
fi
grep -q '^FQAPI_API=' .env || echo 'FQAPI_API=http://127.0.0.1:18921' >> .env
grep -q '^CORS_ORIGINS=' .env || echo 'CORS_ORIGINS=https://fanqie.momijineko.cn,http://127.0.0.1:8199' >> .env

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
