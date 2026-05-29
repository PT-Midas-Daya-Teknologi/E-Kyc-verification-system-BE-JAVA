@echo off
REM Compile Java sources directly with javac
setlocal enabledelayedexpansion

echo Compiling LivenessService...
cd /d "%~dp0"

REM Set JAVA_HOME if not already set
if not defined JAVA_HOME (
    for /f "tokens=*" %%A in ('powershell -Command "[System.Environment]::GetEnvironmentVariable('JAVA_HOME')"') do set "JAVA_HOME=%%A"
)

if not defined JAVA_HOME (
    echo ERROR: JAVA_HOME is not set. Please set it to your Java installation directory.
    exit /b 1
)

set "JAVA=%JAVA_HOME%\bin\java"
set "JAVAC=%JAVA_HOME%\bin\javac"
set "JAR=%JAVA_HOME%\bin\jar"

REM Check if Java compiler exists
if not exist "%JAVAC%" (
    echo ERROR: javac not found at %JAVAC%
    exit /b 1
)

echo Found Java compiler at: %JAVAC%
echo Running: %JAVAC% -version
%JAVAC% -version

echo.
echo NOTE: Compiling Java with javac directly requires all dependencies in classpath.
echo It's strongly recommended to use Maven instead.
echo.
echo To install Maven:
echo   1. Download from https://maven.apache.org/download.cgi
echo   2. Extract to a folder
echo   3. Add bin folder to PATH
echo   4. Run: mvn clean spring-boot:run -DskipTests
echo.

pause
