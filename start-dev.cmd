@echo off
setlocal

cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start-dev.ps1"

if errorlevel 1 (
    echo.
    echo Startup failed. Read the error above.
    pause
)
