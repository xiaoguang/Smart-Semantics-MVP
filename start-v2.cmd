@echo off
setlocal

cd /d "%~dp0"

echo.
echo ========================================
echo   Linguan Semantic Workbench V2
echo ========================================
echo.

where.exe node.exe >nul 2>&1
if errorlevel 1 (
  echo [ERROR] Node.js was not found.
  echo Install Node.js 20 or newer from https://nodejs.org/
  pause
  exit /b 1
)

where.exe npm.cmd >nul 2>&1
if errorlevel 1 (
  echo [ERROR] npm was not found. Check your Node.js installation.
  pause
  exit /b 1
)

if not exist "node_modules" (
  echo [SETUP] Installing dependencies...
  call npm.cmd install
  if errorlevel 1 (
    echo.
    echo [ERROR] Dependency installation failed.
    pause
    exit /b 1
  )
)

echo [START] Opening the V2 prototype in your browser...
echo [STOP] Close this window or press Ctrl+C to stop the server.
echo.

call npm.cmd run dev -- --open
set "START_EXIT_CODE=%ERRORLEVEL%"

if not "%START_EXIT_CODE%"=="0" (
  echo.
  echo [ERROR] The development server exited with code %START_EXIT_CODE%.
  pause
)

exit /b %START_EXIT_CODE%
