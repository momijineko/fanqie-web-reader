#!/usr/bin/env bash
# 番茄小说阅读器 本地一键启动（macOS / Linux / Git Bash）
# 用法: ./start.sh        （Windows 用户请双击 start.bat）
# 环境变量: SKIP_UNIDBG=1 跳过 unidbg 签名服务；SKIP_JRE=1 跳过 JRE 自动下载
set -u
cd "$(dirname "$0")"

UNIDBG_VERSION=0.0.6
UNIDBG_SHA256=505fa249c37365cde4bcaafcd433cc6e8af83fdfb1948a5b145ddc5a45293cfb
UNIDBG_PORT=8099
SERVER_PORT=8080
JAR="unidbg/unidbg-boot-server-${UNIDBG_VERSION}.jar"
HEALTH_URL="http://127.0.0.1:${UNIDBG_PORT}/api/fqnovel/health"

say()  { printf '\033[1;36m[start]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[warn]\033[0m %s\n' "$*"; }
fail() { printf '\033[1;31m[fail]\033[0m %s\n' "$*"; exit 1; }

# 本地健康探测：绕过代理、限 2 秒超时
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

# ---------- Java 运行时与 unidbg 签名服务 ----------
UNIDBG_PID=""
cleanup() { [ -n "$UNIDBG_PID" ] && kill "$UNIDBG_PID" 2>/dev/null; }
trap cleanup EXIT

have_unidbg=1
JAVA_CMD=""

# 复用项目内置 JRE（须实际可运行，防止平台不符的残留）
if [ -x "jre/bin/java.exe" ]; then
  JAVA_CMD="jre/bin/java.exe"
elif [ -x "jre/bin/java" ]; then
  JAVA_CMD="jre/bin/java"
fi
if [ -n "$JAVA_CMD" ] && ! "$JAVA_CMD" -version >/dev/null 2>&1; then
  warn "本地 jre/ 无法运行（平台不符或损坏），重新下载..."
  rm -rf jre
  JAVA_CMD=""
fi

# 自动下载 Temurin 17 JRE 到 jre/（Adoptium 直连，失败走 gh-proxy 镜像）
jre_download() {
  local os=linux arch=x64
  case "$(uname)" in
    Darwin) os=mac ;;
    MINGW*|MSYS*|CYGWIN*) os=windows ;;
  esac
  case "$(uname -m)" in aarch64|arm64) arch=aarch64;; esac
  mkdir -p .jre_tmp
  say "下载 Temurin 17 JRE（约 45MB，$os/$arch）..."
  curl -fL --retry 3 --connect-timeout 15 -o .jre_tmp/jre.pkg \
    "https://api.adoptium.net/v3/binary/latest/17/ga/$os/$arch/jre/hotspot/normal/eclipse?project=jdk" || {
    say "Adoptium 直连失败，尝试镜像加速..."
    curl -fsS --retry 2 --connect-timeout 15 -o .jre_tmp/asset.json \
      "https://api.adoptium.net/v3/assets/latest/17/hotspot?os=$os&architecture=$arch&image_type=jre&vendor=eclipse" || return 1
    local link
    link=$("$PY" -c "import json;print(json.load(open('.jre_tmp/asset.json'))[0]['binary']['package']['link'])" 2>/dev/null) || return 1
    curl -fL --retry 3 -o .jre_tmp/jre.pkg "https://gh-proxy.com/$link" \
      || curl -fL --retry 3 -o .jre_tmp/jre.pkg "$link" || return 1
  }
  # Windows 包是 zip（Git Bash 的 GNU tar 不认），优先 System32 bsdtar，再 unzip / PowerShell
  if [ "$os" = "windows" ]; then
    /c/Windows/System32/tar.exe -xf .jre_tmp/jre.pkg -C .jre_tmp 2>/dev/null \
      || unzip -q -o .jre_tmp/jre.pkg -d .jre_tmp 2>/dev/null \
      || powershell -NoProfile -Command "Expand-Archive -Force '.jre_tmp/jre.pkg' '.jre_tmp'" || return 1
  else
    tar -xzf .jre_tmp/jre.pkg -C .jre_tmp || return 1
  fi
  local jbin
  jbin=$(find .jre_tmp -type f \( -name "java.exe" -o -name "java" \) -path "*/bin/*" | head -1)
  [ -n "$jbin" ] || return 1
  rm -rf jre
  mv "$(dirname "$(dirname "$jbin")")" jre || return 1
  rm -rf .jre_tmp
  if [ -f "jre/bin/java.exe" ]; then JAVA_CMD="jre/bin/java.exe"; else JAVA_CMD="jre/bin/java"; fi
  "$JAVA_CMD" -version >/dev/null 2>&1
}

