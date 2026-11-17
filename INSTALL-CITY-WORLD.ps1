$ErrorActionPreference = "Stop"

# Downloads and installs the complete 1:1 Lower Manhattan world on first server start.
# Source: Necron, Planet Minecraft project 731925. Keep the creator attribution.

$ServerRoot = $PSScriptRoot
$WorldPath = Join-Path $ServerRoot "world"
$MarkerPath = Join-Path $WorldPath ".newyorkrp-map-installed"
$ArchivePath = Join-Path $ServerRoot "new-york-lower-manhattan.zip"
$ProjectUrl = "https://www.planetminecraft.com/project/new-york-lower-manhattan-1-1-scale-nyc/"
$DownloadUrl = "https://www.planetminecraft.com/project/new-york-lower-manhattan-1-1-scale-nyc/download/mirror/731925/"
$Headers = @{
    "User-Agent" = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    "Referer" = $ProjectUrl
    "Accept" = "text/html,application/xhtml+xml,application/xml;q=0.9,application/zip,application/octet-stream,*/*;q=0.8"
}

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
    # Planet Minecraft protects its download mirror against non-browser requests.
    Invoke-WebRequest -Uri $DownloadUrl -Headers $Headers -OutFile $ArchivePath -MaximumRedirection 10 -TimeoutSec 1800
    if (-not (Test-Path $ArchivePath) -or (Get-Item $ArchivePath).Length -lt 1024) {
        throw "The download response is empty or too small to be a world archive."
    }
} catch {
    if (Test-Path $ArchivePath) { Remove-Item $ArchivePath -Force }
    throw "Could not download the New York map from Planet Minecraft. The site may be blocking automated downloads. Server startup cancelled so a random vanilla world is not created. Details: $($_.Exception.Message)"
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
