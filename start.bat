@echo off
rem Fanqie Web Reader - local launcher (Windows)
rem Env vars: SKIP_UNIDBG=1 skip unidbg service; SKIP_JRE=1 skip JRE auto-download
rem NOTE: keep this file ASCII-only, cmd.exe mis-parses UTF-8 Chinese on some machines.
cd /d "%~dp0"

rem ---------- Load .env (existing env vars take precedence) ----------
rem Must run BEFORE "setlocal enabledelayedexpansion" so values containing "!" stay intact.
if exist ".env" (
  for /f "usebackq eol=# tokens=1,* delims==" %%a in (".env") do (
    if not "%%b"=="" if not defined %%a set "%%a=%%~b"
  )
  echo [start] Loaded .env config.
)

setlocal enabledelayedexpansion

set UNIDBG_VERSION=0.0.6
set UNIDBG_SHA256=505fa249c37365cde4bcaafcd433cc6e8af83fdfb1948a5b145ddc5a45293cfb
set UNIDBG_PORT=8099
set SERVER_PORT=8080
set JAR=unidbg\unidbg-boot-server-%UNIDBG_VERSION%.jar

rem ---------- Proxy env notice ----------
rem Python upstream clients ignore HTTP_PROXY etc. (trust_env=False); all upstreams go direct.
if defined HTTP_PROXY echo [note] Proxy env var HTTP_PROXY detected - intentionally ignored, upstream connections go direct.
if defined HTTPS_PROXY echo [note] Proxy env var HTTPS_PROXY detected - intentionally ignored, upstream connections go direct.
if defined ALL_PROXY echo [note] Proxy env var ALL_PROXY detected - intentionally ignored, upstream connections go direct.

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

rem ---------- Java runtime & unidbg ----------
set HAVE_UNIDBG=1
set "JAVA_CMD=java"
if "%SKIP_UNIDBG%"=="1" (
  echo [warn] SKIP_UNIDBG=1, skipping unidbg.
  set HAVE_UNIDBG=0
  goto :after_unidbg
)

if exist "jre\bin\java.exe" (
  set "JAVA_CMD=jre\bin\java.exe"
  goto :java_ready
)

where java >nul 2>nul || goto :java_missing
rem PATH java found: reject 21+ (incompatible with unidbg)
set "JVER="
for /f tokens^=2^ delims^=^" %%v in ('java -version 2^>^&1 ^| findstr /c:"version"') do set "JVER=%%v"
set "JMAJOR="
for /f "delims=. tokens=1" %%m in ("!JVER!") do set "JMAJOR=%%m"
if not defined JMAJOR set "JMAJOR=0"
if "!JMAJOR!"=="1" set "JMAJOR=8"
if !JMAJOR! GEQ 21 (
  echo [warn] PATH Java is !JVER! - 21+ incompatible with unidbg, downloading Temurin 17 instead.
  goto :java_download
)
goto :java_ready

:java_missing
if "%SKIP_JRE%"=="1" (
  echo [warn] SKIP_JRE=1 and no Java found, skipping unidbg.
  set HAVE_UNIDBG=0
  goto :after_unidbg
)

