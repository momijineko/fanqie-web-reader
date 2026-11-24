"""Regression: caller-supplied ids must not escape fqapi path prefixes."""

from unittest.mock import AsyncMock, patch

import httpx
import pytest
from fastapi import HTTPException
from fastapi.testclient import TestClient

from server import app
from shared import _fqapi_safe_url, fqapi_get, require_resource_id


@pytest.fixture
def client():
    test_client = TestClient(app)
    yield test_client
    test_client.close()


@pytest.mark.parametrize(
    "value",
    [
        "../videos/123",
        "../../../admin",
        "123/../admin",
        "abc",
        "",
        "12a34",
        "%2e%2e/admin",
        "123456789012345678901234567890123",  # 33 digits
    ],
)
def test_require_resource_id_rejects_unsafe(value):
    with pytest.raises(HTTPException) as exc:
        require_resource_id(value, "book_id")
    assert exc.value.status_code == 400


def test_require_resource_id_accepts_numeric():
    assert require_resource_id("7123456789012345678", "book_id") == "7123456789012345678"


@pytest.mark.parametrize(
    "path",
    [
        "/api/v1/books/../admin",
        "/api/v1/chapters/123/../../admin",
        "/api/v1/books/12%2e%2e/admin",
        "/admin",
        "/api/v1/account/me",
        "api/v1/search",
        "/api/v1/books/not-a-number",
    ],
)
def test_fqapi_safe_url_rejects(path, monkeypatch):
    monkeypatch.setattr("shared.FQAPI_API", "http://127.0.0.1:18921")
    assert _fqapi_safe_url(path) is None


@pytest.mark.parametrize(
    "path",
    [
        "/api/v1/search",
        "/api/v1/books/7123456789012345678",
        "/api/v1/books/7123456789012345678/directory",
        "/api/v1/books/7123456789012345678/reviews",
        "/api/v1/chapters/8123456789012345678",
        "/api/v1/chapters/8123456789012345678/reviews",
    ],
)
def test_fqapi_safe_url_allows(path, monkeypatch):
    monkeypatch.setattr("shared.FQAPI_API", "http://127.0.0.1:18921")
    url = _fqapi_safe_url(path)
    assert url is not None
    assert isinstance(url, httpx.URL)
    assert url.path == path


@pytest.mark.asyncio
async def test_fqapi_get_does_not_call_http_for_traversal(monkeypatch):
    monkeypatch.setattr("shared.FQAPI_API", "http://127.0.0.1:18921")
    monkeypatch.setattr("shared.FQAPI_TOKEN", "secret-token")
    mock_get = AsyncMock()
    monkeypatch.setattr("shared.client", type("C", (), {"get": mock_get})())
    result = await fqapi_get("/api/v1/books/../admin")
    assert result is None
    mock_get.assert_not_called()


@pytest.mark.parametrize(
    "path,params",
    [
        ("/api/chapters", {"book_id": "../admin"}),
        ("/api/detail", {"book_id": "../../../videos/1"}),
        ("/api/content", {"chapter_id": "../admin", "book_id": "1"}),
        ("/api/content", {"chapter_id": "1", "book_id": "../admin"}),
    ],
)
def test_reader_routes_reject_path_traversal_ids(client, path, params):
    with patch("routers.books.fqapi_get", new_callable=AsyncMock) as mock_fq:
        response = client.get(path, params=params)
        assert response.status_code == 400
        mock_fq.assert_not_called()


def test_paragraph_counts_reject_path_traversal(client):
    with patch("routers.comments.fqapi_get", new_callable=AsyncMock) as mock_fq:
        response = client.post(
            "/api/paragraph_comment_counts",
            json={"chapter_id": "../admin", "book_id": "1"},
        )
        assert response.status_code == 400
        mock_fq.assert_not_called()
