# Run the VoxelEngine dedicated server (hgdsNET protocol).
# Examples:
#   .\run-server.ps1                        # world "world", default port (45165)
#   .\run-server.ps1 -Port 25599 -Flat      # custom port, superflat world
#   .\run-server.ps1 -World my_world -Seed 123 -Max 16
param(
    [string]$World = "world",
    [int]$Port = 45165,
    [long]$Seed = 0,
    [switch]$Flat,
    [int]$Max = 8,
    [string]$Motd = "VoxelEngine server on hgdsNET"
)
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$mvnDir = 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.3\plugins\maven-plugin\lib\maven3\bin'
$env:Path = "$mvnDir;$env:Path"

$execArgs = "--world=$World --port=$Port --max=$Max --motd=$Motd"
if ($Seed -ne 0) { $execArgs += " --seed=$Seed" }
if ($Flat) { $execArgs += " --flat" }

Push-Location $root
try {
    & mvn -o -q -pl server -am compile exec:java "-Dexec.args=$execArgs"
} finally {
    Pop-Location
}