:java_download
echo [start] Downloading Temurin 17 JRE (~45MB) ...
mkdir .jre_tmp 2>nul
curl -fL --retry 3 --connect-timeout 15 -o ".jre_tmp\jre.zip" "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jre/hotspot/normal/eclipse?project=jdk"
if errorlevel 1 (
  echo [start] Adoptium direct failed, trying mirror ...
  curl -s --retry 2 --connect-timeout 15 -o ".jre_tmp\asset.json" "https://api.adoptium.net/v3/assets/latest/17/hotspot?os=windows&architecture=x64&image_type=jre&vendor=eclipse"
  echo $j = ConvertFrom-Json (Get-Content -Raw '.jre_tmp\asset.json'^); Write-Output $j[0].binary.package.link > ".jre_tmp\get.ps1"
  set "PKGLINK="
  for /f "usebackq delims=" %%u in (`powershell -NoProfile -ExecutionPolicy Bypass -File ".jre_tmp\get.ps1"`) do set "PKGLINK=%%u"
  if defined PKGLINK (
    curl -fL --retry 3 -o ".jre_tmp\jre.zip" "https://gh-proxy.com/!PKGLINK!"
    if errorlevel 1 curl -fL --retry 3 -o ".jre_tmp\jre.zip" "!PKGLINK!"
  )
)
if not exist ".jre_tmp\jre.zip" (
  echo [warn] JRE download failed. Install manually, e.g. winget install EclipseAdoptium.Temurin.17.JRE
  rd /s /q .jre_tmp 2>nul
  set HAVE_UNIDBG=0
  goto :after_unidbg
)
echo [start] Extracting JRE ...
tar -xf ".jre_tmp\jre.zip" -C ".jre_tmp" 2>nul || powershell -NoProfile -Command "Expand-Archive -Force '.jre_tmp\jre.zip' '.jre_tmp'"
for /d %%d in (.jre_tmp\*) do if not exist "jre" move "%%d" "jre" >nul
rd /s /q .jre_tmp 2>nul
if exist "jre\bin\java.exe" set "JAVA_CMD=jre\bin\java.exe"
%JAVA_CMD% -version >nul 2>nul || (
  echo [warn] Downloaded JRE failed to run.
  set "JAVA_CMD="
  set HAVE_UNIDBG=0
  goto :after_unidbg
)

:java_ready
echo [start] Java runtime: %JAVA_CMD%

curl -s --noproxy "*" --max-time 2 "http://127.0.0.1:%UNIDBG_PORT%/api/fqnovel/health" 2>nul | find /i "UP" >nul && (
  echo [start] unidbg already running on port %UNIDBG_PORT%, skip.
  set HAVE_UNIDBG=0
  goto :after_unidbg
)

if not exist "%JAR%" (
  echo [start] Downloading fqnovel-unidbg v%UNIDBG_VERSION% ...
  mkdir unidbg 2>nul
  curl -fL --retry 3 --connect-timeout 15 -o "%JAR%" "https://gh-proxy.com/https://github.com/mtongle/fqnovel-unidbg/releases/download/v%UNIDBG_VERSION%/unidbg-boot-server-%UNIDBG_VERSION%.jar"
  if errorlevel 1 curl -fL --retry 3 --connect-timeout 15 -o "%JAR%" "https://github.com/mtongle/fqnovel-unidbg/releases/download/v%UNIDBG_VERSION%/unidbg-boot-server-%UNIDBG_VERSION%.jar"
  if errorlevel 1 (
    echo [fail] Jar download failed. Check network, or download manually to %JAR%
    goto :end_fail
  )
)

echo [start] Verifying jar SHA256 ...
certutil -hashfile "%JAR%" SHA256 | find /i "%UNIDBG_SHA256%" >nul || (
  echo [fail] Jar checksum mismatch. Delete %JAR% and retry.
  goto :end_fail
)

echo [start] Launching unidbg in a new window, health check up to 3 min ...
start "fqnovel-unidbg" /min cmd /k %JAVA_CMD% -Dfile.encoding=UTF-8 -jar "%JAR%"

set /a TRIES=0
:wait_unidbg
set /a TRIES+=1
if !TRIES! GTR 60 goto :unidbg_timeout
curl -s --noproxy "*" --max-time 2 "http://127.0.0.1:%UNIDBG_PORT%/api/fqnovel/health" 2>nul | find /i "UP" >nul && goto :unidbg_ok
timeout /t 3 /nobreak >nul
goto :wait_unidbg

:unidbg_ok
echo [start] unidbg ready at http://127.0.0.1:%UNIDBG_PORT%
goto :after_unidbg
:unidbg_timeout
echo [warn] unidbg not ready in time, see the fqnovel-unidbg window log. Falling back to community API.

rem ---------- Web service ----------
:after_unidbg
curl -s --noproxy "*" --max-time 2 "http://127.0.0.1:%SERVER_PORT%/api/health" 2>nul | find /i "ok" >nul && (
  echo [fail] Port %SERVER_PORT% already in use: http://localhost:%SERVER_PORT%
  goto :end_fail
)

start "" http://localhost:%SERVER_PORT%
echo [start] Web service at http://localhost:%SERVER_PORT%  (Ctrl+C to stop)
echo [note]  If unidbg was launched, stop it by closing the "fqnovel-unidbg" window.
.venv\Scripts\python.exe server.py
goto :eof

:end_fail
pause
