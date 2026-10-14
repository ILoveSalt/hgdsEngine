@echo off
rem ============================================
rem  Запуск выделенного сервера VoxelEngine
rem  Файл: run-server.bat
rem
rem  Примеры:
rem    run-server.bat
rem    run-server.bat --world=world --port=45165
rem    run-server.bat --world=мой_мир --seed=123 --flat --max=16
rem ============================================
chcp 65001 >nul
setlocal
title VoxelEngine Server

rem Папка, где лежит этот .bat и jar-файлы
set "ROOT=%~dp0"
set "JAR=%ROOT%server\target\VoxelEngine-Server.jar"

if not exist "%JAR%" (
    echo [ОШИБКА] Не найден файл: %JAR%
    echo Сначала соберите проект: powershell -ExecutionPolicy Bypass -File build.ps1 -Goals "package -DskipTests"
    pause
    exit /b 1
)

rem Все аргументы, переданные .bat, передаются на сервер (world/port/seed/flat/max/motd...)
java -Dfile.encoding=UTF-8 -jar "%JAR%" %*

echo.
echo Сервер остановлен.
pause
endlocal
