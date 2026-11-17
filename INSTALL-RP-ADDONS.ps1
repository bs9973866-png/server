$ErrorActionPreference = "Stop"

# Installs only the two RP addons selected for Paper 26.2.
# Run from the extracted server repository folder, with the server stopped.

$ServerRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$PluginsDir = Join-Path $ServerRoot "plugins"
New-Item -ItemType Directory -Force -Path $PluginsDir | Out-Null

$Headers = @{ "User-Agent" = "NEW-YORK-RP-setup/1.0 (https://github.com/bs9973866-png/server)" }
$GameVersions = [uri]::EscapeDataString('["26.2"]')
$Loaders = [uri]::EscapeDataString('["paper"]')

function Install-ModrinthPlugin([string]$Project, [string]$DisplayName) {
    Write-Host "Checking $DisplayName for Paper 26.2..." -ForegroundColor Cyan
    $Api = "https://api.modrinth.com/v2/project/$Project/version?loaders=$Loaders&game_versions=$GameVersions"
    $Versions = Invoke-RestMethod -Uri $Api -Headers $Headers -Method Get
    $Version = $Versions | Where-Object { $_.version_type -eq "release" } | Select-Object -First 1
    if (-not $Version) {
        throw "No stable Paper 26.2 release was returned for $DisplayName. Nothing was installed for this addon."
    }

    $File = $Version.files | Where-Object { $_.primary } | Select-Object -First 1
    if (-not $File) { $File = $Version.files | Select-Object -First 1 }
    if (-not $File -or -not $File.url) { throw "Modrinth did not provide a downloadable JAR for $DisplayName." }

    $Destination = Join-Path $PluginsDir $File.filename
    if (Test-Path $Destination) {
        $Backup = "$Destination.backup-$(Get-Date -Format yyyyMMdd-HHmmss)"
        Move-Item $Destination $Backup
        Write-Host "Existing file backed up: $Backup" -ForegroundColor Yellow
    }

    Invoke-WebRequest -Uri $File.url -Headers $Headers -OutFile $Destination
    Write-Host "Installed $DisplayName $($Version.version_number): $Destination" -ForegroundColor Green
}

Install-ModrinthPlugin "npc-plugin" "NPC Plugin"
Install-ModrinthPlugin "2CrfFJyk" "Mercedex Car Plugin"

Write-Host ""
Write-Host "Addons downloaded. Put the freshly built NewYorkRP.jar in plugins too, then start the server." -ForegroundColor Green
Write-Host "See INSTALL-CITY-WORLD.md for the large New York map and NPC setup." -ForegroundColor Cyan
