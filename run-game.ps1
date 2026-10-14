# Run the VoxelEngine game client (menu: singleplayer & multiplayer).
# -AutoExitSeconds N - close the game after N seconds (for automated checks).
param(
    [int]$AutoExitSeconds = 0
)
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$mvnDir = 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.3\plugins\maven-plugin\lib\maven3\bin'
$env:Path = "$mvnDir;$env:Path"

if ($AutoExitSeconds -gt 0) { $env:VOXEL_AUTO_EXIT_SECONDS = "$AutoExitSeconds" }

Push-Location $root
try {
    & mvn -o -q -pl client -am compile exec:java
} finally {
    Pop-Location
}
