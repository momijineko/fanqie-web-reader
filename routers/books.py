import re
import time
from collections import OrderedDict
from pathlib import Path
from typing import Any

from fastapi import APIRouter, Query
from fastapi.responses import FileResponse, JSONResponse

from shared import UNIDBG_API, _fanqie_client, client, community_get, logger, resolve_version

router = APIRouter()

_CONTENT_TTL = 300
_DETAIL_TTL = 3600
_CACHE_MAX = 500
_cache: OrderedDict[str, tuple[float, Any]] = OrderedDict()

_STATUS_MAP = {"1": "连载中", "0": "已完结"}


def _cache_get(key: str, ttl: float) -> Any | None:
    entry = _cache.get(key)
    if entry and time.time() - entry[0] < ttl:
        _cache.move_to_end(key)
        return entry[1]
    if entry:
        _cache.pop(key, None)
    return None


def _cache_set(key: str, value: Any) -> None:
    _cache[key] = (time.time(), value)
    _cache.move_to_end(key)
    while len(_cache) > _CACHE_MAX:
        _cache.popitem(last=False)


@router.get("/sw.js")
async def sw_js():
    path = Path("static/sw.js")
    if path.exists():
        return FileResponse(
            path,
            media_type="application/javascript",
            headers={"Service-Worker-Allowed": "/"},
        )
    return JSONResponse(status_code=404, content={"error": "not found"})


@router.get("/api/health")
async def health():
    return {"status": "ok", "version": resolve_version()}


@router.get("/api/version")
async def version():
    return {"version": resolve_version()}


@router.get("/")
async def index():
    return FileResponse("static/index.html")


async def _unidbg_get(path: str, params: dict | None = None) -> dict | None:
    """请求 unidbg 服务；成功（code==0）时返回 data 载荷，任何失败返回 None 以便回退下一数据源。"""
    try:
        r = await _fanqie_client.get(f"{UNIDBG_API}{path}", params=params)
        data = r.json()
        if data.get("code") == 0:
            return data.get("data") or {}
        logger.debug("unidbg %s 返回失败: code=%s msg=%s", path, data.get("code"), data.get("message"))
    except Exception as e:
        logger.info("unidbg %s 不可用: %s: %s", path, type(e).__name__, e)
    return None


def _html_paragraphs(content_html: str) -> list[str]:
    """按 <p> 块提取段落文本，剥离除 <img> 外的全部内嵌标签。

    兼容两种上游格式：社区 API 的 <p>纯文本</p>，以及 unidbg 的
    <p><blk>文本</blk></p>（文本还包在 <blk> 内嵌标签里）。
    保留 <img> 是为了插图类章节——reader.js 会识别纯图片段落并渲染。
    """
    content_html = content_html or ""
    paras = []
    for m in re.finditer(r'<p[^>]*>(.*?)</p>', content_html, re.S):
        text = re.sub(r'<(?!img\b)[^>]+>', '', m.group(1)).strip()
        if text:
            paras.append(text)
    if paras:
        return paras
    # 无 <p> 结构时整体剥标签（保留 <img>）、按行分段
    text = re.sub(r'<(?!img\b)[^>]+>', '\n', content_html)
    return [l.strip() for l in text.split('\n') if l.strip()]


# ====== 搜索 ======

def _map_unidbg_book(item: dict) -> dict:
    tags = item.get("tagsStr") or ""
    if not tags and isinstance(item.get("tags"), list):
        tags = ",".join(item["tags"])
    return {
        "BookID": item.get("bookId", ""),
        "Name": item.get("bookName", ""),
        "Author": item.get("author", ""),
        "Desc": item.get("description", ""),
        "ThumbUrl": item.get("coverUrl", ""),
        "ChapterCount": item.get("totalChapters", ""),
        "Category": item.get("category", ""),
        "Score": item.get("rating") or item.get("score") or "",
        "WordCount": item.get("wordCount", 0),
        "Status": _STATUS_MAP.get(str(item.get("creationStatus", "")), ""),
        "Tags": tags,
        "ReadCount": item.get("readCount", 0),
    }


