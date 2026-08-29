import json
import logging
import os
from pathlib import Path

import httpx

COOKIE_FILE = Path("user_cookie.json")

# 社区 API 备用源：逗号分隔多实例，请求时按序轮询，任一实例复活即自动生效。
# 允许配置为空（COMMUNITY_API=）——此时仅用 unidbg 数据源，回退层返回 502。
COMMUNITY_APIS = [
    u.strip() for u in os.environ.get(
        "COMMUNITY_API",
        "http://101.35.133.34:5000,https://tt.sjmyzq.cn",
    ).split(",") if u.strip()
]
UNIDBG_API = os.environ.get("UNIDBG_API", "http://127.0.0.1:8099")
# 段评 Mock 仅供开发调试：没启动 unidbg 时预览段评的格式和样式用。
# 生产/日常使用必须保持 false（默认），否则段评显示的是假数据。
PARA_COMMENT_MOCK = os.environ.get("PARA_COMMENT_MOCK", "false").lower() in ("true", "1", "yes")

logger = logging.getLogger("fanqie")

_version_cache: str | None = None


def resolve_version() -> str:
    """版本号解析：优先 APP_VERSION 环境变量（Docker 构建时注入 git describe 结果），
    其次本地 git describe，兜底 0.0.0-dev。进程内缓存避免每次请求 fork 子进程。"""
    global _version_cache
    if _version_cache is None:
        version = os.environ.get("APP_VERSION", "").strip()
        if not version:
            try:
                import subprocess
                version = subprocess.check_output(
                    ["git", "describe", "--tags", "--abbrev=0"],
                    stderr=subprocess.DEVNULL, timeout=3,
                ).decode().strip()
            except Exception:
                version = ""
        _version_cache = version or "0.0.0-dev"
    return _version_cache


def cors_origins() -> list[str]:
    """解析 CORS_ORIGINS 环境变量；未设置时回退到本地常用端口。"""
    raw = os.environ.get("CORS_ORIGINS", "").strip()
    if not raw:
        return [
            "http://localhost:8080",
            "http://127.0.0.1:8080",
            "http://localhost:8199",
            "http://127.0.0.1:8199",
        ]
    return [o.strip() for o in raw.split(",") if o.strip()]


# trust_env=False：忽略 HTTP_PROXY/ALL_PROXY 等代理环境变量。本应用全部上游
# （unidbg、社区 API、番茄 CDN/passport）均为国内直连可达，而用户机器上的全局
# 代理变量会把发往 127.0.0.1 unidbg 的请求也劫持进代理，得到 502 空响应。
client: httpx.AsyncClient = httpx.AsyncClient(
    timeout=30.0,
    trust_env=False,
    headers={
        "User-Agent": "Mozilla/5.0 (Linux; Android 9; SM-G960F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
    },
    follow_redirects=True,
)

_fanqie_client: httpx.AsyncClient = httpx.AsyncClient(
    timeout=30.0,
    trust_env=False,
    headers={
        "User-Agent": "com.dragon.read/6.5.3.32.3 (Android 9)",
        "Content-Type": "application/json",
    },
    follow_redirects=True,
)


async def community_get(path: str, params: dict) -> dict | None:
    """按序请求各社区 API 实例（COMMUNITY_APIS），返回首个 code==200 的响应；全部失败返回 None。

    单实例总超时 6s：避免 TCP 挂起（非拒绝）的实例把每个回退请求拖满 30s。
    """
    for base in COMMUNITY_APIS:
        try:
            r = await client.get(f"{base}{path}", params=params, timeout=6.0)
            data = r.json()
            if data.get("code") == 200:
                return data
        except Exception as e:
            logger.info("社区 API %s%s 不可用: %s: %s", base, path, type(e).__name__, e)
    return None


def load_cookie() -> dict:
    if COOKIE_FILE.exists():
        try:
            return json.loads(COOKIE_FILE.read_text("utf-8"))
        except Exception:
            logger.warning("cookie 文件读取失败，已忽略", exc_info=True)
    return {}


def save_cookie(cookie_str: str):
    COOKIE_FILE.write_text(json.dumps({"cookie": cookie_str}, ensure_ascii=False), "utf-8")


def web_headers(cookie: str = "") -> dict:
    h = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36",
        "Accept": "application/json, text/plain, */*",
        "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
        "Referer": "https://fanqienovel.com/bookshelf?enter_from=menu",
        "Content-Type": "application/json",
    }
    if cookie:
        h["Cookie"] = cookie
    return h