if [ "${SKIP_UNIDBG:-0}" = "1" ]; then
  warn "SKIP_UNIDBG=1，跳过 unidbg"
  have_unidbg=0
else
  # PATH java 存在但为 21+（与 unidbg 不兼容）时，清空后走自动下载
  if [ -n "$JAVA_CMD" ] && [ "$JAVA_CMD" != "jre/bin/java" ] && [ "$JAVA_CMD" != "jre/bin/java.exe" ]; then
    jv=$(java -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/')
    if [ "${jv:-0}" -ge 21 ] 2>/dev/null; then
      warn "PATH 上的 Java 为 ${jv}（21+ 与 unidbg 不兼容），自动改用本地 Temurin 17"
      JAVA_CMD=""
    fi
  fi
  if [ -z "$JAVA_CMD" ]; then
    if [ "${SKIP_JRE:-0}" = "1" ]; then
      warn "SKIP_JRE=1 且未检测到可用 Java，跳过 unidbg"
      have_unidbg=0
    elif jre_download; then
      say "JRE 就绪 → $JAVA_CMD"
    else
      warn "JRE 自动下载失败，可手动安装（11 或 17，勿用 21+）后重跑，或设 SKIP_JRE=1 跳过："
      warn "  macOS:   brew install --cask temurin@17"
      warn "  Ubuntu:  sudo apt install openjdk-17-jre"
      warn "  Windows: winget install EclipseAdoptium.Temurin.17.JRE"
      have_unidbg=0
    fi
  fi
fi

if [ "$have_unidbg" = "1" ]; then
  if health 2 "$HEALTH_URL" | grep -q "UP"; then
    say "unidbg 已在运行（端口 $UNIDBG_PORT），跳过启动"
  else
    if [ ! -f "$JAR" ]; then
      say "下载 fqnovel-unidbg v${UNIDBG_VERSION} ..."
      mkdir -p unidbg
      URL1="https://gh-proxy.com/https://github.com/mtongle/fqnovel-unidbg/releases/download/v${UNIDBG_VERSION}/unidbg-boot-server-${UNIDBG_VERSION}.jar"
      URL2="https://github.com/mtongle/fqnovel-unidbg/releases/download/v${UNIDBG_VERSION}/unidbg-boot-server-${UNIDBG_VERSION}.jar"
      curl -fL --retry 3 --connect-timeout 15 -o "$JAR" "$URL1" \
        || curl -fL --retry 3 --connect-timeout 15 -o "$JAR" "$URL2" \
        || fail "jar 下载失败，请检查网络后重试（或手动下载 ${URL2} 放到 ${JAR}）"
    fi
    say "校验 jar SHA256 ..."
    calc=$(sha256sum "$JAR" 2>/dev/null | cut -d' ' -f1) || calc=$(shasum -a 256 "$JAR" | cut -d' ' -f1)
    [ "$calc" = "$UNIDBG_SHA256" ] || fail "jar 校验不符：$calc"

    say "后台启动 unidbg（日志 unidbg/unidbg.log，健康检查最长等 3 分钟）..."
    # Must pass --server.port explicitly: the SERVER_PORT env var (for the Python web
    # service) would otherwise be picked up by Spring Boot relaxed binding and make
    # unidbg steal port 8080, colliding with the web server.
    "$JAVA_CMD" -Dfile.encoding=UTF-8 -jar "$JAR" --server.port=${UNIDBG_PORT} > unidbg/unidbg.log 2>&1 &
    UNIDBG_PID=$!

    ok=0
    for _ in $(seq 1 60); do
      health 2 "$HEALTH_URL" | grep -q "UP" && ok=1 && break
      kill -0 "$UNIDBG_PID" 2>/dev/null || break
      sleep 3
    done
    if [ "$ok" = "1" ]; then
      say "unidbg 就绪 → ${HEALTH_URL}"
    else
      warn "unidbg 未能就绪（日志见 unidbg/unidbg.log），继续以社区 API 数据源启动"
    fi
  fi
fi

# ---------- Web 服务 ----------
health 2 "http://127.0.0.1:${SERVER_PORT}/api/health" | grep -q "ok" \
  && fail "端口 ${SERVER_PORT} 已有实例在运行（http://localhost:${SERVER_PORT}）"

( sleep 2; { command -v open >/dev/null 2>&1 && open "http://localhost:${SERVER_PORT}"; } \
  || { command -v xdg-open >/dev/null 2>&1 && xdg-open "http://localhost:${SERVER_PORT}"; } ) >/dev/null 2>&1 &

say "启动 Web 服务 → http://localhost:${SERVER_PORT}  （Ctrl+C 退出）"
"$VPY" server.py
