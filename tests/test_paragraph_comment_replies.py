"""Paragraph comments must attach nested replies from reply/list."""

from unittest.mock import AsyncMock, patch

import pytest
from fastapi.testclient import TestClient

from routers.comments import _normalize_comments, _normalize_replies
from server import app


@pytest.fixture
def client():
    test_client = TestClient(app)
    yield test_client
    test_client.close()


def test_normalize_comments_extracts_comment_id_and_reply_count():
    raw = {
        "code": 0,
        "data": {
            "data_list": [
                {
                    "comment": {
                        "comment_id": "991122",
                        "common": {
                            "content": {"text": "主评"},
                            "create_timestamp": 1700000000,
                            "user_info": {
                                "base_info": {
                                    "user_name": "读者甲",
                                    "user_avatar": "",
                                }
                            },
                        },
                        "stat": {"digg_count": 2, "reply_count": 4},
                    }
                }
            ]
        },
    }
    comments = _normalize_comments(raw)
    assert len(comments) == 1
    assert comments[0]["comment_id"] == "991122"
    assert comments[0]["reply_count"] == 4
    assert comments[0]["content"] == "主评"
    assert "reply_list" not in comments[0]


def test_normalize_replies_handles_capital_common():
    """Official reply payloads use ``Common`` (capital C)."""
    raw = {
        "code": 0,
        "data": {
            "reply_list": [
                {
                    "reply_id": "1",
                    "Common": {
                        "content": {"text": "楼中楼"},
                        "create_timestamp": 1700000100,
                        "user_info": {
                            "base_info": {
                                "user_name": "读者乙",
                                "user_avatar": "",
                            }
                        },
                    },
                    "stat": {"digg_count": 7},
                    "reply_to_user_info": {
                        "base_info": {"user_name": "读者甲"}
                    },
                }
            ]
        },
    }
    replies = _normalize_replies(raw)
    assert len(replies) == 1
    assert replies[0]["content"] == "楼中楼"
    assert replies[0]["user_name"] == "读者乙"
    assert replies[0]["digg_count"] == 7
    assert replies[0]["reply_to_user_name"] == "读者甲"


def test_paragraph_comments_enriches_replies_via_fqapi(client):
    list_payload = {
        "code": 0,
        "data": {
            "data_list": [
                {
                    "comment": {
                        "comment_id": "555666777",
                        "common": {
                            "content": {"text": "渝川好评"},
                            "create_timestamp": 1689424215,
                            "user_info": {
                                "base_info": {
                                    "user_name": "墨辰莫",
                                    "user_avatar": "",
                                }
                            },
                        },
                        "stat": {"digg_count": 1, "reply_count": 2},
                    }
                }
            ]
        },
    }
    reply_payload = {
        "code": 0,
        "data": {
            "reply_list": [
                {
                    "reply_id": "88",
                    "common": {
                        "content": {"text": "附议"},
                        "create_timestamp": 1689425000,
                        "user_info": {
                            "base_info": {
                                "user_name": "路人丙",
                                "user_avatar": "",
                            }
                        },
                    },
                    "stat": {"digg_count": 0},
                }
            ]
        },
    }

    async def fake_fqapi(path, params=None, timeout=20.0):
        if path.endswith("/reviews"):
            return list_payload
        if "/comments/" in path and path.endswith("/replies"):
            assert path.endswith("/comments/555666777/replies")
            assert params["book_id"] == "7178473447670942780"
            assert params["group_id"] == "7178482265998459407"
            return reply_payload
        return None

    with (
        patch("routers.comments.fqapi_get", new_callable=AsyncMock, side_effect=fake_fqapi) as mock_fq,
        patch("routers.comments.UNIDBG_API", ""),
    ):
        response = client.post(
            "/api/paragraph_comments",
            json={
                "chapter_id": "7178482265998459407",
                "book_id": "7178473447670942780",
                "paragraph_idx": 0,
                "count": 5,
            },
        )

    assert response.status_code == 200
    body = response.json()
    assert body["code"] == 200
    assert body["data"][0]["content"] == "渝川好评"
    assert body["data"][0]["reply_count"] == 2
    assert body["data"][0]["reply_list"][0]["content"] == "附议"
    assert body["data"][0]["reply_list"][0]["user_name"] == "路人丙"
    assert mock_fq.await_count == 2


def test_fqapi_safe_url_allows_comment_replies(monkeypatch):
    from shared import _fqapi_safe_url

    monkeypatch.setattr("shared.FQAPI_API", "http://127.0.0.1:18921")
    url = _fqapi_safe_url("/api/v1/comments/555666777/replies")
    assert url is not None
    assert url.path == "/api/v1/comments/555666777/replies"


def test_paragraph_comments_returns_parents_when_reply_enrich_times_out(client, monkeypatch):
    """Reply lookups must not starve the parent comment response past the client deadline."""
    import asyncio

    import routers.comments as comments_mod

    monkeypatch.setattr(comments_mod, "_REPLY_ENRICH_BUDGET_S", 0.05)

    list_payload = {
        "code": 0,
        "data": {
            "data_list": [
                {
                    "comment": {
                        "comment_id": "555666777",
                        "common": {
                            "content": {"text": "主评仍应返回"},
                            "create_timestamp": 1689424215,
                            "user_info": {"base_info": {"user_name": "墨辰莫", "user_avatar": ""}},
                        },
                        "stat": {"digg_count": 1, "reply_count": 2},
                    }
                }
            ]
        },
    }

    async def slow_fqapi(path, params=None, timeout=20.0):
        if path.endswith("/reviews"):
            return list_payload
        if "/comments/" in path and path.endswith("/replies"):
            await asyncio.sleep(2.0)
            return {"code": 0, "data": {"reply_list": []}}
        return None

    with (
        patch("routers.comments.fqapi_get", new_callable=AsyncMock, side_effect=slow_fqapi),
        patch("routers.comments.UNIDBG_API", ""),
    ):
        response = client.post(
            "/api/paragraph_comments",
            json={
                "chapter_id": "7178482265998459407",
                "book_id": "7178473447670942780",
                "paragraph_idx": 0,
                "count": 5,
            },
        )

    assert response.status_code == 200
    body = response.json()
    assert body["code"] == 200
    assert body["data"][0]["content"] == "主评仍应返回"
    assert body["data"][0].get("reply_list") in (None, [])
