import time
import uuid

import httpx
from fastapi import APIRouter
from pydantic import BaseModel, Field

from shared import COOKIE_FILE, client, load_cookie, logger, save_cookie, web_headers

router = APIRouter()


class CookiePayload(BaseModel):
    cookie: str = Field(..., min_length=1)


class ProgressPayload(BaseModel):
    book_id: str = Field(..., min_length=1)
    item_id: str = ""
    chapter_id: str = ""
    index: int = 0
    chapter_idx: int = 0


class BookshelfTargetPayload(BaseModel):
    book_id: str = Field(..., min_length=1)


class MoveBookPayload(BaseModel):
    book_id: str = Field(..., min_length=1)
    group_id: str = ""
    group_name: str = ""


def _require_cookie() -> str | None:
    cdata = load_cookie()
    cookie = cdata.get("cookie", "") if isinstance(cdata, dict) else ""
    return cookie or None


@router.post("/api/user/cookie")
async def save_user_cookie(body: CookiePayload):
    raw = body.cookie.strip()
    # 允许只贴 sessionid 值（不含 "=" 时按 sessionid 处理）
    cookie = raw if "=" in raw else f"sessionid={raw}"
    save_cookie(cookie)
    return {"code": 200, "msg": "saved"}


@router.delete("/api/user/cookie")
async def delete_user_cookie():
    if COOKIE_FILE.exists():
        COOKIE_FILE.unlink()
    return {"code": 200, "msg": "deleted"}


# ===== 扫码登录（番茄官网同源 passport，纯 Web 请求，无需 unidbg 签名）=====
# 流程：/passport/web/get_qrcode/ 出码 → /passport/web/check_qrconnect/ 轮询 →
# 确认后跟随 redirect_url 收 Set-Cookie → jar 里的 cookie 即登录态，复用 save_cookie。
# 实测（2026-08）约束：aid 必须是 2503（1967 会"该应用无权限"）；参数用 next 而非 service；
# 全程无验证码；响应中的 captcha 字段非空时表示上游加了人机校验，直接报错回退 Cookie 粘贴。
_PASSPORT_QUERY = {
    "aid": "2503",
    "app_name": "novelapp",
    "version_code": "57700",
    "device_platform": "web",
    "channel": "novel",
    "sdk_version": "1.6.1",
    "passport_sdk_version": "2.0.0",
    "new_user": "0",
}
_NEXT_URL = "https://fanqienovel.com/"
_LOGIN_SESSION_TTL = 300.0

_login_sessions: dict[str, dict] = {}


def _new_login_client() -> httpx.AsyncClient:
    return httpx.AsyncClient(
        timeout=15.0,
        headers={
            "User-Agent": web_headers()["User-Agent"],
            "Referer": "https://fanqienovel.com/",
            "Origin": "https://fanqienovel.com",
            "Accept": "application/json, text/plain, */*",
        },
        follow_redirects=True,
    )


async def _close_login_session(session_id: str):
    sess = _login_sessions.pop(session_id, None)
    if sess:
        try:
            await sess["client"].aclose()
        except Exception:
            pass


async def _sweep_login_sessions():
    now = time.time()
    for sid in [sid for sid, s in _login_sessions.items() if now - s["ts"] > _LOGIN_SESSION_TTL]:
        await _close_login_session(sid)


async def _finalize_qr_login(sess: dict, redirect_url: str) -> str:
    c = sess["client"]
    for url in [u for u in (redirect_url, _NEXT_URL) if u]:
        try:
            await c.get(url, headers={"Accept": "text/html,application/json"})
        except Exception as e:
            logger.info("扫码登录跟随跳转失败 %s: %s", url, type(e).__name__)
    cookie_map: dict[str, str] = {}
    for ck in c.cookies.jar:
        # 只收番茄域的 cookie：redirect 链上可能途经其他域，不该混进登录态
        dom = (ck.domain or "").lstrip(".")
        if ck.value is None or not (dom == "fanqienovel.com" or dom.endswith(".fanqienovel.com")):
            continue
        cookie_map[ck.name] = ck.value
    cookie_str = "; ".join(f"{k}={v}" for k, v in cookie_map.items())
    if "sessionid" in cookie_str:
        save_cookie(cookie_str)
    return cookie_str


