import hashlib
import time

from fastapi import APIRouter, Body, Query

from shared import COMMUNITY_API, PARA_COMMENT_MOCK, UNIDBG_API, _fanqie_client, client

router = APIRouter()


@router.get("/api/comments")
async def comments(
    book_id: str = Query(...),
    chapter_id: str = Query(default=""),
    offset: int = 0,
    count: int = 20,
):
    try:
        params = {"book_id": book_id, "offset": offset, "count": count}
        if chapter_id:
            params["chapter_id"] = chapter_id
        r = await client.get(
            f"{COMMUNITY_API}/api/comment",
            params=params,
        )
        data = r.json()
        if data.get("code") == 200:
            inner = data.get("data") or {}
            if isinstance(inner, dict):
                d2 = inner.get("data") or inner
                if isinstance(d2, dict):
                    clist = d2.get("comment")
                    if isinstance(clist, list):
                        d2["comment"] = [_normalize_book_comment(c) for c in clist]
            return data
    except Exception:
        pass
    return {"code": 200, "data": [], "msg": "no comments"}


def _normalize_book_comment(c: dict) -> dict:
    """Flatten Fanqie book-level comment into user_name/avatar_url/content/reply_list shape."""
    if not isinstance(c, dict):
        return c
    ui = c.get("user_info") or {}
    user_name = ""
    avatar_url = ""
    if isinstance(ui, dict):
        user_name = ui.get("user_name") or ui.get("nick_name") or ""
        avatar_url = ui.get("user_avatar") or ui.get("avatar_url") or ""
    content = c.get("text") or c.get("content") or ""
    ts = c.get("create_timestamp") or c.get("create_time") or 0
    if isinstance(ts, (int, float)) and ts > 1_000_000_000_000:
        ts = ts // 1000
    digg = c.get("digg_count") or 0
    out = dict(c)
    out["user_name"] = user_name or c.get("user_name") or "匿名"
    out["avatar_url"] = _fix_img_url(avatar_url or c.get("avatar_url") or "")
    out["content"] = content
    out["create_time"] = ts
    out["digg_count"] = digg
    replies = c.get("reply_list") or c.get("reply_comment") or c.get("child_comments") or []
    if isinstance(replies, list) and replies:
        out["reply_list"] = [_normalize_book_comment(rc) for rc in replies if isinstance(rc, dict)]
    return out


@router.post("/api/paragraph_comment_counts")
async def paragraph_comment_counts(request_body: dict = Body(default={})):
    """Get paragraph comment counts. Mock mode for local dev, unidbg proxy for production."""
    body = request_body or {}
    chapter_id = body.get("chapter_id", "")
    book_id = body.get("book_id", "")
    if not chapter_id:
        return {"code": 400, "data": {}, "msg": "chapter_id required"}

    if PARA_COMMENT_MOCK:
        return {"code": 200, "data": _mock_para_counts(chapter_id), "msg": "success (mock)"}

    try:
        req_body = {"chapterId": chapter_id, "commentSource": 2, "serverChannel": 17, "groupType": 15}
        if book_id:
            req_body["bookId"] = book_id
        r = await _fanqie_client.post(
            f"{UNIDBG_API}/api/fqcomment/idea",
            json=req_body,
            timeout=20.0,
        )
        data = r.json()
        counts = _extract_para_counts(data)
        return {"code": 200, "data": counts, "msg": "success"}
    except Exception as e:
        print(f"paragraph_comment_counts ERROR: {type(e).__name__}: {e}")
    return {"code": 200, "data": {}, "msg": "no data"}


def _mock_para_counts(chapter_id: str) -> dict:
    """Generate mock paragraph comment counts for local development."""
    seed = int(hashlib.md5(chapter_id.encode()).hexdigest()[:8], 16)
    counts = {}
    positions = [0, 3, 7, 12]
    for i, pos in enumerate(positions):
        cnt = (seed >> (i * 4) & 0xF) % 8 + 1
        counts[str(pos)] = cnt
    return counts


