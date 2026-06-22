@echo off
REM Build all ASPIRESERVER plugins
REM Output JARs will be in each module's target\ folder and also copied to build\

echo === Building ASPIRESERVER ===
call mvnw.cmd clean package -q
if %ERRORLEVEL% neq 0 (
    echo Build failed!
    exit /b 1
)

if not exist build mkdir build

echo.
echo === Copying JARs to build\ ===
copy aspire-core\target\aspire-core-*.jar build\ >nul
copy aspire-buildbattle\target\aspire-buildbattle-*.jar build\ >nul
copy aspire-smp\target\aspire-smp-*.jar build\ >nul
copy aspire-creative\target\aspire-creative-*.jar build\ >nul

echo.
echo === Build Complete! ===
echo JARs ready in build\:
dir /b build\*.jar
echo.
echo Copy all JARs from build\ into your server's plugins\ folder.
