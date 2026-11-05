@REM ----------------------------------------------------------------------------
@REM Maven Wrapper Batch Script
@REM ----------------------------------------------------------------------------
@echo off
setlocal

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.

@REM If system mvn exists, use it
where mvn >nul 2>nul
if %ERRORLEVEL% equ 0 (
  mvn %*
  exit /b %ERRORLEVEL%
)

@REM If IntelliJ bundled Maven exists, use it
if exist "D:\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd" (
  "D:\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd" %*
  exit /b %ERRORLEVEL%
)

echo Error: Maven is not installed on PATH. Please install Maven or run from your IDE.
exit /b 1
