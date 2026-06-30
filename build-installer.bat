@echo off
setlocal

:: ============================================================
:: build-installer.bat
:: Builds a self-contained portable Windows app for Chat Application.
:: Bundles the JRE inside — no Java required on the target PC.
:: Requires JDK 21 (Temurin) installed.
:: ============================================================

:: Auto-detect JDK 21 location
set "JDK21="
for /d %%D in ("C:\Program Files\Java\jdk-24*") do set "JDK21=%%D"
for /d %%D in ("C:\Program Files\Java\jdk-21*") do set "JDK21=%%D"
for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-21*") do set "JDK21=%%D"
for /d %%D in ("C:\Program Files\Microsoft\jdk-21*") do set "JDK21=%%D"

if "%JDK21%"=="" (
    echo [ERROR] JDK 21 not found. Please install Temurin JDK 21 from:
    echo         https://adoptium.net
    pause
    exit /b 1
)

if not exist "%JDK21%\bin\jpackage.exe" (
    echo [ERROR] jpackage.exe not found in: %JDK21%\bin
    echo         Make sure you installed JDK 21 ^(not JRE^).
    pause
    exit /b 1
)

echo [INFO] Using JDK 21: %JDK21%
set "JAVA_HOME=%JDK21%"
set "PATH=%JDK21%\bin;%PATH%"

:: Step 1: Build the fat JAR with Maven
echo.
echo [INFO] Building fat JAR with Maven...
call mvn clean package -q
if errorlevel 1 (
    echo [ERROR] Maven build failed!
    pause
    exit /b 1
)
echo [INFO] Fat JAR built successfully.

:: Step 2: Use jpackage --type app-image (no WiX needed)
::         This creates a self-contained folder with bundled JRE.
echo.
echo [INFO] Creating self-contained app bundle with jpackage...

set "OUT=dist"
if exist "%OUT%" rmdir /s /q "%OUT%"
mkdir "%OUT%"

jpackage ^
  --type app-image ^
  --name "ChatApplication" ^
  --app-version "1.0" ^
  --input target ^
  --main-jar ChatApplication-1.0-SNAPSHOT.jar ^
  --main-class com.chat.client.Launcher ^
  --dest "%OUT%" ^
  --java-options "--add-opens=javafx.graphics/com.sun.javafx.application=ALL-UNNAMED" ^
  --java-options "--add-opens=java.base/java.lang=ALL-UNNAMED"

if errorlevel 1 (
    echo [ERROR] jpackage failed!
    pause
    exit /b 1
)

:: Step 3: Zip the output for easy sharing
echo.
echo [INFO] Zipping the app bundle...
powershell -Command "Compress-Archive -Path '%OUT%\ChatApplication' -DestinationPath 'ChatApplication-portable.zip' -Force"
echo.
echo ============================================================
echo  SUCCESS!
echo ============================================================
echo  Portable app folder : %OUT%\ChatApplication\
echo  Shareable ZIP       : ChatApplication-portable.zip
echo.
echo  To run: unzip and double-click ChatApplication\ChatApplication.exe
echo  Works on ANY Windows PC - no Java required!
echo ============================================================
pause
