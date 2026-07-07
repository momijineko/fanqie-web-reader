import asyncio
import logging
import os
from contextlib import asynccontextmanager
from pathlib import Path
from typing import AsyncGenerator

from fastapi import FastAPI, WebSocket
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles

from shared import client, cors_origins, _fanqie_client, logger
from routers import books, comments, user

logging.basicConfig(
    level=os.environ.get("LOG_LEVEL", "INFO").upper(),
    format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
)

_hot_clients: set[WebSocket] = set()


@asynccontextmanager
async def lifespan(_app: FastAPI) -> AsyncGenerator:
    watch_task = asyncio.create_task(_watch_static())
    yield
    watch_task.cancel()
    await client.aclose()
    await _fanqie_client.aclose()


app = FastAPI(title="Fanqie Web Reader", lifespan=lifespan)

_origins = cors_origins()
app.add_middleware(
    CORSMiddleware,
    allow_origins=_origins,
    allow_credentials=("*" not in _origins),
    allow_methods=["*"],
    allow_headers=["*"],
)
logger.info("CORS origins: %s (credentials=%s)", _origins, "*" not in _origins)

app.mount("/static", StaticFiles(directory="static"), name="static")

app.include_router(books.router)
app.include_router(comments.router)
app.include_router(user.router)


async def _watch_static():
    try:
        from watchfiles import awatch
    except ImportError:
        logger.warning("watchfiles 未安装，回退到 mtime 轮询")
        await _poll_static()
        return

    async for _changes in awatch("static"):
        for _kind, path in _changes:
            rel = Path(path).as_posix()
            msg = {"type": "css" if rel.endswith(".css") else "reload", "path": rel}
            for ws in list(_hot_clients):
                try:
                    await ws.send_json(msg)
                except Exception:
                    _hot_clients.discard(ws)


async def _poll_static():
    mtimes: dict[str, float] = {}
    for root, _, files in os.walk("static"):
        for f in files:
            p = os.path.join(root, f)
            try:
                mtimes[p] = os.path.getmtime(p)
            except OSError:
                pass
    while True:
        await asyncio.sleep(1)
        for root, _, files in os.walk("static"):
            for f in files:
                p = os.path.join(root, f)
                try:
                    m = os.path.getmtime(p)
                except OSError:
                    continue
                if mtimes.get(p) != m:
                    mtimes[p] = m
                    rel = Path(p).as_posix()
                    msg = {"type": "css" if rel.endswith(".css") else "reload", "path": rel}
                    for ws in list(_hot_clients):
                        try:
                            await ws.send_json(msg)
                        except Exception:
                            _hot_clients.discard(ws)


@app.websocket("/ws/hot")
async def hot_ws(ws: WebSocket):
    await ws.accept()
    _hot_clients.add(ws)
    try:
        while True:
            await ws.receive_text()
    except Exception:
        _hot_clients.discard(ws)


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("server:app", host="0.0.0.0", port=8080, reload=False)
