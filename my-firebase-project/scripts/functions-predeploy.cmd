@echo off
setlocal
set "FUNCTIONS_DIR=%~dp0..\my-firebase-functions"
call npm.cmd --prefix "%FUNCTIONS_DIR%" run lint
if errorlevel 1 exit /b %errorlevel%
call npm.cmd --prefix "%FUNCTIONS_DIR%" run build
exit /b %errorlevel%