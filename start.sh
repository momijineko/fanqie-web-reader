#!/usr/bin/env bash
# 番茄小说阅读器 本地一键启动（macOS / Linux / Git Bash）
# 用法: ./start.sh        （Windows 用户请双击 start.bat）
# 环境变量: SKIP_FQAPI=1 跳过 fqapi 签名服务（降级社区 API）
set -u
cd "$(dirname "$0")"

FQAPI_REF="${FQAPI_REF:-v0.2.0}"
FQAPI_PORT=18921
SERVER_PORT=8080
FQAPI_BIN="fqapi/fqapi-server"
FQAPI_DIR="fqapi"
HEALTH_URL="http://127.0.0.1:${FQAPI_PORT}/readyz"
HEALTHZ_URL="http://127.0.0.1:${FQAPI_PORT}/healthz"

say()  { printf '\033[1;36m[start]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[warn]\033[0m %s\n' "$*"; }
fail() { printf '\033[1;31m[fail]\033[0m %s\n' "$*"; exit 1; }

health() { curl -s --noproxy '*' --max-time "${1:-2}" "$2" 2>/dev/null; }
command -v curl >/dev/null 2>&1 || fail "需要 curl，请先安装"

# ---------- 加载 .env（系统已存在的环境变量优先，不被覆盖） ----------
if [ -f .env ]; then
  while IFS= read -r line || [ -n "$line" ]; do
    line=${line%$'\r'}
    case "$line" in ''|\#*) continue;; esac
    key=${line%%=*}
    case "$key" in ''|[0-9]*|*[!A-Za-z0-9_]*) continue;; esac
    val=${line#*=}
    val=${val%\"}; val=${val#\"}; val=${val%\'}; val=${val#\'}
    [ -z "${!key:-}" ] && export "$key=$val"
  done < .env
  say "已加载 .env 配置"
fi

# ---------- 确保 FQAPI_TOKEN ----------
ensure_token() {
  if [ -n "${FQAPI_TOKEN:-}" ]; then
    return 0
  fi
  FQAPI_TOKEN=$(openssl rand -hex 24 2>/dev/null || head -c 24 /dev/urandom | xxd -p)
  export FQAPI_TOKEN
  if [ -f .env ]; then
    if grep -q '^FQAPI_TOKEN=' .env 2>/dev/null; then
      # 只改空值行
      if grep -q '^FQAPI_TOKEN=$' .env 2>/dev/null || grep -q '^FQAPI_TOKEN=$' .env 2>/dev/null; then
        tmp=$(mktemp)
        sed "s|^FQAPI_TOKEN=.*|FQAPI_TOKEN=${FQAPI_TOKEN}|" .env > "$tmp" && mv "$tmp" .env
      fi
    else
      printf '\nFQAPI_TOKEN=%s\n' "$FQAPI_TOKEN" >> .env
    fi
  else
    cp .env.example .env 2>/dev/null || true
    if [ -f .env ]; then
      tmp=$(mktemp)
      sed "s|^FQAPI_TOKEN=.*|FQAPI_TOKEN=${FQAPI_TOKEN}|" .env > "$tmp" && mv "$tmp" .env
    else
      printf 'FQAPI_TOKEN=%s\nFQAPI_API=http://127.0.0.1:%s\n' "$FQAPI_TOKEN" "$FQAPI_PORT" > .env
    fi
  fi
  # 若仍为空（原 .env 有非空但未 export 到当前），强制写入
  if ! grep -q "^FQAPI_TOKEN=${FQAPI_TOKEN}$" .env 2>/dev/null; then
    if grep -q '^FQAPI_TOKEN=' .env 2>/dev/null; then
      tmp=$(mktemp)
      sed "s|^FQAPI_TOKEN=.*|FQAPI_TOKEN=${FQAPI_TOKEN}|" .env > "$tmp" && mv "$tmp" .env
    else
      printf '\nFQAPI_TOKEN=%s\n' "$FQAPI_TOKEN" >> .env
    fi
  fi
  say "已生成 FQAPI_TOKEN 并写入 .env"
}

# ---------- Python 与虚拟环境 ----------
PY=python3
command -v python3 >/dev/null 2>&1 || PY=python
command -v "$PY" >/dev/null 2>&1 || fail "未找到 python3/python，请先安装 Python 3.10+"

if [ ! -x ".venv/bin/python" ] && [ ! -x ".venv/Scripts/python.exe" ]; then
  say "创建虚拟环境 .venv ..."
  "$PY" -m venv .venv || fail "venv 创建失败"
fi
VPY=".venv/bin/python"; [ -x "$VPY" ] || VPY=".venv/Scripts/python.exe"

if [ ! -f ".venv/.deps_done" ]; then
  say "安装依赖 requirements.txt ..."
  "$VPY" -m pip install -q -r requirements.txt || fail "依赖安装失败"
  touch .venv/.deps_done
fi

# ---------- fqapi-core 签名服务 ----------
FQAPI_PID=""
cleanup() { [ -n "$FQAPI_PID" ] && kill "$FQAPI_PID" 2>/dev/null; }
trap cleanup EXIT

have_fqapi=1

build_fqapi() {
  command -v cargo >/dev/null 2>&1 || return 1
  command -v git >/dev/null 2>&1 || return 1
  mkdir -p "$FQAPI_DIR" .fqapi_src
  if [ ! -d ".fqapi_src/fanqie-lite-api/.git" ]; then
    say "克隆 fanqie-lite-api ${FQAPI_REF} ..."
    rm -rf .fqapi_src/fanqie-lite-api
    git clone --depth 1 --branch "$FQAPI_REF" https://github.com/Maicy0609/fanqie-lite-api.git .fqapi_src/fanqie-lite-api \
      || return 1
  fi
  say "编译 fqapi-server（首次约 1–3 分钟）..."
  ( cd .fqapi_src/fanqie-lite-api && cargo build --release --locked --bin fqapi-server ) || return 1
  cp -f .fqapi_src/fanqie-lite-api/target/release/fqapi-server "$FQAPI_BIN"
  chmod +x "$FQAPI_BIN"
  return 0
}

if [ "${SKIP_FQAPI:-0}" = "1" ]; then
  warn "SKIP_FQAPI=1，跳过 fqapi（将降级社区 API）"
  have_fqapi=0
else
  ensure_token
  export FQAPI_API="${FQAPI_API:-http://127.0.0.1:${FQAPI_PORT}}"
  if health 2 "$HEALTHZ_URL" | grep -q '"status"'; then
    say "fqapi 已在运行（端口 $FQAPI_PORT），跳过启动"
  else
    if [ ! -x "$FQAPI_BIN" ]; then
      say "本地无 fqapi 二进制，尝试从源码编译 ${FQAPI_REF} ..."
      if ! build_fqapi; then
        warn "fqapi 编译失败。可安装 Rust（https://rustup.rs）后重试，或设 SKIP_FQAPI=1 降级社区 API"
        have_fqapi=0
      fi
    fi
    if [ "$have_fqapi" = "1" ]; then
      mkdir -p "$FQAPI_DIR/data"
      say "后台启动 fqapi（日志 ${FQAPI_DIR}/fqapi.log，就绪最长等约 60s）..."
      "$FQAPI_BIN" \
        --token "$FQAPI_TOKEN" \
        --data-dir "$FQAPI_DIR/data" \
        --listen "127.0.0.1:${FQAPI_PORT}" \
        > "$FQAPI_DIR/fqapi.log" 2>&1 &
      FQAPI_PID=$!
      ok=0
      for _ in $(seq 1 40); do
        if health 2 "$HEALTH_URL" | grep -q '"status":"ok"'; then
          ok=1
          break
        fi
        kill -0 "$FQAPI_PID" 2>/dev/null || break
        sleep 2
      done
      if [ "$ok" = "1" ]; then
        say "fqapi 就绪 → ${HEALTH_URL}"
      else
        # healthz 活着但设备池未就绪时仍可试着继续
        if health 2 "$HEALTHZ_URL" | grep -q '"status"'; then
          warn "fqapi 进程已启动但设备池尚未就绪，继续启动 Web（稍后自动可用）"
        else
          warn "fqapi 未能就绪（日志见 ${FQAPI_DIR}/fqapi.log），继续以社区 API 数据源启动"
        fi
      fi
    fi
  fi
fi

# ---------- Web 服务 ----------
health 2 "http://127.0.0.1:${SERVER_PORT}/api/health" | grep -q "ok" \
  && fail "端口 ${SERVER_PORT} 已有实例在运行（http://localhost:${SERVER_PORT}）"

( sleep 2; { command -v open >/dev/null 2>&1 && open "http://localhost:${SERVER_PORT}"; } \
  || { command -v xdg-open >/dev/null 2>&1 && xdg-open "http://localhost:${SERVER_PORT}"; } ) >/dev/null 2>&1 &

say "启动 Web 服务 → http://localhost:${SERVER_PORT}  （Ctrl+C 退出）"
export FQAPI_API="${FQAPI_API:-http://127.0.0.1:${FQAPI_PORT}}"
"$VPY" server.py
