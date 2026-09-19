@echo off
setlocal

rem ===== Project directory: the folder where this script is located =====
set "PROJECT_DIR=%~dp0"
cd /d "%PROJECT_DIR%"

rem ===== Maven configuration =====
set "JAVA_HOME=E:\Java\jdk17.0.7"
set "MAVEN_HOME=E:\apache-maven-3.9.16"
set "MAVEN_CMD=%MAVEN_HOME%\bin\mvn.cmd"
set "MAVEN_SETTINGS=%MAVEN_HOME%\conf\settings.xml"
set "MAVEN_REPO=E:\develop\repository"
set "PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%"

echo.
echo ========================================
echo Spring Boot Maven package
echo Project: %PROJECT_DIR%
echo ========================================
echo.

if not exist "%PROJECT_DIR%pom.xml" (
    echo [ERROR] pom.xml was not found in this directory.
    echo Please put this script in the project root directory.
    echo.
    pause
    exit /b 1
)

if not exist "%MAVEN_CMD%" (
    echo [ERROR] Maven was not found:
    echo %MAVEN_CMD%
    echo Please update MAVEN_HOME in this script.
    echo.
    pause
    exit /b 1
)

if not exist "%JAVA_HOME%\bin\javac.exe" (
    echo [ERROR] JDK was not found:
    echo %JAVA_HOME%
    echo Please update JAVA_HOME in this script.
    echo.
    pause
    exit /b 1
)

if not exist "%MAVEN_SETTINGS%" (
    echo [ERROR] Maven settings.xml was not found:
    echo %MAVEN_SETTINGS%
    echo Please update MAVEN_SETTINGS in this script.
    echo.
    pause
    exit /b 1
)

echo Building... Tests will be skipped.
echo Java: %JAVA_HOME%
echo.

call "%MAVEN_CMD%" -DskipTests=true -s "%MAVEN_SETTINGS%" -Dmaven.repo.local="%MAVEN_REPO%" clean package

if errorlevel 1 (
    echo.
    echo [FAILED] Maven package failed.
    pause
    exit /b 1
)

echo.
echo [SUCCESS] Package completed successfully.
echo Jar files are in:
echo %PROJECT_DIR%target
echo.
pause
exit /b 0
