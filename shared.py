import json
import os
from pathlib import Path

import httpx

COOKIE_FILE = Path("user_cookie.json")

COMMUNITY_API = "http://101.35.133.34:5000"
FANQIE_API = "https://api5-normal-sinfonlinec.fqnovel.com"
UNIDBG_API = os.environ.get("UNIDBG_API", "http://127.0.0.1:8099")
PARA_COMMENT_MOCK = os.environ.get("PARA_COMMENT_MOCK", "true").lower() in ("true", "1", "yes")

client: httpx.AsyncClient = httpx.AsyncClient(
    timeout=30.0,
    headers={
        "User-Agent": "Mozilla/5.0 (Linux; Android 9; SM-G960F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
    },
    follow_redirects=True,
)

_fanqie_client: httpx.AsyncClient = httpx.AsyncClient(
    timeout=30.0,
    headers={
        "User-Agent": "com.dragon.read/6.5.3.32.3 (Android 9)",
        "Content-Type": "application/json",
    },
    follow_redirects=True,
)


def load_cookie() -> dict:
    if COOKIE_FILE.exists():
        try:
            return json.loads(COOKIE_FILE.read_text("utf-8"))
        except Exception:
            pass
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