def _extract_para_counts(raw) -> dict:
    """Extract {para_index: count} mapping from various API response formats."""
    def _from_obj(obj):
        out = {}
        for k, v in obj.items():
            try:
                idx = int(k)
            except (ValueError, TypeError):
                continue
            if isinstance(v, dict):
                cnt = v.get("count") or v.get("comment_count") or 0
                if isinstance(cnt, (int, float)) and cnt > 0:
                    out[str(idx)] = cnt
            elif isinstance(v, (int, float)) and v > 0:
                out[str(idx)] = v
        return out

    def _from_array(arr):
        out = {}
        for item in arr:
            if not isinstance(item, dict):
                continue
            idx = item.get("para_index") or item.get("para_idx") or item.get("index") or 0
            cnt = item.get("count") or item.get("comment_count") or 0
            try:
                idx = int(idx)
                cnt = int(cnt)
            except (ValueError, TypeError):
                continue
            if idx >= 0 and cnt > 0:
                out[str(idx)] = cnt
        return out

    candidates_obj = [raw]
    if isinstance(raw, dict):
        candidates_obj.append(raw.get("data"))
        candidates_obj.append(raw.get("paras"))
        d1 = raw.get("data")
        if isinstance(d1, dict):
            candidates_obj.append(d1.get("data"))
            candidates_obj.append(d1.get("paras"))
            d2 = d1.get("data")
            if isinstance(d2, dict):
                candidates_obj.append(d2.get("data"))
                candidates_obj.append(d2)

    for obj in candidates_obj:
        if isinstance(obj, dict) and obj:
            result = _from_obj(obj)
            if result:
                return result

    candidates_arr = []
    if isinstance(raw, dict):
        for key in ["data_list", "list", "idea_list", "ideas"]:
            candidates_arr.append(raw.get(key))
        if isinstance(raw.get("data"), dict):
            for key in ["data_list", "list"]:
                candidates_arr.append(raw["data"].get(key))
        if isinstance(raw.get("detail"), dict):
            for key in ["data_list", "list"]:
                candidates_arr.append(raw["detail"].get(key))

    for arr in candidates_arr:
        if isinstance(arr, list) and arr:
            result = _from_array(arr)
            if result:
                return result

    return {}


@router.post("/api/paragraph_comments")
async def paragraph_comments(request_body: dict = Body(default={})):
    """Get paragraph comments. Mock mode for local dev, unidbg proxy for production."""
    body = request_body or {}
    chapter_id = body.get("chapter_id", "")
    book_id = body.get("book_id", "")
    paragraph_idx = body.get("paragraph_idx", 0)
    count = body.get("count", 20)
    if not chapter_id:
        return {"code": 400, "data": [], "msg": "chapter_id required"}

    if PARA_COMMENT_MOCK:
        return {"code": 200, "data": _mock_para_comments(chapter_id, paragraph_idx), "msg": "success (mock)"}

    try:
        r = await _fanqie_client.post(
            f"{UNIDBG_API}/api/fqcomment/list",
            json={
                "chapterId": chapter_id,
                "bookId": book_id,
                "paraIndex": paragraph_idx,
                "commentSource": 2,
                "commentType": 1,
                "serverChannel": 18,
                "groupType": 15,
                "count": count,
            },
            timeout=20.0,
        )
        data = r.json()
        comments = _normalize_comments(data)
        return {"code": 200, "data": comments, "msg": "success"}
    except Exception as e:
        print(f"paragraph_comments ERROR: {e}")
    return {"code": 200, "data": [], "msg": "no paragraph comments"}


