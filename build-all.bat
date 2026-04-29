@echo off
:: ════════════════════════════════════════════════════════════════
::  SpanTag — Build All Services using root pom.xml
::  Requires pom.xml to be at D:\keyclockbf\pom.xml
:: ════════════════════════════════════════════════════════════════

setlocal

set BASE=D:\keyclockbf
set MVN=mvn

echo.
echo [93m Building all SpanTag services... [0m
echo [90m Running: mvn clean package -DskipTests [0m
echo [90m From: %BASE% [0m
echo.

cd /d "%BASE%"
call %MVN% clean package -DskipTests

if %errorlevel%==0 (
    echo.
    echo [92m All services built successfully! [0m
    echo [90m Now run start.bat to start all services. [0m
) else (
    echo.
    echo [91m Build failed. Check output above. [0m
    echo [90m Tip: run individual builds to isolate the issue:
    echo   cd %BASE%\payment-service
    echo   mvn clean package -DskipTests [0m
)
echo.

endlocal
