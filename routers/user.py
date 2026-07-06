import time

import httpx
from fastapi import APIRouter, Body

from shared import COOKIE_FILE, load_cookie, save_cookie, web_headers

router = APIRouter()


@router.post("/api/user/cookie")
async def save_user_cookie(body: dict = Body(default={})):
    cookie = (body or {}).get("cookie", "").strip()
    if not cookie:
        return {"code": 400, "msg": "cookie is required"}
    save_cookie(cookie)
    return {"code": 200, "msg": "saved"}


@router.delete("/api/user/cookie")
async def delete_user_cookie():
    if COOKIE_FILE.exists():
        COOKIE_FILE.unlink()
    return {"code": 200, "msg": "deleted"}


@router.get("/api/user/info")
async def get_user_info():
    cdata = load_cookie()
    cookie = cdata.get("cookie", "") if isinstance(cdata, dict) else ""
    if not cookie:
        return {"code": 401, "msg": "not logged in", "data": None}
    try:
        async with httpx.AsyncClient(timeout=15.0, follow_redirects=True) as client:
            r = await client.get(
                "https://fanqienovel.com/api/user/info/v2",
                headers=web_headers(cookie),
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
        print(f"get_user_info ERROR: {e}")
    return {"code": 500, "msg": "request failed", "data": None}


@router.get("/api/user/bookshelf")
async def get_user_bookshelf():
    cdata = load_cookie()
    cookie = cdata.get("cookie", "") if isinstance(cdata, dict) else ""
    if not cookie:
        return {"code": 401, "msg": "not logged in", "data": None}
    try:
        async with httpx.AsyncClient(timeout=15.0, follow_redirects=True) as client:
            r = await client.get(
                "https://fanqienovel.com/reading/bookapi/bookshelf/info/v:version/",
                params={"aid": "1967", "iid": "0", "version_code": "57700", "update_version_code": "57700"},
                headers=web_headers(cookie),
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
                print(f"fetch book progress error: {e}")

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
                    print(f"bookshelf multidetail error for group {gid}: {e}")
                    for b in books:
                        result.append(_make_book_obj(b["book_id"], None, gid, gname, b["add_time"]))
            return {"code": 200, "data": result, "msg": "success"}
    except Exception as e:
        print(f"get_user_bookshelf ERROR: {e}")
    return {"code": 500, "msg": "request failed", "data": None}


@router.post("/api/user/progress")
async def update_read_progress(req: dict):
    cdata = load_cookie()
    cookie = cdata.get("cookie", "") if isinstance(cdata, dict) else ""
    if not cookie:
        return {"code": 401, "msg": "not logged in"}
    book_id = str(req.get("book_id", ""))
    item_id = str(req.get("item_id", req.get("chapter_id", "")))
    if not book_id or not item_id:
        return {"code": 400, "msg": "missing book_id or item_id"}
    chapter_idx = int(req.get("index", req.get("chapter_idx", 0)) or 0)
    csrf = _extract_csrf(cookie)
    params = {
        "book_id": book_id,
        "item_id": item_id,
        "read_progress": chapter_idx,
        "index": chapter_idx,
        "read_timestamp": str(int(time.time())),
        "genre_type": 1,
    }
    try:
        async with httpx.AsyncClient(timeout=10.0, follow_redirects=True) as client:
            r = await client.post(
                "https://fanqienovel.com/api/reader/book/update_progress",
                headers={
                    **web_headers(cookie),
                    "x-secsdk-csrf-token": csrf,
                    "origin": "https://fanqienovel.com",
                },
                params=params,
            )
            data = r.json()
            if data.get("code") == 0:
                return {"code": 200, "msg": "success"}
            return {"code": data.get("code", -1), "msg": data.get("message", "failed")}
    except Exception as e:
        print(f"update_read_progress ERROR: {e}")
    return {"code": 500, "msg": "request failed"}


@router.post("/api/user/bookshelf/add")
async def add_to_cloud_shelf(body: dict = Body(default={})):
    cdata = load_cookie()
    cookie = cdata.get("cookie", "") if isinstance(cdata, dict) else ""
    if not cookie:
        return {"code": 401, "msg": "not logged in"}
    book_id = str(body.get("book_id", ""))
    if not book_id:
        return {"code": 400, "msg": "missing book_id"}
    csrf = _extract_csrf(cookie)
    params = {
        "identify_data": [{
            "book_id": book_id,
            "book_type": 0,
            "asterisked": False,
            "modify_time": int(time.time() * 1000),
        }],
        "add_book_source": 0,
    }
    try:
        async with httpx.AsyncClient(timeout=10.0, follow_redirects=True) as client:
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
            )
            data = r.json()
            if data.get("code") == 0:
                return {"code": 200, "msg": "success"}
            return {"code": data.get("code", -1), "msg": data.get("message", "failed")}
    except Exception as e:
        print(f"add_to_bookshelf ERROR: {e}")
    return {"code": 500, "msg": "request failed"}


@router.post("/api/user/bookshelf/remove")
async def remove_from_cloud_shelf(body: dict = Body(default={})):
    cdata = load_cookie()
    cookie = cdata.get("cookie", "") if isinstance(cdata, dict) else ""
    if not cookie:
        return {"code": 401, "msg": "not logged in"}
    book_id = str(body.get("book_id", ""))
    if not book_id:
        return {"code": 400, "msg": "missing book_id"}
    csrf = _extract_csrf(cookie)
    params = {
        "identify_data": [{
            "book_id": book_id,
            "book_type": 0,
            "remove_type": 1,
            "modify_time": int(time.time() * 1000),
        }],
    }
    try:
        async with httpx.AsyncClient(timeout=10.0, follow_redirects=True) as client:
            r = await client.post(
                "https://fanqienovel.com/reading/bookapi/bookshelf/delete/v:version/",
                headers={
                    **web_headers(cookie),
                    "x-secsdk-csrf-token": csrf,
                    "origin": "https://fanqienovel.com",
                    "Content-Type": "application/json",
                },
                json=params,
            )
            data = r.json()
            if data.get("code") == 0:
                return {"code": 200, "msg": "success"}
            return {"code": data.get("code", -1), "msg": data.get("message", "failed")}
    except Exception as e:
        print(f"remove_from_bookshelf ERROR: {e}")
    return {"code": 500, "msg": "request failed"}


@router.post("/api/user/bookshelf/move")
async def move_cloud_shelf_book(body: dict = Body(default={})):
    cdata = load_cookie()
    cookie = cdata.get("cookie", "") if isinstance(cdata, dict) else ""
    if not cookie:
        return {"code": 401, "msg": "not logged in"}
    book_id = str(body.get("book_id", ""))
    group_id = str(body.get("group_id", ""))
    group_name = str(body.get("group_name", "")).strip()
    if not book_id:
        return {"code": 400, "msg": "missing book_id"}
    if not group_id and not group_name:
        return {"code": 400, "msg": "missing group_id or group_name"}
    csrf = _extract_csrf(cookie)
    book_entry = {
        "book_id": book_id,
        "book_type": 0,
        "modify_time": int(time.time() * 1000),
    }
    if group_id:
        book_entry["group_id"] = group_id
    if group_name:
        book_entry["group_name"] = group_name
    params = {"book_data": [book_entry]}
    try:
        async with httpx.AsyncClient(timeout=10.0, follow_redirects=True) as client:
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
            )
            data = r.json()
            if data.get("code") == 0:
                return {"code": 200, "msg": "success"}
            return {"code": data.get("code", -1), "msg": data.get("message", "failed")}
    except Exception as e:
        print(f"move_cloud_shelf_book ERROR: {e}")
    return {"code": 500, "msg": "request failed"}


def _extract_csrf(cookie: str) -> str:
    for part in cookie.split(";"):
        kv = part.strip().split("=", 1)
        if len(kv) == 2 and kv[0].strip() == "passport_csrf_token":
            return kv[1].strip()
    return ""