def _mock_para_comments(chapter_id: str, paragraph_idx: int) -> list:
    """Generate mock paragraph comments for local development."""
    counts = _mock_para_counts(chapter_id)
    if str(paragraph_idx) not in counts:
        return []

    now = int(time.time())
    users = [
        {"name": "书虫小王", "avatar": "https://i.pravatar.cc/80?img=1"},
        {"name": "夜读人", "avatar": "https://i.pravatar.cc/80?img=2"},
        {"name": "墨染书香", "avatar": "https://i.pravatar.cc/80?img=3"},
        {"name": "浮生若梦", "avatar": "https://i.pravatar.cc/80?img=4"},
        {"name": "阅尽千帆", "avatar": "https://i.pravatar.cc/80?img=5"},
    ]
    texts = [
        "这段写得太好了，画面感十足！",
        "作者文笔真的不错，细腻入微",
        "看到这里忍不住笑出了声",
        "剧情转折好突然，完全没想到",
        "这段描写很真实，感同身受",
        "伏笔埋得好深，前面就有暗示了",
        "这角色塑造得很有层次感",
        "节奏把握得恰到好处，不拖沓",
    ]
    seed = int(hashlib.md5(f"{chapter_id}_{paragraph_idx}".encode()).hexdigest()[:8], 16)
    num_comments = (seed % 5) + 1
    result = []
    for i in range(num_comments):
        user_idx = (seed + i) % len(users)
        text_idx = (seed + i * 3) % len(texts)
        comment = {
            "user_name": users[user_idx]["name"],
            "avatar_url": users[user_idx]["avatar"],
            "content": texts[text_idx],
            "create_time": now - (i + 1) * 3600 * ((seed % 24) + 1),
            "digg_count": (seed + i) % 50,
        }
        if i == 0 and seed % 4 == 0:
            comment["images"] = ["https://picsum.photos/seed/para{}_{}/300/200".format(paragraph_idx, i)]
        if i == 0 and seed % 3 == 0:
            reply_user = users[(user_idx + 2) % len(users)]
            comment["reply_list"] = [
                {
                    "user_name": reply_user["name"],
                    "avatar_url": reply_user["avatar"],
                    "content": "同感！",
                    "create_time": comment["create_time"] + 1800,
                    "digg_count": 3,
                }
            ]
        result.append(comment)
    return result


def _fix_img_url(url: str) -> str:
    """Convert ByteDance HEIC avatar URLs to browser-friendly JPEG."""
    if not url:
        return url
    if ".heic" in url.lower():
        url = url.replace(".heic", ".jpeg").replace(".HEIC", ".jpeg")
    return url