@router.post("/api/user/login/qrcode/start")
async def login_qrcode_start():
    await _sweep_login_sessions()
    c = _new_login_client()
    try:
        r = await c.get(
            "https://fanqienovel.com/passport/web/get_qrcode/",
            params={"next": _NEXT_URL, **_PASSPORT_QUERY},
        )
        data = r.json()
    except Exception as e:
        await c.aclose()
        logger.warning("get_qrcode 请求失败: %s", e, exc_info=True)
        return {"code": 500, "msg": f"upstream error: {type(e).__name__}"}
    d = data.get("data") or data
    if d.get("captcha"):
        await c.aclose()
        return {"code": 502, "msg": "passport 要求人机校验，请改用 Cookie 粘贴登录"}
    if d.get("error_code") not in (0, None, "0"):
        await c.aclose()
        return {"code": 502, "msg": d.get("description") or f"passport error {d.get('error_code')}"}
    token = str(d.get("token") or d.get("qr_token") or "")
    if not token:
        await c.aclose()
        return {"code": 502, "msg": "missing qrcode token"}
    qrcode_b64 = str(d.get("qrcode") or "")
    session_id = uuid.uuid4().hex
    _login_sessions[session_id] = {
        "client": c,
        "token": token,
        "ts": time.time(),
    }
    return {
        "code": 200,
        "data": {
            "session_id": session_id,
            "qrcode": f"data:image/png;base64,{qrcode_b64}" if qrcode_b64 else "",
            "qrcode_index_url": str(d.get("qrcode_index_url") or ""),
            "expire_time": d.get("expire_time", 0),
        },
        "msg": "success",
    }


@router.get("/api/user/login/qrcode/poll")
async def login_qrcode_poll(session_id: str):
    sess = _login_sessions.get(session_id)
    if not sess:
        return {"code": 404, "msg": "session expired", "data": {"status": "expired"}}
    sess["ts"] = time.time()
    try:
        r = await sess["client"].get(
            "https://fanqienovel.com/passport/web/check_qrconnect/",
            params={"next": _NEXT_URL, "token": sess["token"], **_PASSPORT_QUERY},
        )
        d = r.json().get("data") or {}
    except Exception as e:
        # 网络抖动按继续等待处理，前端下一轮重试
        logger.warning("check_qrconnect 请求失败: %s", e)
        return {"code": 200, "data": {"status": "waiting"}}
    status = str(d.get("status") or "").lower()
    redirect_url = str(d.get("redirect_url") or d.get("redirectUrl") or d.get("url") or d.get("next_url") or "")
    if d.get("captcha"):
        await _close_login_session(session_id)
        return {"code": 200, "data": {"status": "failed"}, "msg": "passport 要求人机校验，请改用 Cookie 粘贴登录"}
    if redirect_url or status in ("confirmed", "success", "done", "ok"):
        try:
            cookie_str = await _finalize_qr_login(sess, redirect_url)
        except Exception as e:
            # 落盘失败等异常不向上抛：返回 failed 引导兜底，同时确保会话关闭
            logger.warning("扫码登录 finalize 失败: %s", e, exc_info=True)
            cookie_str = ""
        finally:
            await _close_login_session(session_id)
        if "sessionid" not in cookie_str:
            return {
                "code": 200,
                "data": {"status": "failed"},
                "msg": "扫码已确认，但未能建立会话，请改用 Cookie 粘贴登录",
            }
        return {"code": 200, "data": {"status": "success"}}
    if d.get("error_code") not in (0, None, "0"):
        await _close_login_session(session_id)
        return {
            "code": 200,
            "data": {"status": "expired"},
            "msg": d.get("description") or "二维码已过期，请刷新重试",
        }
    scanned = status not in ("", "new", "init")
    return {"code": 200, "data": {"status": "scanned" if scanned else "waiting"}}