@router.get("/api/search")
async def search(key: str = Query(...), tab_type: int = 3, offset: int = 0):
    d = await _unidbg_get(
        "/api/fqsearch/books",
        params={"query": key, "tabType": tab_type, "offset": offset, "count": 20},
    )
    if d is not None:
        books = [_map_unidbg_book(b) for b in d.get("books") or [] if isinstance(b, dict)]
        return {"code": 200, "data": books, "has_more": bool(d.get("hasMore")), "msg": "success"}

    data = await community_get("/api/search", {"key": key, "tab_type": tab_type, "offset": offset})
    if data is None:
        logger.warning("search 所有数据源均失败")
        return JSONResponse(status_code=502, content={"code": 502, "msg": "all sources unavailable", "data": []})
    raw = data.get("data", {})
    if isinstance(raw, dict):
        tabs = raw.get("search_tabs", [])
        has_more = any(t.get("has_more") for t in tabs if isinstance(t, dict))
        books = _extract_books_from_tabs(tabs)
        if books:
            return {"code": 200, "data": books, "has_more": has_more, "msg": "success"}
    return {"code": 200, "data": [], "msg": "no results"}


def _extract_books_from_tabs(tabs: list) -> list:
    """Extract normalized book dicts from upstream search_tabs."""
    books = []
    for tab in tabs:
        if not tab or not isinstance(tab, dict):
            continue
        items = tab.get("data", [])
        if not items:
            continue
        for item in items:
            if not item or not isinstance(item, dict):
                continue
            bd_raw = item.get("book_data")
            if not bd_raw or not isinstance(bd_raw, list):
                continue
            bd = bd_raw[0] if bd_raw else {}
            if not bd or not isinstance(bd, dict):
                continue
            bid = bd.get("book_id", "")
            if bid:
                stat = bd.get("creation_status", "")
                books.append({
                    "BookID": bid,
                    "Name": bd.get("book_name", ""),
                    "Author": bd.get("author", ""),
                    "Desc": bd.get("abstract", ""),
                    "ThumbUrl": bd.get("thumb_url", bd.get("audio_thumb_uri", "")),
                    "ChapterCount": bd.get("chapter_number", ""),
                    "Category": bd.get("category", ""),
                    "Score": bd.get("score", ""),
                    "WordCount": bd.get("word_number", 0),
                    "Status": _STATUS_MAP.get(str(stat), ""),
                    "Tags": bd.get("tags", ""),
                    "ReadCount": bd.get("read_count", 0),
                })
    return books


@router.get("/api/author_books")
async def author_books(author_id: str = Query(...)):
    """Fetch all books by a specific author via official Fanqie API."""
    try:
        r = await client.get(
            "https://api5-normal-sinfonlinec.fqnovel.com/reading/user/basic_info/get/v",
            params={"user_id": author_id, "aid": "1967", "version_code": "65532"},
            headers={"User-Agent": "com.dragon.read/6.5.3.32.3 (Android 9)"},
        )
        data = r.json()
        if data.get("code") != 0:
            return {"code": 200, "data": [], "msg": "no results"}

        author_data = data.get("data", {})
        raw_books = author_data.get("praise_info", {}).get("praise_book_list", [])
        if not raw_books:
            raw_books = author_data.get("author_book_info", [])
        books = []
        for bd in raw_books:
            if not bd or not isinstance(bd, dict):
                continue
            bid = bd.get("book_id", "")
            if bid:
                stat = str(bd.get("creation_status", ""))
                books.append({
                    "BookID": bid,
                    "Name": bd.get("book_name", ""),
                    "ShortName": bd.get("book_short_name", ""),
                    "Author": bd.get("author", ""),
                    "Desc": bd.get("abstract", ""),
                    "ThumbUrl": bd.get("thumb_url", bd.get("audio_thumb_uri", "")),
                    "ChapterCount": bd.get("serial_count", ""),
                    "Category": bd.get("category", ""),
                    "Score": bd.get("score", ""),
                    "WordCount": bd.get("word_number", 0),
                    "Status": _STATUS_MAP.get(stat, ""),
                    "Tags": bd.get("tags", ""),
                    "ReadCount": bd.get("read_count", 0),
                    "ReadCountText": bd.get("read_cnt_text", ""),
                })

        return {
            "code": 200,
            "data": books,
            "author_name": author_data.get("user_name", ""),
            "author_avatar": author_data.get("user_avatar", ""),
            "author_desc": author_data.get("description", ""),
            "author_fans": author_data.get("fans_num", 0),
            "author_book_num": author_data.get("author_book_num", len(books)),
            "is_author": author_data.get("is_author", False),
            "msg": "success",
        }
    except Exception as e:
        logger.warning("author_books 请求失败: %s", e, exc_info=True)
        return JSONResponse(status_code=502, content={"code": 502, "msg": f"upstream error: {type(e).__name__}", "data": []})


# ====== 章节目录 ======

