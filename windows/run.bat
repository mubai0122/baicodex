@echo off
REM Baicodex Windows - double click to run (outside any sandbox, so hardware fingerprint works)
setlocal
set JAVA_HOME=C:\AndroidSdk\jdk-21
set PATH=%JAVA_HOME%\bin;C:\AndroidSdk\gradle-9.0.0\bin;%PATH%
cd /d "%~dp0"
gradle run --offline
endlocal
