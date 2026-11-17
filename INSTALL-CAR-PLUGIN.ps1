$ErrorActionPreference = "Stop"
$ServerRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$PluginsDir = Join-Path $ServerRoot "plugins"
New-Item -ItemType Directory -Force -Path $PluginsDir | Out-Null

$Existing = Get-ChildItem -Path $PluginsDir -Filter "*Mercedex*.jar" -File -ErrorAction SilentlyContinue | Select-Object -First 1
if ($Existing) {
    Write-Host "Car plugin already exists: $($Existing.Name)" -ForegroundColor Green
    exit 0
}

$Headers = @{ "User-Agent" = "NewYorkRP-Server-Setup/1.0 (https://github.com/bs9973866-png/server)" }
$ProjectId = "2CrfFJyk"
$Loaders = [uri]::EscapeDataString('["paper"]')
$GameVersions = [uri]::EscapeDataString('["26.2"]')
$Api = "https://api.modrinth.com/v2/project/$ProjectId/version?loaders=$Loaders&game_versions=$GameVersions"

Write-Host "Checking the Mercedex car plugin for Paper 26.2..." -ForegroundColor Cyan
$Versions = Invoke-RestMethod -Uri $Api -Headers $Headers -Method Get
$Version = $Versions | Where-Object { $_.version_type -eq "release" } | Select-Object -First 1
if (-not $Version) {
    throw "Modrinth did not return a stable Mercedex release for Paper 26.2. The server was not started."
}
$File = $Version.files | Where-Object { $_.primary } | Select-Object -First 1
if (-not $File) { $File = $Version.files | Select-Object -First 1 }
if (-not $File -or -not $File.url -or -not $File.filename.EndsWith(".jar")) {
    throw "Modrinth did not provide a valid car plugin JAR."
}

$Destination = Join-Path $PluginsDir $File.filename
Write-Host "Downloading Mercedex $($Version.version_number)..." -ForegroundColor Cyan
Invoke-WebRequest -Uri $File.url -Headers $Headers -OutFile $Destination
if (-not (Test-Path $Destination) -or (Get-Item $Destination).Length -lt 100KB) {
    throw "Car plugin download is missing or incomplete."
}
Write-Host "Car plugin is ready: $Destination" -ForegroundColor Green
