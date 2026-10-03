@echo off
setlocal
title PSQ Server Console
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0psq-admin-web\Start-AdminWeb.ps1"
if errorlevel 1 pause
endlocal
