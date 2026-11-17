@echo off
chcp 65001 >nul
setlocal
title Minecraft Spigot 26.2
cd /d "%~dp0"

if not exist "spigot-26.2.jar" (
  echo [ERROR] spigot-26.2.jar not found.
  echo Run BUILD-SPIGOT.bat first.
  pause
  exit /b 1
)

if not exist "eula.txt" (
  echo eula=false>"eula.txt"
)

findstr /i /c:"eula=true" "eula.txt" >nul
if errorlevel 1 (
  echo Minecraft EULA has not been accepted.
  echo Read https://aka.ms/MinecraftEULA
  echo If you agree, edit eula.txt and set eula=true.
  pause
  exit /b 0
)

echo Starting Spigot 26.2...
java -Xms1G -Xmx4G -jar "spigot-26.2.jar" nogui
echo.
echo Server stopped.
pause
