$ErrorActionPreference = "Stop"
$ServerRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$PluginsDir = Join-Path $ServerRoot "plugins"
New-Item -ItemType Directory -Force -Path $PluginsDir | Out-Null

$Headers = @{ "User-Agent" = "NewYorkRP-Server-Setup/1.0 (https://github.com/bs9973866-png/server)" }
$ProjectId = "lMVQTkWl"
$Loaders = [uri]::EscapeDataString('["paper"]')
$GameVersions = [uri]::EscapeDataString('["26.2"]')
$Api = "https://api.modrinth.com/v2/project/$ProjectId/version?loaders=$Loaders&game_versions=$GameVersions"

Write-Host "Checking the official NPC Plugin release for Paper 26.2..." -ForegroundColor Cyan
$Versions = Invoke-RestMethod -Uri $Api -Headers $Headers -Method Get
$Version = $Versions | Where-Object { $_.version_type -eq "release" } | Select-Object -First 1
if (-not $Version) {
    throw "Modrinth did not return a stable NPC Plugin release for Paper 26.2. The server was not started."
}
$File = $Version.files | Where-Object { $_.primary } | Select-Object -First 1
if (-not $File) { $File = $Version.files | Select-Object -First 1 }
if (-not $File -or -not $File.url -or -not $File.filename.EndsWith(".jar")) {
    throw "Modrinth did not provide a valid NPC Plugin JAR."
}

$Destination = Join-Path $PluginsDir $File.filename
if (-not (Test-Path $Destination)) {
    Write-Host "Downloading NPC Plugin $($Version.version_number)..." -ForegroundColor Cyan
    Invoke-WebRequest -Uri $File.url -Headers $Headers -OutFile $Destination
}
if (-not (Test-Path $Destination) -or (Get-Item $Destination).Length -lt 100KB) {
    throw "NPC Plugin download is missing or incomplete."
}
Write-Host "NPC Plugin $($Version.version_number) is ready: $Destination" -ForegroundColor Green
