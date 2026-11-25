import pytest
from fastapi.testclient import TestClient

from server import app


@pytest.fixture
def client():
    # Endpoint tests do not need the app's long-running static-file watcher.
    test_client = TestClient(app)
    yield test_client
    test_client.close()


def test_health(client):
    response = client.get("/api/health")

    assert response.status_code == 200
    assert response.json()["status"] == "ok"
    assert "no-cache" in response.headers.get("cache-control", "")


def test_version_no_cache(client):
    response = client.get("/api/version")

    assert response.status_code == 200
    assert "version" in response.json()
    assert "no-cache" in response.headers.get("cache-control", "")


def test_sw_js_no_cache(client):
    response = client.get("/sw.js")

    assert response.status_code == 200
    assert "no-cache" in response.headers.get("cache-control", "")
    assert response.headers.get("service-worker-allowed") == "/"


def test_index(client):
    response = client.get("/")

    assert response.status_code == 200
    assert 'id="app"' in response.text
    assert "no-cache" in response.headers.get("cache-control", "")


def test_static_js_no_cache(client):
    response = client.get("/static/js/app.js")

    assert response.status_code == 200
    assert "no-cache" in response.headers.get("cache-control", "")
