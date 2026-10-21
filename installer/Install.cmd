@echo off
powershell.exe -NoProfile -File "%~dp0Install-FrameRevive.ps1" %*
if errorlevel 1 pause