@router.get("/api/chapters")
async def chapters(book_id: str = Query(...)):
    d = await _unidbg_get(f"/api/fqsearch/directory/{book_id}")
    if d is not None:
        result = _map_unidbg_directory(d)
        if result:
            return {"code": 200, "data": result, "msg": "success", "total": len(result)}
        return {"code": 200, "data": [], "msg": "no chapters"}

    data = await community_get("/api/book", {"book_id": book_id})
    if data is None:
        logger.warning("chapters 所有数据源均失败 book_id=%s", book_id)
        return JSONResponse(status_code=502, content={"code": 502, "msg": "all sources unavailable", "data": []})
    outer = data.get("data", {})
    inner = outer.get("data", {})
    ids = inner.get("allItemIds", [])
    vols = inner.get("chapterListWithVolume", [])

    result = []
    for vi, vol in enumerate(vols):
        if isinstance(vol, list):
            for ch in vol:
                result.append({
                    "ChapterID": ch.get("itemId", ""),
                    "Name": ch.get("title", ""),
                    "Order": ch.get("realChapterOrder", ""),
                    "UpdateTime": ch.get("firstPassTime", 0),
                })
        elif isinstance(vol, dict):
            ch_list = vol.get("chapterList", [])
            for ch in ch_list:
                result.append({
                    "ChapterID": ch.get("chapterId", ch.get("itemId", "")),
                    "Name": ch.get("chapterTitle", ch.get("title", "")),
                    "UpdateTime": ch.get("firstPassTime", ch.get("publishTime", 0)),
                })

    if result:
        return {"code": 200, "data": result, "msg": "success", "total": len(result)}

    if ids:
        result = [{"ChapterID": cid, "Name": f"第{i+1}章"} for i, cid in enumerate(ids)]
        return {"code": 200, "data": result, "msg": "success", "total": len(result)}
    return {"code": 200, "data": [], "msg": "no chapters"}


def _uget(obj: dict, *keys):
    """unidbg 各接口的 JSON 命名风格不统一（有的 camelCase 有的 snake_case），依次取首个非空值。"""
    for k in keys:
        v = obj.get(k)
        if v is not None:
            return v
    return None


def _map_unidbg_directory(d: dict) -> list:
    result = []
    for ch in _uget(d, "itemDataList", "item_data_list") or []:
        if not isinstance(ch, dict):
            continue
        item_id = _uget(ch, "itemId", "item_id")
        if not item_id:
            continue
        order = _uget(ch, "chapterIndex", "chapter_index")
        result.append({
            "ChapterID": item_id,
            "Name": _uget(ch, "title") or f"第{len(result) + 1}章",
            "Order": order if order is not None else "",
            "UpdateTime": _uget(ch, "firstPassTime", "first_pass_time") or 0,
            "volume_name": _uget(ch, "volumeName", "volume_name") or "",
        })
    if result:
        return result
    for i, c in enumerate(_uget(d, "catalogData", "catalog_data") or []):
        if isinstance(c, dict) and _uget(c, "itemId", "item_id"):
            result.append({
                "ChapterID": _uget(c, "itemId", "item_id"),
                "Name": _uget(c, "catalogTitle", "catalog_title") or f"第{i + 1}章",
                "Order": i,
                "UpdateTime": 0,
                "volume_name": "",
            })
    return result


# ====== 章节正文 ======

@router.get("/api/content")
async def content(chapter_id: str = Query(...), book_id: str = Query(default="")):
    cache_key = f"content:{chapter_id}"
    cached = _cache_get(cache_key, _CONTENT_TTL)
    if cached is not None:
        return cached

    # 数据源 1：unidbg 签名接口（需要 book_id）
    if book_id:
        d = await _unidbg_get(f"/api/fqnovel/chapter/{book_id}/{chapter_id}")
        if d is not None:
            paragraphs = _html_paragraphs(_uget(d, "rawContent", "raw_content") or "")
            if not paragraphs:
                paragraphs = [l for l in (_uget(d, "txtContent", "txt_content") or "").split('\n') if l.strip()]
            if paragraphs:
                result = {
                    "code": 200,
                    "data": {
                        "ChapterID": chapter_id,
                        "Title": _uget(d, "title") or "",
                        "Paragraphs": paragraphs,
                        "AuthorSpeak": "",
                    },
                    "msg": "success",
                }
                _cache_set(cache_key, result)
                return result
            logger.warning("unidbg 正文为空 chapter_id=%s，回退社区 API", chapter_id)

    # 数据源 2：社区 API（多实例轮询）
    r_data = await community_get("/api/raw_full", {"item_id": chapter_id})
    if r_data is not None:
        raw = r_data.get("data", {})
        content_html = raw.get("content", "")
        author_speak = raw.get("author_speak", "")
        title = raw.get("title", "")
        paragraphs = _html_paragraphs(content_html)

        result = {
            "code": 200,
            "data": {
                "ChapterID": chapter_id,
                "Title": title,
                "Paragraphs": paragraphs,
                "AuthorSpeak": author_speak,
            },
            "msg": "success",
        }
        _cache_set(cache_key, result)
        return result

    r_data = await community_get("/api/content", {"tab": "小说", "item_id": chapter_id})
    if r_data is not None:
        text = r_data.get("data", {}).get("content", "")
        if text:
            paragraphs = [l for l in text.split('\n') if l.strip()]
            result = {
                "code": 200,
                "data": {
                    "ChapterID": chapter_id,
                    "Paragraphs": paragraphs,
                    "AuthorSpeak": "",
                },
                "msg": "success",
            }
            _cache_set(cache_key, result)
            return result

    logger.warning("content 所有数据源均失败 chapter_id=%s", chapter_id)
    return JSONResponse(status_code=503, content={"code": 503, "msg": "Content unavailable"})


