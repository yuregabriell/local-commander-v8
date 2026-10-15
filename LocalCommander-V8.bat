@echo off
setlocal EnableExtensions EnableDelayedExpansion
title Local Commander V8 Portable
cd /d "%~dp0"

set "LC_HOME=%~dp0"
set "RUNTIME=%LC_HOME%runtime"
set "PYDIR=%RUNTIME%\python"
set "PY=%PYDIR%\python.exe"
set "PYZIP=%RUNTIME%\python-3.12.10-embed-amd64.zip"
set "GETPIP=%RUNTIME%\get-pip.py"
set "SETUP_FLAG=%RUNTIME%\setup-portable.ok"

if exist "%PY%" if exist "%SETUP_FLAG%" goto START

cls
echo ============================================================
echo            LOCAL COMMANDER V8 - PORTABLE
echo ============================================================
echo.
echo Primeira execucao.
echo Nada sera instalado no Windows.
echo O Python e as dependencias ficarao somente nesta pasta.
echo.

if not exist "%RUNTIME%" mkdir "%RUNTIME%"

if not exist "%PY%" (
    echo [1/6] Baixando Python portatil 3.12.10 x64...
    if exist "%PYZIP%" del /q "%PYZIP%" >nul 2>nul

    where curl.exe >nul 2>nul
    if not errorlevel 1 (
        curl.exe -L --fail --retry 2 -o "%PYZIP%" "https://www.python.org/ftp/python/3.12.10/python-3.12.10-embed-amd64.zip"
    )

    if not exist "%PYZIP%" (
        powershell.exe -NoProfile -ExecutionPolicy Bypass -Command ^
          "$ErrorActionPreference='Stop'; [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -UseBasicParsing 'https://www.python.org/ftp/python/3.12.10/python-3.12.10-embed-amd64.zip' -OutFile '%PYZIP%'"
    )

    if not exist "%PYZIP%" goto DOWNLOAD_ERROR

    echo [2/6] Extraindo runtime local...
    if exist "%PYDIR%" rmdir /s /q "%PYDIR%"
    mkdir "%PYDIR%"

    powershell.exe -NoProfile -ExecutionPolicy Bypass -Command ^
      "Expand-Archive -LiteralPath '%PYZIP%' -DestinationPath '%PYDIR%' -Force"
    if errorlevel 1 goto SETUP_ERROR
)

echo [3/6] Habilitando site-packages no Python portatil...
for %%F in ("%PYDIR%\python*._pth") do (
    powershell.exe -NoProfile -ExecutionPolicy Bypass -Command ^
      "$p='%%~fF'; $c=Get-Content -LiteralPath $p; $c=$c -replace '#import site','import site'; Set-Content -LiteralPath $p -Value $c -Encoding ASCII"
)

if not exist "%GETPIP%" (
    echo [4/6] Preparando pip local...
    where curl.exe >nul 2>nul
    if not errorlevel 1 (
        curl.exe -L --fail --retry 2 -o "%GETPIP%" "https://bootstrap.pypa.io/get-pip.py"
    )
    if not exist "%GETPIP%" (
        powershell.exe -NoProfile -ExecutionPolicy Bypass -Command ^
          "$ErrorActionPreference='Stop'; [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -UseBasicParsing 'https://bootstrap.pypa.io/get-pip.py' -OutFile '%GETPIP%'"
    )
)
if not exist "%GETPIP%" goto DOWNLOAD_ERROR

"%PY%" -m pip --version >nul 2>nul
if errorlevel 1 (
    "%PY%" "%GETPIP%" --no-warn-script-location
    if errorlevel 1 goto SETUP_ERROR
)

echo [5/6] Instalando dependencias somente nesta pasta...
"%PY%" -m pip install --disable-pip-version-check --no-warn-script-location -r "%LC_HOME%requirements.txt"
if errorlevel 1 (
    echo.
    echo AVISO: alguma dependencia opcional falhou.
    echo O agente basico ainda pode funcionar.
)

echo [6/6] Preparando navegador automatizado...
set "PLAYWRIGHT_BROWSERS_PATH=%RUNTIME%\ms-playwright"
"%PY%" -m playwright install chromium >nul 2>nul
if errorlevel 1 (
    echo AVISO: Chromium do Playwright nao foi instalado agora.
    echo O Local Commander continuara com os outros recursos.
)

> "%SETUP_FLAG%" echo ready
if not exist "%RUNTIME%\first_run.flag" > "%RUNTIME%\first_run.flag" echo first
timeout /t 1 /nobreak >nul

:START
cls
set "PLAYWRIGHT_BROWSERS_PATH=%RUNTIME%\ms-playwright"
"%PY%" "%LC_HOME%app\main.py"
set "RC=%ERRORLEVEL%"
if "%RC%"=="20" (
    cls
    timeout /t 1 /nobreak >nul
    goto START
)
exit /b %RC%

:DOWNLOAD_ERROR
echo.
echo ============================================================
echo NAO CONSEGUI BAIXAR O RUNTIME PORTATIL
echo ============================================================
echo.
echo Verifique internet, antivirus/firewall e tente novamente.
echo Nenhum Python foi instalado no Windows.
echo.
start "" "%LC_HOME%docs\conectar.html"
pause
exit /b 2

:SETUP_ERROR
echo.
echo ============================================================
echo ERRO DURANTE O SETUP PORTATIL
echo ============================================================
echo.
echo Tudo fica dentro desta pasta.
echo Se quiser recomecar, apague:
echo   runtime\python
echo   runtime\setup-portable.ok
echo e abra o BAT novamente.
echo.
start "" "%LC_HOME%docs\conectar.html"
pause
exit /b 1