@router.get("/api/user/info")
async def get_user_info():
    cookie = _require_cookie()
    if not cookie:
        return {"code": 401, "msg": "not logged in", "data": None}
    try:
        r = await client.get(
            "https://fanqienovel.com/api/user/info/v2",
            headers=web_headers(cookie),
            timeout=15.0,
        )
        data = r.json()
        if data.get("code") == 0:
            user = data.get("data", {})
            return {
                "code": 200,
                "data": {
                    "user_name": user.get("name", user.get("user_name", "")),
                    "avatar_url": user.get("avatar", user.get("avatar_url", "")),
                    "gender": user.get("gender", 0),
                    "user_id": str(user.get("id", user.get("user_id", user.get("uid", "")))),
                },
                "msg": "success",
            }
        return {"code": data.get("code", -1), "msg": data.get("message", "unknown error"), "data": None}
    except Exception as e:
        logger.warning("get_user_info 请求失败: %s", e, exc_info=True)
        return {"code": 500, "msg": f"upstream error: {type(e).__name__}", "data": None}


@router.get("/api/user/bookshelf")
async def get_user_bookshelf():
    cookie = _require_cookie()
    if not cookie:
        return {"code": 401, "msg": "not logged in", "data": None}
    try:
        r = await client.get(
            "https://fanqienovel.com/reading/bookapi/bookshelf/info/v:version/",
            params={"aid": "1967", "iid": "0", "version_code": "57700", "update_version_code": "57700"},
            headers=web_headers(cookie),
            timeout=15.0,
        )
        data = r.json()
        if data.get("code") != 0:
            return {"code": data.get("code", -1), "msg": data.get("message", "bookshelf error"), "data": None}
        shelf_data = data.get("data", {})
        shelf_items = shelf_data.get("book_shelf_info", []) or []
        if not shelf_items:
            return {"code": 200, "data": [], "msg": "success"}
        groups = {}
        group_order = []
        ungrouped = []
        for b in shelf_items:
            if not isinstance(b, dict) or not b.get("book_id"):
                continue
            gid = str(b.get("group_id", ""))
            gname = b.get("group_name", "")
            book_entry = {
                "book_id": str(b["book_id"]),
                "item_id": str(b.get("last_operate_time", "0")),
                "last_read_time": int(b.get("last_operate_time", 0) or 0),
                "add_time": int(b.get("add_shelf_time", 0) or 0),
            }
            if not gid:
                ungrouped.append(book_entry)
            else:
                if gid not in groups:
                    groups[gid] = {"group_id": gid, "group_name": gname, "books": []}
                    group_order.append(gid)
                groups[gid]["books"].append(book_entry)
        csrf = _extract_csrf(cookie)
        md_headers = {
            **web_headers(cookie),
            "Content-Type": "application/json",
            "x-secsdk-csrf-token": csrf,
            "origin": "https://fanqienovel.com",
        }

        progress_map = {}
        try:
            rp = await client.get(
                "https://fanqienovel.com/api/reader/book/progress",
                headers=web_headers(cookie),
                timeout=15.0,
            )
            pd = rp.json()
            if pd.get("code") == 0 and isinstance(pd.get("data"), list):
                for item in pd["data"]:
                    bid = str(item.get("book_id", ""))
                    if bid:
                        progress_map[bid] = {
                            "item_id": str(item.get("item_id", "0") or "0"),
                            "index": int(item.get("index", 0) or 0),
                            "read_timestamp": int(item.get("read_timestamp", 0) or 0),
                        }
        except Exception as e:
            logger.warning("fetch book progress 失败: %s", e, exc_info=True)

        def _make_book_obj(bid, binfo, gid, gname, add_time):
            prog = progress_map.get(str(bid), {})
            prog_item_id = prog.get("item_id", "0")
            prog_idx = prog.get("index", -1)
            prog_ts = prog.get("read_timestamp", 0)
            has_progress = prog_item_id and prog_item_id != "0" and prog_ts > 0
            return {
                "BookID": str(bid),
                "Name": binfo.get("book_name", "") if binfo else "",
                "ThumbUrl": binfo.get("thumb_url", "") if binfo else "",
                "Desc": binfo.get("abstract", "") if binfo else "",
                "ChapterCount": int(binfo.get("serial_count", 0) or 0) if binfo else 0,
                "Status": ("连载中" if str(binfo.get("creation_status")) == "1" else "已完结" if str(binfo.get("creation_status")) == "0" else "") if binfo else "",
                "GroupID": gid,
                "GroupName": gname,
                "LastReadChapter": binfo.get("item_show_title", "") if binfo and has_progress else "",
                "LastReadTime": prog_ts if has_progress else 0,
                "LastUpdateTime": int(binfo.get("last_chapter_update_time", 0) or 0) if binfo else 0,
                "UpdateStopped": str(binfo.get("update_stop", "0") or "0") == "1" if binfo else False,
                "AddTime": add_time,
                "ReadChapterIdx": prog_idx if has_progress else -1,
                "ReadItemId": prog_item_id if has_progress else "0",
            }

        result = []
        all_batches = []
        if ungrouped:
            all_batches.append(("", "", ungrouped))
        for gid in group_order:
            all_batches.append((gid, groups[gid]["group_name"], groups[gid]["books"]))

        for gid, gname, books in all_batches:
            books_payload = []
            for b in books:
                prog = progress_map.get(b["book_id"], {})
                pid = prog.get("item_id", "0") if prog else "0"
                books_payload.append({"book_id": b["book_id"], "item_id": pid})
            add_time_map = {b["book_id"]: b["add_time"] for b in books}
            try:
                rb = await client.post(
                    "https://fanqienovel.com/api/bookshelf/multidetail",
                    headers=md_headers,
                    json={"books": books_payload},
                    timeout=15.0,
                )
                bd = rb.json()
                if bd.get("code") == 0:
                    for binfo in bd.get("data", {}).get("detail_list", []):
                        bid = str(binfo.get("book_id", ""))
                        result.append(_make_book_obj(bid, binfo, gid, gname, add_time_map.get(bid, 0)))
                else:
                    for b in books:
                        result.append(_make_book_obj(b["book_id"], None, gid, gname, b["add_time"]))
            except Exception as e:
                logger.warning("bookshelf multidetail 失败 group=%s: %s", gid, e, exc_info=True)
                for b in books:
                    result.append(_make_book_obj(b["book_id"], None, gid, gname, b["add_time"]))
        return {"code": 200, "data": result, "msg": "success"}
    except Exception as e:
        logger.warning("get_user_bookshelf 请求失败: %s", e, exc_info=True)
        return {"code": 500, "msg": f"upstream error: {type(e).__name__}", "data": None}


