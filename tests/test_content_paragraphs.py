"""fqapi content must not cache raw HTML as Paragraphs."""

from unittest.mock import AsyncMock, patch

import pytest
from fastapi.testclient import TestClient

from routers.books import _html_paragraphs
from server import app


@pytest.fixture
def client():
    test_client = TestClient(app)
    yield test_client
    test_client.close()


def test_html_paragraphs_strips_tags():
    html = "<h1>Title</h1><p>第一段<em>强调</em></p><p>第二段</p>"
    assert _html_paragraphs(html) == ["第一段强调", "第二段"]


def test_html_paragraphs_empty_markup_only():
    assert _html_paragraphs("<div><span></span></div>") == []


def test_content_falls_through_when_fqapi_html_has_no_text(client):
    """Empty-after-strip fqapi HTML must not be newline-split/cached; try next source."""
    fqapi_payload = {
        "code": 0,
        "data": {"content": "<div class='wrap'><span></span></div>", "title": "x"},
    }
    community_payload = {
        "code": 200,
        "data": {"content": "纯文本第一行\n纯文本第二行"},
    }

    with (
        patch("routers.books.fqapi_get", new_callable=AsyncMock, return_value=fqapi_payload),
        patch("routers.books.unidbg_get", new_callable=AsyncMock, return_value=None),
        patch("routers.books.community_get", new_callable=AsyncMock) as mock_community,
    ):
        # /api/raw_full first, then /api/content
        mock_community.side_effect = [None, community_payload]
        response = client.get(
            "/api/content",
            params={"chapter_id": "8123456789012345678", "book_id": "7123456789012345678"},
        )

    assert response.status_code == 200
    body = response.json()
    assert body["code"] == 200
    paras = body["data"]["Paragraphs"]
    assert paras == ["纯文本第一行", "纯文本第二行"]
    assert not any("<" in p for p in paras)
