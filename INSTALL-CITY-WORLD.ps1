$ErrorActionPreference = "Stop"

# Downloads and installs the complete 1:1 Lower Manhattan world on first server start.
# Source: Necron, Planet Minecraft project 731925. Keep the creator attribution.

$ServerRoot = $PSScriptRoot
$WorldPath = Join-Path $ServerRoot "world"
$MarkerPath = Join-Path $WorldPath ".newyorkrp-map-installed"
$ArchivePath = Join-Path $ServerRoot "new-york-lower-manhattan.zip"
$DownloadUrl = "https://www.planetminecraft.com/project/new-york-lower-manhattan-1-1-scale-nyc/download/mirror/731925/"
$Headers = @{ "User-Agent" = "NEW-YORK-RP-Server-Setup/1.0 (https://github.com/bs9973866-png/server)" }

if (Test-Path (Join-Path $WorldPath "level.dat")) {
    if (Test-Path $MarkerPath) {
        Write-Host "[MAP] New York city map is already installed." -ForegroundColor Green
    } else {
        Write-Host "[MAP] Existing world detected. Leaving it untouched." -ForegroundColor Yellow
        Write-Host "[MAP] To install the included city on a fresh server, keep the world folder absent before first start."
    }
    exit 0
}

Write-Host "[MAP] Downloading Necron's complete 1:1 Lower Manhattan map..." -ForegroundColor Cyan
try {
    Invoke-WebRequest -Uri $DownloadUrl -Headers $Headers -OutFile $ArchivePath -MaximumRedirection 10 -TimeoutSec 1800
} catch {
    throw "Could not download the New York map. Server startup cancelled so a random vanilla world is not created. Details: $($_.Exception.Message)"
}

try {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($ArchivePath)
    try {
        $levelEntries = @($zip.Entries | Where-Object { $_.FullName -match '(^|/)level\.dat$' })
        if ($levelEntries.Count -ne 1) {
            throw "The downloaded file is not the expected Minecraft world archive (level.dat not found uniquely)."
        }
        $rootPrefix = $levelEntries[0].FullName -replace 'level\.dat$', ''
        $tempPath = Join-Path $ServerRoot "new-york-map-extract"
        if (Test-Path $tempPath) { Remove-Item $tempPath -Recurse -Force }
        [System.IO.Compression.ZipFile]::ExtractToDirectory($ArchivePath, $tempPath)
        $sourceWorld = Join-Path $tempPath ($rootPrefix -replace '/', [IO.Path]::DirectorySeparatorChar)
        if (-not (Test-Path (Join-Path $sourceWorld "level.dat"))) {
            throw "The extracted archive does not contain a valid world folder."
        }
        if (Test-Path $WorldPath) { Remove-Item $WorldPath -Recurse -Force }
        Move-Item -LiteralPath $sourceWorld -Destination $WorldPath
        Set-Content -LiteralPath $MarkerPath -Value "Installed from Planet Minecraft project 731925 by Necron."
        Write-Host "[MAP] New York city world installed successfully." -ForegroundColor Green
    } finally {
        $zip.Dispose()
    }
} catch {
    if (Test-Path $WorldPath) { Remove-Item $WorldPath -Recurse -Force }
    throw
} finally {
    if (Test-Path $ArchivePath) { Remove-Item $ArchivePath -Force }
    $tempPath = Join-Path $ServerRoot "new-york-map-extract"
    if (Test-Path $tempPath) { Remove-Item $tempPath -Recurse -Force }
}

Write-Host "[MAP] World spawn will use the map's saved spawn location." -ForegroundColor Cyan
