<#
Start multiple backend services at once (PowerShell)

Usage:
  # Start services (default)
  pwsh -ExecutionPolicy Bypass -File .\scripts\start-services.ps1

  # Start services (if running PowerShell 5 use powershell.exe)
  powershell.exe -ExecutionPolicy Bypass -File .\scripts\start-services.ps1

  # Stop the services started by this script (use -Stop)
  pwsh -ExecutionPolicy Bypass -File .\scripts\start-services.ps1 -Stop

Notes:
- The script looks for jars in each module's `target` folder whose name contains the module folder name
  (for example: ecommerce-cart-service-1.0-SNAPSHOT.jar).
- AI service (`ecommerce-ai-service`) is excluded by default.
- Logs are written to ./logs/<service>.log
#>
param(
    [switch]$Stop
)

# Resolve project root (script location is expected to be projectRoot\scripts)
$scriptDir = $PSScriptRoot
$projectRoot = (Resolve-Path "$scriptDir\..\").ProviderPath

# Ensure java is available on PATH before attempting to start services
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Error "'java' not found in PATH. 请先安装 JDK 并确保 'java' 可在命令行中运行。验证命令： java -version"
    exit 1
}

# Services to start (order matters: dependencies first; gateway last). Excluding AI service.
$services = @(
    'ecommerce-user-service',
    'ecommerce-merchant-service',
    'ecommerce-dashboard',
    'ecommerce-cart-service',
    'ecommerce-inventory-service',
    'ecommerce-order-service',
    'ecommerce-payment-service',
    'ecommerce-chat-service',
    'ecommerce-gateway'
)

# Directory for logs
$logDir = Join-Path $projectRoot 'logs'
if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir | Out-Null }

function Find-JarForService($projectRoot, $serviceName) {
    $targetDir = Join-Path $projectRoot $serviceName
    $targetDir = Join-Path $targetDir 'target'
    if (-not (Test-Path $targetDir)) { return $null }

    # Prefer jars that start with the service name, exclude source/javadoc/original jars
    $candidates = Get-ChildItem -Path $targetDir -Filter "*.jar" -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch 'sources|javadoc|original' }

    if (-not $candidates) { return $null }

    # Try to find a jar that contains the service name
    $match = $candidates | Where-Object { $_.Name -like "$serviceName*.jar" } | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($match) { return $match.FullName }

    # Fallback: return the most recently modified jar
    return ($candidates | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
}

function Start-ServiceJar($serviceName) {
    $jar = Find-JarForService $projectRoot $serviceName
    if (-not $jar) {
        Write-Warning "Jar not found for service: $serviceName. Expected under: $projectRoot\$serviceName\target\"
        return
    }

    $logFile = Join-Path $logDir ("$serviceName.log")

    Write-Host "Starting $serviceName -> $jar"

    # Start the java process via cmd.exe so we can redirect both stdout and stderr to the same file
    # Use cmd /c "java -jar "path" > "logFile" 2>&1"
    $cmd = 'cmd.exe'
    # Ensure console uses UTF-8 so redirected logs are encoded as UTF-8 (helps avoid garbled Chinese characters)
    # Use chcp 65001 before starting java. Use && to chain commands so java runs after chcp.
    $cmdArg = "/c chcp 65001>nul && java -jar `"$jar`" > `"$logFile`" 2>&1"
    try {
        $proc = Start-Process -FilePath $cmd -ArgumentList $cmdArg -WorkingDirectory (Split-Path $jar) -WindowStyle Hidden -PassThru
        Start-Sleep -Milliseconds 300
        Write-Host "  PID: $($proc.Id)  Log: $logFile"
    }
    catch {
        Write-Error ("Failed to start {0}: {1}" -f $serviceName, $($_))
    }
}

function Stop-StartedServices() {
    # Use Win32_Process to inspect command line and find java processes running jar files under this project
    $procs = Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -and $_.CommandLine -match '\.jar' -and $_.CommandLine -match [regex]::Escape($projectRoot) }
    if (-not $procs) { Write-Host "No running service processes found under project root: $projectRoot"; return }

    $procs | Select-Object ProcessId, CommandLine | ForEach-Object {
        $processId = $_.ProcessId
        $cmd = $_.CommandLine
        Write-Host "Stopping PID $processId -> $cmd"
        try { Stop-Process -Id $processId -Force -ErrorAction Stop; Start-Sleep -Milliseconds 200 }
        catch { Write-Warning ("Failed to stop PID {0}: {1}" -f $processId, $($_)) }
    }
}

if ($Stop) {
    Write-Host "Stopping started backend service jars (excluding AI service)..."
    Stop-StartedServices
    Write-Host "Done."
    return
}

# Start loop
Write-Host "Starting backend services (AI excluded). Project root: $projectRoot" -ForegroundColor Cyan
foreach ($svc in $services) {
    Start-ServiceJar $svc
}

Write-Host "All start commands issued. Check logs in: $logDir" -ForegroundColor Green
Write-Host "To stop services started under this project run:`n  powershell -ExecutionPolicy Bypass -File .\scripts\start-services.ps1 -Stop" -ForegroundColor Yellow

