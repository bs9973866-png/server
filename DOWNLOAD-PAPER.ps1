# Downloads the latest stable Paper 26.2 build and verifies its SHA-256.
$ErrorActionPreference = "Stop"
$Version = "26.2"
$UserAgent = "MinecraftServerSetup/1.0 (https://github.com/bs9973866-png/server)"
$ApiUrl = "https://fill.papermc.io/v3/projects/paper/versions/$Version/builds"

Write-Host "Checking PaperMC for a stable Paper $Version build..."
try {
    $builds = Invoke-RestMethod -Uri $ApiUrl -Headers @{ "User-Agent" = $UserAgent } -TimeoutSec 30
} catch {
    Write-Host "[ERROR] Could not contact the official PaperMC download service."
    throw
}

$build = $builds |
    Where-Object { $_.channel -eq "STABLE" -and $_.downloads.'server:default'.url } |
    Sort-Object -Property id -Descending |
    Select-Object -First 1

if (-not $build) {
    throw "No stable Paper $Version build is currently available."
}

$download = $build.downloads.'server:default'
$target = Join-Path $PSScriptRoot "paper-26.2.jar"
Write-Host "Downloading Paper $Version build #$($build.id)..."
Invoke-WebRequest -Uri $download.url -Headers @{ "User-Agent" = $UserAgent } -OutFile $target -TimeoutSec 300

$actualHash = (Get-FileHash -Path $target -Algorithm SHA256).Hash.ToLowerInvariant()
$expectedHash = [string]$download.checksums.sha256
if ($expectedHash -and $actualHash -ne $expectedHash.ToLowerInvariant()) {
    Remove-Item -LiteralPath $target -Force
    throw "SHA-256 verification failed. The downloaded file was deleted."
}

Write-Host ""
Write-Host "[SUCCESS] Paper $Version build #$($build.id) is ready."
Write-Host "File: $target"
Write-Host "SHA-256: $actualHash"
