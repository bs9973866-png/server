@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0INSTALL-RP-ADDONS.ps1"
if errorlevel 1 (
  echo.
  echo Addon installation failed. Read the error above.
  pause
  exit /b 1
)
echo.
pause
