<#
.SYNOPSIS
Build and verify a signed Drawly candidate on an explicitly selected QA emulator.
.DESCRIPTION
Uses installed tools and locked dependencies. Does not deploy or publish.
The selected emulator must be the project's NotesReview AVD; no physical device is used.
#>
param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [Parameter(Mandatory = $true)][string]$AndroidSdk,
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$Python = 'python',
    [switch]$Online
)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$adb = Join-Path $AndroidSdk 'platform-tools\adb.exe'
if ($Serial -notmatch '^emulator-\d+$') { throw 'Select the isolated NotesReview QA emulator.' }
if (-not (Test-Path -LiteralPath $adb)) { throw 'Android SDK platform-tools are missing.' }
if (-not $JavaHome -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\java.exe'))) {
    throw 'Supply -JavaHome with an installed JDK 17 or another version compatible with the pinned Gradle.'
}
$qemu = & $adb -s $Serial shell getprop ro.kernel.qemu
if ($LASTEXITCODE -ne 0 -or "$qemu".Trim() -ne '1') { throw 'The selected emulator is not available.' }
$avd = & $adb -s $Serial shell getprop ro.boot.qemu.avd_name
if ($LASTEXITCODE -ne 0 -or "$avd".Trim() -ne 'NotesReview') { throw 'Refusing to run against an AVD other than NotesReview.' }
$previousJava = $env:JAVA_HOME
$previousSerial = $env:ANDROID_SERIAL
Push-Location $projectRoot
try {
    $env:JAVA_HOME = $JavaHome
    $env:ANDROID_SERIAL = $Serial
    & node 'cloudflare/node_modules/typescript/bin/tsc' --project 'cloudflare/tsconfig.json' --noEmit
    if ($LASTEXITCODE -ne 0) { throw 'Worker typecheck failed. Run npm ci in cloudflare if dependencies are absent.' }
    & node --test 'cloudflare/test/worker.test.mjs'
    if ($LASTEXITCODE -ne 0) { throw 'Worker regression tests failed.' }
    $tasks = @(':app:testDebugUnitTest', ':app:lintRelease', ':app:assembleRelease', ':app:bundleRelease', ':app:connectedDebugAndroidTest', '--max-workers=1')
    if (-not $Online) { $tasks += '--offline' }
    & .\gradlew.bat @tasks
    if ($LASTEXITCODE -ne 0) { throw 'Android verification failed. Inspect app/build/reports and app/build/outputs/androidTest-results.' }
    $apk = 'app/build/outputs/apk/release/app-release.apk'
    $bundle = 'app/build/outputs/bundle/release/app-release.aab'
    & $Python 'tools/check-native-alignment.py' $apk $bundle
    if ($LASTEXITCODE -ne 0) { throw 'Native-library alignment failed.' }
    $apksigner = Get-ChildItem -LiteralPath (Join-Path $AndroidSdk 'build-tools') -Directory |
        Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' } |
        Sort-Object { [version]$_.Name } -Descending |
        ForEach-Object { Join-Path $_.FullName 'apksigner.bat' } |
        Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
    if (-not $apksigner) { throw 'Android SDK apksigner is missing.' }
    & $apksigner verify --verbose --print-certs $apk
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
    & (Join-Path $JavaHome 'bin\jarsigner.exe') -verify $bundle
    if ($LASTEXITCODE -ne 0) { throw 'Bundle signature verification failed.' }
    Write-Output 'Local verification passed. Review the audit report and distribution requirements before publication.'
} finally {
    $env:JAVA_HOME = $previousJava
    $env:ANDROID_SERIAL = $previousSerial
    Pop-Location
}
