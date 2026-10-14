param(
    [string]$Goals = "compile",
    [string]$LogFile = "_build.log"
)
# Собираем проект офлайн через Maven, входящий в поставку IntelliJ IDEA.
# Результат пишется в лог, по завершении создаётся маркер _build.done с кодом возврата.
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$mvnDir = 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.3\plugins\maven-plugin\lib\maven3\bin'
$env:Path = "$mvnDir;$env:Path"
$log = Join-Path $root $LogFile
$done = Join-Path $root '_build.done'
Remove-Item -Force $done -ErrorAction SilentlyContinue
& mvn -B $Goals.Split(' ') 2>&1 | Out-File -FilePath $log -Encoding UTF8
$code = $LASTEXITCODE
Set-Content -Path $done -Value "exit=$code" -Encoding UTF8
