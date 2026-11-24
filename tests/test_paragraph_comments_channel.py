"""fqapi paragraph comments must use server_channel=9 (not unidbg's 18)."""

from unittest.mock import AsyncMock, patch

import pytest
from fastapi.testclient import TestClient

from server import app


@pytest.fixture
def client():
    test_client = TestClient(app)
    yield test_client
    test_client.close()


def test_paragraph_comments_calls_fqapi_with_channel_9(client):
    fq_payload = {
        "code": 0,
        "data": {
            "data_list": [
                {
                    "comment": {
                        "comment_id": "1",
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
                        "stat": {"digg_count": 1},
                    }
                }
            ]
        },
    }

    with (
        patch("routers.comments.fqapi_get", new_callable=AsyncMock, return_value=fq_payload) as mock_fq,
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
    assert body["data"][0]["digg_count"] == 1
    mock_fq.assert_awaited_once()
    path, kwargs = mock_fq.await_args.args[0], mock_fq.await_args.kwargs
    assert path.endswith("/reviews")
    assert kwargs["params"]["server_channel"] == 9
    assert kwargs["params"]["comment_source"] == 2
    assert kwargs["params"]["para_index"] == 0
