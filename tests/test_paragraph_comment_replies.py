"""Paragraph comment replies are lazy-loaded via a dedicated endpoint."""

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


def test_paragraph_comments_list_omits_reply_bodies(client):
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
                        "reply_list": [
                            {
                                "common": {
                                    "content": {"text": "不应出现在列表"},
                                    "user_info": {"base_info": {"user_name": "x"}},
                                }
                            }
                        ],
                    }
                }
            ]
        },
    }

    async def fake_fqapi(path, params=None, timeout=20.0):
        if path.endswith("/reviews"):
            return list_payload
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
    assert body["data"][0]["comment_id"] == "555666777"
    assert "reply_list" not in body["data"][0]
    mock_fq.assert_awaited_once()


def test_paragraph_comment_replies_via_fqapi(client):
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
            "/api/paragraph_comment_replies",
            json={
                "comment_id": "555666777",
                "chapter_id": "7178482265998459407",
                "book_id": "7178473447670942780",
                "count": 10,
            },
        )

    assert response.status_code == 200
    body = response.json()
    assert body["code"] == 200
    assert body["data"][0]["content"] == "附议"
    assert body["data"][0]["user_name"] == "路人丙"
    mock_fq.assert_awaited_once()


def test_fqapi_safe_url_allows_comment_replies(monkeypatch):
    from shared import _fqapi_safe_url

    monkeypatch.setattr("shared.FQAPI_API", "http://127.0.0.1:18921")
    url = _fqapi_safe_url("/api/v1/comments/555666777/replies")
    assert url is not None
    assert url.path == "/api/v1/comments/555666777/replies"
