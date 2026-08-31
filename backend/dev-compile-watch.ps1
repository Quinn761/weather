$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$sourceRoot = Join-Path $projectRoot 'src'
$logPath = Join-Path $projectRoot 'target/dev-compile-watch.log'

New-Item -ItemType Directory -Force -Path (Split-Path -Parent $logPath) | Out-Null

function Invoke-Compile {
    $timestamp = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
    Add-Content -Path $logPath -Value "[$timestamp] Change detected, running mvnw compile..."
    Push-Location $projectRoot
    try {
        & .\mvnw.cmd compile *>> $logPath
    } catch {
        Add-Content -Path $logPath -Value $_.Exception.Message
    } finally {
        Pop-Location
    }
}

$watcher = New-Object System.IO.FileSystemWatcher
$watcher.Path = $sourceRoot
$watcher.IncludeSubdirectories = $true
$watcher.NotifyFilter = [System.IO.NotifyFilters]'FileName, LastWrite, Size'
$watcher.EnableRaisingEvents = $true

$lastRun = Get-Date '2000-01-01'
$action = {
    $now = Get-Date
    if (($now - $script:lastRun).TotalSeconds -lt 2) {
        return
    }
    $script:lastRun = $now
    Invoke-Compile
}

Register-ObjectEvent $watcher Changed -Action $action | Out-Null
Register-ObjectEvent $watcher Created -Action $action | Out-Null
Register-ObjectEvent $watcher Deleted -Action $action | Out-Null
Register-ObjectEvent $watcher Renamed -Action $action | Out-Null

Add-Content -Path $logPath -Value "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] Watching $sourceRoot"

while ($true) {
    Start-Sleep -Seconds 2
}
