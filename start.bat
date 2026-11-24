@echo off
rem Fanqie Web Reader - local launcher (Windows)
rem Env vars: SKIP_FQAPI=1 skip fqapi and fall back to community API
rem NOTE: keep this file ASCII-only, cmd.exe mis-parses UTF-8 Chinese on some machines.
cd /d "%~dp0"

rem ---------- Load .env (existing env vars take precedence) ----------
if exist ".env" (
  for /f "usebackq eol=# tokens=1,* delims==" %%a in (".env") do (
    if not "%%b"=="" if not defined %%a set "%%a=%%~b"
  )
  echo [start] Loaded .env config.
)

setlocal enabledelayedexpansion

set FQAPI_REF=v0.2.0
set FQAPI_PORT=18921
set SERVER_PORT=8080
set FQAPI_BIN=fqapi\fqapi-server.exe
if not exist "%FQAPI_BIN%" set FQAPI_BIN=fqapi\fqapi-server

where curl >nul 2>nul || (echo [fail] curl is required & goto :end_fail)

rem ---------- Python & venv ----------
set PYEXE=
py -3 --version >nul 2>&1 && set PYEXE=py -3
if not defined PYEXE (
  python --version >nul 2>&1 && set PYEXE=python
)
if not defined PYEXE (
  echo [fail] Python not found. Please install Python 3.10+ first.
  goto :end_fail
)

if not exist ".venv\Scripts\python.exe" (
  echo [start] Creating venv .venv ...
  %PYEXE% -m venv .venv || goto :end_fail
)
if not exist ".venv\.deps_done" (
  echo [start] Installing requirements.txt ...
  .venv\Scripts\python.exe -m pip install -q -r requirements.txt || goto :end_fail
  type nul > .venv\.deps_done
)

rem ---------- Ensure FQAPI_TOKEN ----------
if not defined FQAPI_TOKEN (
  for /f %%t in ('powershell -NoProfile -Command "[Convert]::ToHexString((1..24 | ForEach-Object { Get-Random -Maximum 256 }) -as [byte[]]).ToLower()"') do set "FQAPI_TOKEN=%%t"
  if not defined FQAPI_TOKEN set "FQAPI_TOKEN=devlocaltokenpleasechange01"
  if exist ".env" (
    findstr /b /c:"FQAPI_TOKEN=" ".env" >nul 2>nul
    if errorlevel 1 (
      echo FQAPI_TOKEN=!FQAPI_TOKEN!>>.env
    ) else (
      powershell -NoProfile -Command "(Get-Content '.env') -replace '^FQAPI_TOKEN=.*','FQAPI_TOKEN=!FQAPI_TOKEN!' | Set-Content '.env'"
    )
  ) else (
    copy /y .env.example .env >nul 2>nul
    if exist ".env" (
      powershell -NoProfile -Command "(Get-Content '.env') -replace '^FQAPI_TOKEN=.*','FQAPI_TOKEN=!FQAPI_TOKEN!' | Set-Content '.env'"
    ) else (
      > .env echo FQAPI_TOKEN=!FQAPI_TOKEN!
      >> .env echo FQAPI_API=http://127.0.0.1:!FQAPI_PORT!
    )
  )
  echo [start] Generated FQAPI_TOKEN and wrote .env
)

rem ---------- fqapi ----------
set HAVE_FQAPI=1
if "%SKIP_FQAPI%"=="1" (
  echo [warn] SKIP_FQAPI=1, skipping fqapi.
  set HAVE_FQAPI=0
  goto :after_fqapi
)

curl -s --noproxy "*" --max-time 2 "http://127.0.0.1:%FQAPI_PORT%/healthz" | findstr /c:"status" >nul 2>nul
if not errorlevel 1 (
  echo [start] fqapi already running on port %FQAPI_PORT%, skip start.
  goto :after_fqapi
)

if not exist "%FQAPI_BIN%" (
  echo [warn] %FQAPI_BIN% not found.
  echo [warn] On Windows, prefer: docker compose up -d fqapi
  echo [warn] Or build with Rust and place binary at fqapi\fqapi-server.exe
  echo [warn] Continuing with community API fallback. Set SKIP_FQAPI=1 to silence this.
  set HAVE_FQAPI=0
  goto :after_fqapi
)

if not defined FQAPI_API set "FQAPI_API=http://127.0.0.1:%FQAPI_PORT%"
mkdir fqapi\data 2>nul
echo [start] Starting fqapi in background ...
start "fqapi" /MIN "%FQAPI_BIN%" --token "%FQAPI_TOKEN%" --data-dir "fqapi\data" --listen "127.0.0.1:%FQAPI_PORT%"
set OK=0
for /l %%i in (1,1,40) do (
  curl -s --noproxy "*" --max-time 2 "http://127.0.0.1:%FQAPI_PORT%/readyz" | findstr /c:"\"status\":\"ok\"" >nul 2>nul
  if not errorlevel 1 (
    set OK=1
    goto :fqapi_ready
  )
  timeout /t 2 /nobreak >nul
)
:fqapi_ready
if "%OK%"=="1" (
  echo [start] fqapi ready.
) else (
  curl -s --noproxy "*" --max-time 2 "http://127.0.0.1:%FQAPI_PORT%/healthz" | findstr /c:"status" >nul 2>nul
  if not errorlevel 1 (
    echo [warn] fqapi started but device pool still warming up.
  ) else (
    echo [warn] fqapi not ready, continuing with community API fallback.
  )
)

:after_fqapi
rem ---------- Web server ----------
curl -s --noproxy "*" --max-time 2 "http://127.0.0.1:%SERVER_PORT%/api/health" | findstr /c:"ok" >nul 2>nul
if not errorlevel 1 (
  echo [fail] Port %SERVER_PORT% already in use.
  goto :end_fail
)

if not defined FQAPI_API set "FQAPI_API=http://127.0.0.1:%FQAPI_PORT%"
start "" "http://localhost:%SERVER_PORT%"
echo [start] Starting web server at http://localhost:%SERVER_PORT%
.venv\Scripts\python.exe server.py
goto :eof

:end_fail
echo.
pause
exit /b 1