# ====== 书籍详情 ======

def _to_int(v) -> int:
    try:
        return int(str(v).strip())
    except (ValueError, TypeError):
        return 0


@router.get("/api/detail")
async def detail(book_id: str = Query(...)):
    cache_key = f"detail:{book_id}"
    cached = _cache_get(cache_key, _DETAIL_TTL)
    if cached is not None:
        return cached

    d = await _unidbg_get(f"/api/fqnovel/book/{book_id}")
    if d is not None:
        book = {
            "book_id": d.get("bookId", book_id),
            "book_name": d.get("bookName", ""),
            "title": d.get("bookName", ""),
            "author": d.get("author", ""),
            "writer": d.get("author", ""),
            "author_id": d.get("authorId", ""),
            "bind_author_ids": d.get("bindAuthorIds", ""),
            "abstract": d.get("description", ""),
            "description": d.get("description", ""),
            "thumb_url": d.get("coverUrl", ""),
            "audio_thumb_uri": d.get("audioThumbUri", ""),
            "category": d.get("category", ""),
            "chapter_number": d.get("totalChapters") or "",
            "word_number": _to_int(d.get("wordNumber")),
            "score": d.get("score", ""),
            "original_book_name": d.get("modifiedReputationBookName", ""),
            "book_flight_alias_name": d.get("bookFlightAliasName", ""),
            "creation_status": str(d.get("creationStatus", "")),
            "tags": d.get("tags", ""),
            "read_count": d.get("readCount", ""),
            "read_cnt_text": d.get("readCntText", ""),
            "first_chapter_item_id": d.get("firstChapterItemId", ""),
            "last_chapter_item_id": d.get("lastChapterItemId", ""),
            "last_chapter_title": d.get("lastChapterTitle", ""),
            "flight_flag": str(d.get("flightFlag") or ""),
        }
        # 飞行书（改名推广）：详情显示推广名/推广封面，原名与原封面从作者书单解析。
        # unidbg 的 modifiedReputationBookName 对飞行书常为空，bindReputationBookId 指向的是空壳条目。
        if book["flight_flag"] == "1" and book["author_id"]:
            try:
                r = await client.get(
                    "https://api5-normal-sinfonlinec.fqnovel.com/reading/user/basic_info/get/v",
                    params={"user_id": book["author_id"], "aid": "1967", "version_code": "65532"},
                    headers={"User-Agent": "com.dragon.read/6.5.3.32.3 (Android 9)"},
                )
                data = r.json().get("data", {})
                raw_books = data.get("praise_info", {}).get("praise_book_list") or data.get("author_book_info") or []
                for bd in raw_books:
                    if not isinstance(bd, dict) or str(bd.get("book_id")) != str(book_id):
                        continue
                    if bd.get("book_name") and bd["book_name"] != book["book_name"]:
                        book["original_book_name"] = bd["book_name"]
                    if bd.get("thumb_url"):
                        book["original_thumb_url"] = bd["thumb_url"]
                    break
            except Exception as e:
                logger.info("飞行书原名解析失败 book_id=%s: %s: %s", book_id, type(e).__name__, e)
        result = {"code": 200, "data": {**book, "data": book}, "msg": "success"}
        _cache_set(cache_key, result)
        return result

    data = await community_get("/api/detail", {"book_id": book_id})
    if data is None:
        logger.warning("detail 所有数据源均失败 book_id=%s", book_id)
        return JSONResponse(status_code=502, content={"code": 502, "msg": "all sources unavailable", "data": {}})
    _cache_set(cache_key, data)
    return data
