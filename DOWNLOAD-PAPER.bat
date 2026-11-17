@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
title Download Paper 26.2

where java >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Java was not found. Install 64-bit Java 25 or newer.
  pause
  exit /b 1
)

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0DOWNLOAD-PAPER.ps1"
if errorlevel 1 (
  echo.
  echo [ERROR] Paper download failed. See the message above.
  pause
  exit /b 1
)
echo.
pause
