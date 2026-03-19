@echo off
setlocal enabledelayedexpansion

echo ============================================
echo   Building MissileMan DEMO (Steam-ready)
echo ============================================
echo.

:: Set paths
set SRC_DIR=src
set LIB_DIR=lib
set BUILD_DIR=build\demo
set STAGE_DIR=demo
set CLASSPATH=%LIB_DIR%\flatlaf-3.2.5.jar;%LIB_DIR%\jinput.jar

:: Detect Java home - find a JDK that has jar.exe
set "JAVA_HOME="

:: First try: scan PATH for a javac with jar.exe alongside it
for /f "delims=" %%i in ('where javac 2^>nul') do (
    if not defined JAVA_HOME if exist "%%~dpijar.exe" (
        for %%k in ("%%~dpi..") do set "JAVA_HOME=%%~fk"
    )
)

:: Second try: check common JDK install locations
if not defined JAVA_HOME (
    for /d %%d in ("C:\Program Files\Java\jdk-*") do (
        if not defined JAVA_HOME if exist "%%d\bin\jar.exe" set "JAVA_HOME=%%d"
    )
)
if not defined JAVA_HOME if exist "%USERPROFILE%\AppData\Roaming\Code\User\globalStorage\pleiades.java-extension-pack-jdk\java\latest\bin\jar.exe" (
    set "JAVA_HOME=%USERPROFILE%\AppData\Roaming\Code\User\globalStorage\pleiades.java-extension-pack-jdk\java\latest"
)

if not defined JAVA_HOME (
    echo ERROR: Could not find a JDK with jar.exe!
    echo Install a full JDK ^(not just JRE^) and ensure it is on your PATH.
    pause
    exit /b 1
)
set "JAVA_BIN=%JAVA_HOME%\bin\"
echo Using JAVA_HOME: %JAVA_HOME%
set "PATH=%JAVA_HOME%\bin;%PATH%"

:: Clean previous build
if exist "%BUILD_DIR%" rmdir /s /q "%BUILD_DIR%"
mkdir "%BUILD_DIR%\classes"

:: Step 1: Copy source and enable DEMO_MODE
echo [1/6] Enabling DEMO_MODE...
mkdir "%BUILD_DIR%\src"
xcopy /s /q "%SRC_DIR%\*" "%BUILD_DIR%\src\" >nul

:: Replace DEMO_MODE = false with DEMO_MODE = true
powershell -Command "(Get-Content '%BUILD_DIR%\src\Game.java') -replace 'public static final boolean DEMO_MODE = false;', 'public static final boolean DEMO_MODE = true;' | Set-Content '%BUILD_DIR%\src\Game.java'"

:: Step 2: Compile
echo [2/6] Compiling...
del "%BUILD_DIR%\sources.txt" 2>nul
for /r "%BUILD_DIR%\src" %%f in (*.java) do (
    set "JFILE=%%f"
    echo "!JFILE:\=/!">> "%BUILD_DIR%\sources.txt"
)
javac -d "%BUILD_DIR%\classes" -sourcepath "%BUILD_DIR%\src" -cp "%CLASSPATH%" @"%BUILD_DIR%\sources.txt"
if errorlevel 1 (
    echo ERROR: Compilation failed!
    pause
    exit /b 1
)

:: Step 3: Create JAR
echo [3/6] Creating JAR...
jar cfm "%BUILD_DIR%\MissileMan_Demo.jar" Manifest.txt -C "%BUILD_DIR%\classes" .
if errorlevel 1 (
    echo ERROR: JAR creation failed!
    pause
    exit /b 1
)

:: Step 4: Package with jpackage (creates .exe with bundled JRE)
echo [4/6] Packaging with jpackage...
if exist "%STAGE_DIR%" rmdir /s /q "%STAGE_DIR%"

:: Build input lib dir with dependencies
mkdir "%BUILD_DIR%\input-lib"
copy "%LIB_DIR%\jinput.jar" "%BUILD_DIR%\input-lib\" >nul

jpackage --type app-image ^
    --name "MissileMan Demo" ^
    --input "%BUILD_DIR%" ^
    --main-jar "MissileMan_Demo.jar" ^
    --main-class App ^
    --dest "%STAGE_DIR%\temp" ^
    --java-options "-Djpackage.app-version=0.9.0-demo" ^
    --app-version "0.9.0"
if errorlevel 1 (
    echo ERROR: jpackage failed! Falling back to JAR-only build...
    goto :jaronly
)

:: Move from temp subfolder to demo root
for /d %%d in ("%STAGE_DIR%\temp\*") do (
    xcopy /s /q /e "%%d\*" "%STAGE_DIR%\" >nul
)
rmdir /s /q "%STAGE_DIR%\temp"
goto :copyassets

:jaronly
:: Fallback: manual assembly without jpackage
mkdir "%STAGE_DIR%"
copy "%BUILD_DIR%\MissileMan_Demo.jar" "%STAGE_DIR%\MissileMan_Demo.jar" >nul
mkdir "%STAGE_DIR%\lib"
xcopy /s /q "%LIB_DIR%\*" "%STAGE_DIR%\lib\" >nul
:: Create a simple launcher bat as fallback
echo @echo off > "%STAGE_DIR%\Launch MissileMan Demo.bat"
echo java -jar MissileMan_Demo.jar >> "%STAGE_DIR%\Launch MissileMan Demo.bat"

:copyassets
:: Step 5: Copy runtime assets
echo [5/6] Copying assets...
if exist "sprites" xcopy /s /q "sprites\*" "%STAGE_DIR%\sprites\" >nul
if exist "SFX" xcopy /s /q "SFX\*" "%STAGE_DIR%\SFX\" >nul
if exist "Fonts" xcopy /s /q "Fonts\*" "%STAGE_DIR%\Fonts\" >nul
if exist "config" xcopy /s /q "config\*" "%STAGE_DIR%\config\" >nul

:: Copy native DLLs needed by jinput alongside the jar/app
copy "%LIB_DIR%\jinput-dx8_64.dll" "%STAGE_DIR%\" >nul 2>nul
copy "%LIB_DIR%\jinput-raw_64.dll" "%STAGE_DIR%\" >nul 2>nul
copy "%LIB_DIR%\jinput-wintab.dll" "%STAGE_DIR%\" >nul 2>nul
copy "%LIB_DIR%\jinput.jar" "%STAGE_DIR%\app\lib\" >nul 2>nul

mkdir "%STAGE_DIR%\saves" 2>nul

:: Step 6: Clean up temp build files
echo [6/6] Cleaning up...
rmdir /s /q "%BUILD_DIR%"

echo.
echo ============================================
echo   Demo build complete!
echo   Output: %STAGE_DIR%\
echo   Ready for Steam upload.
echo ============================================
pause
