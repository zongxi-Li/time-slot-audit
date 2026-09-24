@echo off
setlocal

if /i not "%~1"=="--run-in-demo-window" (
    start "TimeSlot A302 concurrency demo" "%ComSpec%" /d /k ""%~f0" --run-in-demo-window"
    exit /b 0
)

cd /d "%~dp0"
title TimeSlot A302 concurrency demo

echo ================================================
echo TimeSlot reservation concurrency demonstration
echo Room: A302 ^(ID 2^)
echo Time: tomorrow, 10:00-11:00
echo Requests: 10 concurrent submissions
echo This demo creates one real reservation and does not remove it.
echo ================================================
echo.

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\run-concurrency-demo.ps1" -StartBackendIfMissing -RoomId 2 -Count 10
set "DEMO_EXIT_CODE=%ERRORLEVEL%"

echo.
if "%DEMO_EXIT_CODE%"=="0" (
    echo Demo completed successfully.
) else (
    echo Demo failed with exit code %DEMO_EXIT_CODE%. Read the messages above.
)
echo.
echo This window will remain open so you can review the result. Close it when finished.
exit /b %DEMO_EXIT_CODE%
