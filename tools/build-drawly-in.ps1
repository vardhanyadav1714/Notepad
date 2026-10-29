param(
    [switch]$Preview,
    [int]$VersionCode = 33,
    [string]$VersionName = '2.4.3',
    [string]$JavaHome = 'C:\Program Files\Android\Android Studio\jbr',
    [switch]$Online
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if ($VersionCode -lt 32) { throw 'New-domain builds must use a version code of at least 32.' }
if ($VersionName -notmatch '^[A-Za-z0-9][A-Za-z0-9.+-]*$') { throw 'Invalid version name.' }
if (-not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\java.exe'))) { throw 'Supply an installed Android-compatible JDK using -JavaHome.' }
$previousJava = $env:JAVA_HOME
Push-Location $projectRoot
try {
    $env:JAVA_HOME = $JavaHome
    $arguments = @('-PcollaborationOrigin=https://drawly.in', "-PdrawlyVersionCode=$VersionCode", "-PdrawlyVersionName=$VersionName")
    if ($Preview) {
        $arguments += @('-PdevicePreview=true', ':app:testDebugUnitTest', ':app:assembleDebug', ':app:assembleDebugAndroidTest')
        $variant = 'debug'
        $package = 'in.innovaticshub.notepad.preview'
    } else {
        $arguments += @('-PdevicePreview=false', ':app:testReleaseUnitTest', ':app:lintRelease', ':app:assembleRelease', ':app:bundleRelease')
        $variant = 'release'
        $package = 'in.innovaticshub.notepad'
    }
    if (-not $Online) { $arguments += '--offline' }
    $arguments += '--max-workers=1'
    # Windows PowerShell wraps native stderr (including harmless JVM warnings)
    # as ErrorRecord objects. Preserve the text and use the process exit code.
    $nativePreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & .\gradlew.bat @arguments 2>&1 | ForEach-Object { Write-Output $_.ToString() }
        $gradleExitCode = $LASTEXITCODE
    } finally { $ErrorActionPreference = $nativePreference }
    if ($gradleExitCode -ne 0) { throw 'Drawly new-domain build/checks failed.' }
    $config = Get-Content -LiteralPath "app/build/generated/source/buildConfig/$variant/in/innovaticshub/notepad/BuildConfig.java" -Raw
    if ($config -notmatch 'COLLABORATION_ORIGIN = "https://drawly\.in"') { throw 'Built configuration does not use drawly.in.' }
    $destination = Join-Path $projectRoot 'app/build/outputs/drawly-in'
    New-Item -ItemType Directory -Force -Path $destination | Out-Null
    $suffix = if ($Preview) { 'preview' } else { 'release' }
    $base = "Drawly-$VersionName-drawly-in-$suffix"
    $apk = Join-Path $destination "$base.apk"
    Copy-Item -LiteralPath "app/build/outputs/apk/$variant/app-$variant.apk" -Destination $apk
    $artifacts = @(@{ file = [IO.Path]::GetFileName($apk); sha256 = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant() })
    if (-not $Preview) {
        $bundle = Join-Path $destination "$base.aab"
        Copy-Item -LiteralPath 'app/build/outputs/bundle/release/app-release.aab' -Destination $bundle
        $artifacts += @{ file = [IO.Path]::GetFileName($bundle); sha256 = (Get-FileHash -LiteralPath $bundle -Algorithm SHA256).Hash.ToLowerInvariant() }
    }
    @{ origin = 'https://drawly.in'; applicationId = $package; versionCode = $VersionCode; versionName = $VersionName; artifacts = $artifacts } |
        ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $destination "$base.json")
    Write-Output "Verified new-domain $suffix artifacts: $destination"
} finally {
    $env:JAVA_HOME = $previousJava
    Pop-Location
}
