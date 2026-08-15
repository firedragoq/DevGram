$ErrorActionPreference = "Stop"

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    Write-Host "adb was not found. Install Android SDK Platform Tools and add adb.exe to PATH." -ForegroundColor Red
    exit 1
}

$token = Read-Host "Paste the Dev Server token from DevGram"
if ([string]::IsNullOrWhiteSpace($token)) {
    Write-Host "Token is empty." -ForegroundColor Red
    exit 1
}
$env:DEVGRAM_TOKEN = $token.Trim()

adb devices
if (Get-Command py -ErrorAction SilentlyContinue) {
    py -3 .\devgram_dev.py status
    py -3 .\devgram_dev.py upload .\DevGram-DevServerDemo-1.0.0.dgplugin
} elseif (Get-Command python -ErrorAction SilentlyContinue) {
    python .\devgram_dev.py status
    python .\devgram_dev.py upload .\DevGram-DevServerDemo-1.0.0.dgplugin
} else {
    Write-Host "Python 3 was not found." -ForegroundColor Red
    exit 1
}

Write-Host "Done. Open DevGram > Plugins." -ForegroundColor Green