def _normalize_comments(raw) -> list:
    """Normalize comment data from various API response formats."""
    review_candidates = []

    def _collect_from(node, depth=0):
        if depth > 4:
            return
        if isinstance(node, list):
            review_candidates.append(node)
            return
        if not isinstance(node, dict):
            return
        for key in ["reviews", "list", "data_list", "comment_list", "comments", "ideas"]:
            v = node.get(key)
            if isinstance(v, list):
                review_candidates.append(v)
            elif isinstance(v, dict):
                _collect_from(v, depth + 1)
        for key in ["data", "detail", "response", "result", "comment_data", "common_comments"]:
            v = node.get(key)
            if isinstance(v, (dict, list)):
                _collect_from(v, depth + 1)

    _collect_from(raw)

    reviews = []
    for cand in review_candidates:
        if cand and len(cand) > len(reviews):
            reviews = cand

    if not reviews:
        return []

    result = []
    for item in reviews:
        if not isinstance(item, dict):
            continue

        comment_obj = item.get("comment") or item.get("comment_info") or item
        common = {}
        if isinstance(comment_obj, dict):
            common = comment_obj.get("common") or {}

        user_name = ""
        avatar_url = ""
        def _u(d):
            nonlocal user_name, avatar_url
            if not isinstance(d, dict):
                return
            if not user_name:
                user_name = d.get("user_name") or d.get("name") or d.get("nick_name") or ""
            if not avatar_url:
                avatar_url = d.get("user_avatar") or d.get("avatar_url") or d.get("avatar") or d.get("head_url") or ""
        _u(item.get("user"))
        _u(item.get("user_info"))
        _u(item.get("user_info", {}).get("base_info"))
        _u(common.get("user_info"))
        _u(common.get("user_info", {}).get("base_info"))
        if not user_name:
            user_name = item.get("user_name") or item.get("nick_name") or item.get("name") or "匿名"
        if not avatar_url:
            avatar_url = item.get("avatar_url") or item.get("avatar") or item.get("head_url") or ""

        text = ""
        content = common.get("content") or {}
        if isinstance(content, dict):
            text = content.get("text") or content.get("content") or ""
        if not text and isinstance(comment_obj, dict):
            text = comment_obj.get("text") or comment_obj.get("content") or ""
        if not text:
            text = item.get("text") or item.get("content") or item.get("comment_text") or item.get("reply_text") or ""

        digg_count = (
            item.get("digg_count") or item.get("like_count") or item.get("digg") or 0
        )
        stat = common.get("digg_count") or common.get("like_count")
        if stat:
            digg_count = stat

        ts = 0
        for tsrc in [
            common.get("create_timestamp"),
            comment_obj.get("create_timestamp") if isinstance(comment_obj, dict) else None,
            item.get("created_ts"),
            item.get("create_timestamp"),
            item.get("create_time"),
            item.get("ctime"),
        ]:
            if isinstance(tsrc, (int, float)) and tsrc > 0:
                ts = tsrc
                break
        if isinstance(ts, (int, float)) and ts > 1_000_000_000_000:
            ts = ts // 1000

        comment = {
            "user_name": user_name,
            "avatar_url": _fix_img_url(avatar_url),
            "content": text,
            "create_time": ts,
            "digg_count": digg_count,
        }

        images = item.get("image_list") or item.get("pic_list") or item.get("images") or []
        if not images and isinstance(common.get("content"), dict):
            img_data = common["content"].get("image_data_list") or {}
            if isinstance(img_data, dict) and img_data:
                images = list(img_data.values())
        if isinstance(images, dict):
            images = list(images.values())
        if isinstance(images, str):
            images = [images]
        if images and isinstance(images, list):
            img_urls = []
            for img in images:
                if isinstance(img, str) and img:
                    img_urls.append(_fix_img_url(img))
                elif isinstance(img, dict):
                    url = img.get("url") or img.get("origin_url") or img.get("thumb_url") or img.get("web_url") or ""
                    if url:
                        img_urls.append(_fix_img_url(url))
            if img_urls:
                comment["images"] = img_urls

        replies = item.get("reply_list") or item.get("replies") or item.get("sub_comments") or []
        if not replies and isinstance(common.get("reply_list"), (list, dict)):
            replies = common["reply_list"]
        if isinstance(replies, dict):
            replies = list(replies.values())
        if replies and isinstance(replies, list):
            comment["reply_list"] = []
            for rc in replies[:5]:
                if not isinstance(rc, dict):
                    continue
                rc_cmt = rc.get("comment") or rc.get("comment_info") or rc
                rc_common = rc_cmt.get("common", {}) if isinstance(rc_cmt, dict) else {}
                rc_user = ""
                rc_avatar = ""
                def _ru(d):
                    nonlocal rc_user, rc_avatar
                    if not isinstance(d, dict): return
                    if not rc_user:
                        rc_user = d.get("user_name") or d.get("name") or d.get("nick_name") or ""
                    if not rc_avatar:
                        rc_avatar = d.get("user_avatar") or d.get("avatar_url") or d.get("avatar") or ""
                _ru(rc.get("user"))
                _ru(rc_common.get("user_info"))
                _ru(rc_common.get("user_info", {}).get("base_info"))
                if not rc_user:
                    rc_user = rc.get("user_name") or rc.get("nick_name") or "匿名"
                rc_text = ""
                rc_content = rc_common.get("content") or {}
                if isinstance(rc_content, dict):
                    rc_text = rc_content.get("text") or ""
                if not rc_text:
                    rc_text = rc.get("text") or rc.get("content") or ""
                rc_ts = 0
                for rts in [rc_common.get("create_timestamp"), rc.get("create_timestamp"), rc.get("create_time"), rc.get("created_ts")]:
                    if isinstance(rts, (int, float)) and rts > 0:
                        rc_ts = rts
                        break
                if isinstance(rc_ts, (int, float)) and rc_ts > 1_000_000_000_000:
                    rc_ts = rc_ts // 1000
                rc_digg = rc_common.get("digg_count") or rc.get("digg_count") or rc.get("like_count") or 0
                comment["reply_list"].append({
                    "user_name": rc_user,
                    "avatar_url": _fix_img_url(rc_avatar),
                    "content": rc_text,
                    "create_time": rc_ts,
                    "digg_count": rc_digg,
                })

        result.append(comment)
    return result
