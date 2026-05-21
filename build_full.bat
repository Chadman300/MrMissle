@echo off
setlocal enabledelayedexpansion

echo ============================================
echo   Building MissileMan (Full Game)
echo ============================================
echo.

:: Set paths
set SRC_DIR=src
set LIB_DIR=lib
set BUILD_DIR=build\full
set OUTPUT_DIR=build\output\MissileMan
set CLASSPATH=%LIB_DIR%\flatlaf-3.2.5.jar;%LIB_DIR%\jinput.jar

:: Optional Steam integration: only compiled when steamworks4j jar is present.
:: Drop e.g. steamworks4j-1.9.0.jar into lib/ to enable.
set STEAM_SOURCES=
for %%F in ("%LIB_DIR%\steamworks4j*.jar") do (
    set CLASSPATH=!CLASSPATH!;%%F
    set STEAM_SOURCES=%SRC_DIR%\steam\*.java
    echo Found steamworks4j: %%F
)

:: Clean previous build
if exist "%BUILD_DIR%" rmdir /s /q "%BUILD_DIR%"
mkdir "%BUILD_DIR%\classes"

:: Step 1: Compile
echo [1/4] Compiling...
javac -d "%BUILD_DIR%\classes" -cp "%CLASSPATH%" "%SRC_DIR%\*.java" %STEAM_SOURCES%
if errorlevel 1 (
    echo ERROR: Compilation failed!
    pause
    exit /b 1
)

:: Step 2: Create JAR
echo [2/4] Creating JAR...
jar cfm "%BUILD_DIR%\MissileMan.jar" Manifest.txt -C "%BUILD_DIR%\classes" .
if errorlevel 1 (
    echo ERROR: JAR creation failed!
    pause
    exit /b 1
)

:: Step 3: Assemble output
echo [3/4] Assembling output...
if exist "%OUTPUT_DIR%" rmdir /s /q "%OUTPUT_DIR%"
mkdir "%OUTPUT_DIR%"

:: Copy JAR
copy "%BUILD_DIR%\MissileMan.jar" "%OUTPUT_DIR%\MissileMan.jar" >nul

:: Copy libraries
mkdir "%OUTPUT_DIR%\lib"
xcopy /s /q "%LIB_DIR%\*" "%OUTPUT_DIR%\lib\" >nul

:: Copy runtime assets
if exist "sprites" xcopy /s /q "sprites\*" "%OUTPUT_DIR%\sprites\" >nul
if exist "SFX" xcopy /s /q "SFX\*" "%OUTPUT_DIR%\SFX\" >nul
if exist "Fonts" xcopy /s /q "Fonts\*" "%OUTPUT_DIR%\Fonts\" >nul
if exist "config" xcopy /s /q "config\*" "%OUTPUT_DIR%\config\" >nul
if exist "saves" xcopy /s /q "saves\*" "%OUTPUT_DIR%\saves\" >nul

:: Step 4: Clean up temp build files
echo [4/4] Cleaning up...
rmdir /s /q "%BUILD_DIR%\classes"

echo.
echo ============================================
echo   Full game build complete!
echo   Output: %OUTPUT_DIR%
echo ============================================
pause
