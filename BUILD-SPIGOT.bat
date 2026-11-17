@echo off
chcp 65001 >nul
setlocal
title Spigot 26.2 - BuildTools
cd /d "%~dp0"

echo ==========================================
echo          SPIGOT 26.2 BUILDER
echo ==========================================
echo.

where java >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Java not found.
  echo Install 64-bit Java 25 or newer, then run this file again.
  pause
  exit /b 1
)

echo [1/3] Checking Java...
java -version
echo.

if not exist "BuildTools.jar" (
  echo [2/3] Downloading official Spigot BuildTools...
  curl.exe -fL --retry 3 "https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar" -o "BuildTools.jar"
  if errorlevel 1 (
    echo [ERROR] Could not download BuildTools.jar.
    pause
    exit /b 1
  )
) else (
  echo [2/3] BuildTools.jar already exists.
)

echo.
echo [3/3] Building Spigot 26.2. This can take several minutes...
java -jar "BuildTools.jar" --rev 26.2
if errorlevel 1 (
  echo.
  echo [ERROR] BuildTools failed. Check the messages above.
  pause
  exit /b 1
)

if exist "spigot-26.2.jar" (
  echo.
  echo [SUCCESS] spigot-26.2.jar has been created.
  echo Now run START-SERVER.bat
) else (
  echo.
  echo [ERROR] Build finished but spigot-26.2.jar was not found.
)
pause
