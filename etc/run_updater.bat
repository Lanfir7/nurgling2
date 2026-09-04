@echo off
setlocal EnableExtensions EnableDelayedExpansion

where java >nul 2>&1
if errorlevel 1 (
  echo Java not found. Install JDK 21+ and add it to PATH.
  pause
  exit /b 1
)

set "JAVA_SPEC="
for /f "tokens=2 delims==" %%V in ('java -XshowSettings:properties -version 2^>^&1 ^| findstr "java.specification.version"') do set "JAVA_SPEC=%%V"
for /f "tokens=*" %%V in ("!JAVA_SPEC!") do set "JAVA_SPEC=%%V"
set "JAVA_MAJOR=!JAVA_SPEC!"
if "!JAVA_SPEC:~0,2!"=="1." set "JAVA_MAJOR=!JAVA_SPEC:~2!"
for /f "tokens=1 delims=." %%V in ("!JAVA_MAJOR!") do set "JAVA_MAJOR=%%V"
echo(!JAVA_MAJOR!| findstr /r "^[0-9][0-9]*$" >nul || (
  echo Could not detect Java version. Install JDK 21+.
  pause
  exit /b 1
)
if !JAVA_MAJOR! LSS 21 (
  echo Java 21 or newer is required. Found Java !JAVA_SPEC!.
  pause
  exit /b 1
)

java -jar nurgling_launcher.jar update https://raw.githubusercontent.com/Lanfir7/nurgling-release/stable/ ^
  -Dsun.java2d.uiScale.enabled=false ^
  -Xms512m -Xmx4g -Xss2m ^
  -XX:+UseZGC -XX:+IgnoreUnrecognizedVMOptions -XX:+ZGenerational ^
  -XX:SoftRefLRUPolicyMSPerMB=50 ^
  -XX:+UseStringDeduplication ^
  --add-exports=java.desktop/sun.awt=ALL-UNNAMED ^
  -jar ./hafen.jar
pause
