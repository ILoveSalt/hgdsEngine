@echo off
rem ============================================
rem  Запуск игры VoxelEngine (клиент с меню)
rem  Файл: run-game.bat
rem ============================================
chcp 65001 >nul
setlocal

rem Папка, где лежит этот .bat и jar-файлы
set "ROOT=%~dp0"
set "JAR=%ROOT%client\target\VoxelEngine-Game.jar"

if not exist "%JAR%" (
    echo [ОШИБКА] Не найден файл: %JAR%
    echo Сначала соберите проект: powershell -ExecutionPolicy Bypass -File build.ps1 -Goals "package -DskipTests"
    pause
    exit /b 1
)

rem -Dfile.encoding=UTF-8 — корректная кириллица в чате и консоли
java -Dfile.encoding=UTF-8 -Dsun.java2d.dpiaware=true -jar "%JAR%" %*
if errorlevel 1 pause
endlocal
