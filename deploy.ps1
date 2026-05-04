# deploy.ps1 — build, install, AOT-compile, and launch Actifit on connected device

$ErrorActionPreference = "Stop"

$JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$ADB       = "C:\Users\mcfar\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$PACKAGE   = "io.actifit.fitnesstracker.actifitfitnesstracker"
$ACTIVITY  = "$PACKAGE/.LoginActivity"

$env:JAVA_HOME = $JAVA_HOME
$env:PATH = "$JAVA_HOME\bin;$env:PATH"

# Check device
$devices = & $ADB devices | Select-String "device$"
if (-not $devices) {
    Write-Host "ERROR: No device/emulator connected." -ForegroundColor Red
    exit 1
}

# Build
Write-Host "`n==> Building debug APK..." -ForegroundColor Cyan
.\gradlew.bat assembleDebug
if ($LASTEXITCODE -ne 0) { Write-Host "BUILD FAILED" -ForegroundColor Red; exit 1 }

# Find APK
$apk = (Get-ChildItem "app\build\outputs\apk\debug\*.apk" | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
Write-Host "`n==> Installing: $apk" -ForegroundColor Cyan
& $ADB install -r $apk

# AOT compile for faster startup
Write-Host "`n==> AOT compiling on device..." -ForegroundColor Cyan
& $ADB shell cmd package compile -m speed $PACKAGE

# Launch
Write-Host "`n==> Launching app..." -ForegroundColor Cyan
& $ADB shell am start -n $ACTIVITY

Write-Host "`nDone." -ForegroundColor Green