@router.post("/api/user/progress")
async def update_read_progress(req: ProgressPayload):
    cookie = _require_cookie()
    if not cookie:
        return {"code": 401, "msg": "not logged in"}
    item_id = req.item_id or req.chapter_id
    if not item_id:
        return {"code": 400, "msg": "missing item_id or chapter_id"}
    chapter_idx = req.index or req.chapter_idx
    csrf = _extract_csrf(cookie)
    params = {
        "book_id": req.book_id,
        "item_id": item_id,
        "read_progress": chapter_idx,
        "index": chapter_idx,
        "read_timestamp": str(int(time.time())),
        "genre_type": 1,
    }
    try:
        r = await client.post(
            "https://fanqienovel.com/api/reader/book/update_progress",
            headers={
                **web_headers(cookie),
                "x-secsdk-csrf-token": csrf,
                "origin": "https://fanqienovel.com",
            },
            params=params,
            timeout=10.0,
        )
        data = r.json()
        if data.get("code") == 0:
            return {"code": 200, "msg": "success"}
        return {"code": data.get("code", -1), "msg": data.get("message", "failed")}
    except Exception as e:
        logger.warning("update_read_progress 失败: %s", e, exc_info=True)
        return {"code": 500, "msg": f"upstream error: {type(e).__name__}"}


