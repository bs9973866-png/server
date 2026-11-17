@echo off
chcp 65001 >nul
cd /d "%~dp0"
title Build NEW YORK RP
where mvn >nul 2>nul
if errorlevel 1 (
  echo Maven не найден. Установи Apache Maven и добавь его в PATH.
  pause
  exit /b 1
)
echo Building NEW YORK RP for Paper 26.2...
call mvn -U clean package
if errorlevel 1 (
  echo.
  echo BUILD FAILED. Скопируй сюда весь текст ошибки.
  pause
  exit /b 1
)
if not exist "..\plugins" mkdir "..\plugins"
copy /Y "target\NewYorkRP.jar" "..\plugins\NewYorkRP.jar"
echo.
echo Готово! Плагин скопирован в папку plugins сервера.
pause