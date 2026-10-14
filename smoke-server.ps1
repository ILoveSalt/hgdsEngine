# Smoke test of the dedicated server: print help, then start and stop via stdin.
param([string]$LogFile = "_smoke.log")
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$mvnDir = 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.3\plugins\maven-plugin\lib\maven3\bin'
$env:Path = "$mvnDir;$env:Path"
$log = Join-Path $root $LogFile
$done = Join-Path $root '_smoke.done'
Remove-Item -Force $done -ErrorAction SilentlyContinue
Push-Location $root
try {
    "--- help ---" | Out-File -FilePath $log -Encoding UTF8
    & mvn -o -q -pl server -am compile exec:java "-Dexec.args=help" 2>&1 |
        Out-File -FilePath $log -Encoding UTF8 -Append
    "--- start + stop ---" | Out-File -FilePath $log -Encoding UTF8 -Append
    "stop" | & mvn -o -q -pl server -am compile exec:java "-Dexec.args=--world=smoke --port=0 --flat" 2>&1 |
        Out-File -FilePath $log -Encoding UTF8 -Append
    Set-Content -Path $done -Value "exit=0" -Encoding UTF8
} catch {
    Set-Content -Path $done -Value "exit=1 $_" -Encoding UTF8
} finally {
    Pop-Location
}