@router.post("/api/user/bookshelf/add")
async def add_to_cloud_shelf(body: BookshelfTargetPayload):
    cookie = _require_cookie()
    if not cookie:
        return {"code": 401, "msg": "not logged in"}
    csrf = _extract_csrf(cookie)
    params = {
        "identify_data": [{
            "book_id": body.book_id,
            "book_type": 0,
            "asterisked": False,
            "modify_time": int(time.time() * 1000),
        }],
        "add_book_source": 0,
    }
    try:
        r = await client.post(
            "https://fanqienovel.com/reading/bookapi/bookshelf/add/v:version/",
            params={"aid": "1967", "iid": "0", "version_code": "57700", "update_version_code": "57700"},
            headers={
                **web_headers(cookie),
                "x-secsdk-csrf-token": csrf,
                "origin": "https://fanqienovel.com",
                "Content-Type": "application/json",
            },
            json=params,
            timeout=10.0,
        )
        data = r.json()
        if data.get("code") == 0:
            return {"code": 200, "msg": "success"}
        return {"code": data.get("code", -1), "msg": data.get("message", "failed")}
    except Exception as e:
        logger.warning("add_to_bookshelf 失败: %s", e, exc_info=True)
        return {"code": 500, "msg": f"upstream error: {type(e).__name__}"}


@router.post("/api/user/bookshelf/remove")
async def remove_from_cloud_shelf(body: BookshelfTargetPayload):
    cookie = _require_cookie()
    if not cookie:
        return {"code": 401, "msg": "not logged in"}
    csrf = _extract_csrf(cookie)
    params = {
        "identify_data": [{
            "book_id": body.book_id,
            "book_type": 0,
            "remove_type": 1,
            "modify_time": int(time.time() * 1000),
        }],
    }
    try:
        r = await client.post(
            "https://fanqienovel.com/reading/bookapi/bookshelf/delete/v:version/",
            headers={
                **web_headers(cookie),
                "x-secsdk-csrf-token": csrf,
                "origin": "https://fanqienovel.com",
                "Content-Type": "application/json",
            },
            json=params,
            timeout=10.0,
        )
        data = r.json()
        if data.get("code") == 0:
            return {"code": 200, "msg": "success"}
        return {"code": data.get("code", -1), "msg": data.get("message", "failed")}
    except Exception as e:
        logger.warning("remove_from_bookshelf 失败: %s", e, exc_info=True)
        return {"code": 500, "msg": f"upstream error: {type(e).__name__}"}


@router.post("/api/user/bookshelf/move")
async def move_cloud_shelf_book(body: MoveBookPayload):
    cookie = _require_cookie()
    if not cookie:
        return {"code": 401, "msg": "not logged in"}
    if not body.group_id and not body.group_name.strip():
        return {"code": 400, "msg": "missing group_id or group_name"}
    csrf = _extract_csrf(cookie)
    book_entry = {
        "book_id": body.book_id,
        "book_type": 0,
        "modify_time": int(time.time() * 1000),
    }
    if body.group_id:
        book_entry["group_id"] = body.group_id
    if body.group_name:
        book_entry["group_name"] = body.group_name
    params = {"book_data": [book_entry]}
    try:
        r = await client.post(
            "https://fanqienovel.com/reading/bookapi/bookshelf/update/v:version/",
            params={"aid": "1967", "iid": "0", "version_code": "57700"},
            headers={
                **web_headers(cookie),
                "x-secsdk-csrf-token": csrf,
                "origin": "https://fanqienovel.com",
                "Content-Type": "application/json",
            },
            json=params,
            timeout=10.0,
        )
        data = r.json()
        if data.get("code") == 0:
            return {"code": 200, "msg": "success"}
        return {"code": data.get("code", -1), "msg": data.get("message", "failed")}
    except Exception as e:
        logger.warning("move_cloud_shelf_book 失败: %s", e, exc_info=True)
        return {"code": 500, "msg": f"upstream error: {type(e).__name__}"}


def _extract_csrf(cookie: str) -> str:
    for part in cookie.split(";"):
        kv = part.strip().split("=", 1)
        if len(kv) == 2 and kv[0].strip() == "passport_csrf_token":
            return kv[1].strip()
    return ""
