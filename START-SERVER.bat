@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
title Paper Minecraft Server 26.2

where java >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Java was not found. Install 64-bit Java 25 or newer.
  pause
  exit /b 1
)

if not exist "paper-26.2.jar" (
  echo Paper 26.2 is missing. Downloading the latest stable build...
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0DOWNLOAD-PAPER.ps1"
  if errorlevel 1 (
    echo [ERROR] Could not download Paper.
    pause
    exit /b 1
  )
)

if not exist "eula.txt" (
  echo eula=false>"eula.txt"
)

findstr /i /c:"eula=true" "eula.txt" >nul
if errorlevel 1 (
  echo.
  echo Minecraft EULA has not been accepted.
  echo Read https://aka.ms/MinecraftEULA
  echo If you agree, edit eula.txt and set eula=true.
  pause
  exit /b 0
)

echo Starting Paper 26.2...
java -Xms1G -Xmx4G -jar "paper-26.2.jar" --nogui
echo.
echo Server stopped.
pause
