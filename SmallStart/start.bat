@echo off
setlocal
cd /d "%~dp0"

set "URL=https://raw.githubusercontent.com/Lanfir7/nurgling2/next/release"
set "LAUNCHER=nurgling_launcher.jar"

where java >nul 2>&1
if errorlevel 1 (
  echo Java not found. Install JDK 21+ and add it to PATH.
  pause
  exit /b 1
)

if not exist "%LAUNCHER%" (
  echo Downloading launcher...
  curl -L --fail -o "%LAUNCHER%" "%URL%/%LAUNCHER%"
  if errorlevel 1 (
    echo Failed to download %LAUNCHER%
    pause
    exit /b 1
  )
)

echo Updating from %URL%/
java -jar "%LAUNCHER%" update %URL%/ --add-exports=java.desktop/sun.awt=ALL-UNNAMED -Dsun.java2d.uiScale.enabled=false -jar ./hafen.jar
pause
