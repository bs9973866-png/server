$ErrorActionPreference = "Stop"

# Installs the city world bundled inside the repository ZIP.
# Original map: Midtown Manhattan by BasVerhagen. See MAP-CREDITS.txt.

$ServerRoot = $PSScriptRoot
$WorldPath = Join-Path $ServerRoot "world"
$ArchivePath = Join-Path $ServerRoot "maps\new-york-midtown-manhattan.zip"
$TempPath = Join-Path $ServerRoot "new-york-map-extract"
$MarkerPath = Join-Path $WorldPath ".newyorkrp-map-installed"

if (Test-Path (Join-Path $WorldPath "level.dat")) {
    Write-Host "[MAP] Existing world detected. Leaving it untouched." -ForegroundColor Yellow
    exit 0
}

if (-not (Test-Path $ArchivePath)) {
    throw "Bundled city map archive is missing: $ArchivePath. Download the latest repository ZIP again."
}

if ((Get-Item $ArchivePath).Length -lt 1000000) {
    throw "Bundled city map archive is unexpectedly small or damaged."
}

Write-Host "[MAP] Installing bundled Midtown Manhattan city world..." -ForegroundColor Cyan

try {
    if (Test-Path $TempPath) { Remove-Item $TempPath -Recurse -Force }
    New-Item -ItemType Directory -Path $TempPath | Out-Null
    Expand-Archive -LiteralPath $ArchivePath -DestinationPath $TempPath -Force

    $levelFiles = @(Get-ChildItem -LiteralPath $TempPath -Filter "level.dat" -File -Recurse)
    if ($levelFiles.Count -ne 1) {
        throw "Archive validation failed: expected exactly one level.dat, found $($levelFiles.Count)."
    }

    $sourceWorld = $levelFiles[0].Directory.FullName
    if (Test-Path $WorldPath) { Remove-Item $WorldPath -Recurse -Force }
    Move-Item -LiteralPath $sourceWorld -Destination $WorldPath

    Set-Content -LiteralPath $MarkerPath -Value "Midtown Manhattan by BasVerhagen. See MAP-CREDITS.txt."
    Write-Host "[MAP] Midtown Manhattan installed successfully." -ForegroundColor Green
    Write-Host "[MAP] Existing map spawn and buildings will be preserved." -ForegroundColor Cyan
}
catch {
    if (Test-Path $WorldPath) { Remove-Item $WorldPath -Recurse -Force }
    throw "Could not install the bundled city map. Server startup cancelled. Details: $($_.Exception.Message)"
}
finally {
    if (Test-Path $TempPath) { Remove-Item $TempPath -Recurse -Force }
}
