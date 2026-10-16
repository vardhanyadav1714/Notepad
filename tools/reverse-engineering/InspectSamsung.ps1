<#
.SYNOPSIS
Reproduce the Samsung Notes UI investigation without executing or modifying the APK.
.DESCRIPTION
Uses the supplied REA checkout first, then the checkout's existing JADX engine
and the Android SDK's aapt2 for focused static inspection. Raw results stay in
the ignored samsung_app/analysis directory. No tools or integrations are installed.
#>
param(
    [string]$ReaRoot = 'C:\Users\vardh\rea',
    [string]$JavaHome = 'C:\Program Files\Android\Android Studio\jbr',
    [string]$AndroidSdk = 'C:\Users\vardh\AppData\Local\Android\Sdk',
    [string]$ApkPath
)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
if (-not $ApkPath) {
    $candidates = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'samsung_app') -Filter '*.apk')
    if ($candidates.Count -ne 1) { throw 'Supply -ApkPath when there is not exactly one reference APK.' }
    $ApkPath = $candidates[0].FullName
}
$resolvedApk = (Resolve-Path -LiteralPath $ApkPath).Path
$output = Join-Path $projectRoot 'samsung_app\analysis'
$jar = Join-Path $ReaRoot '_reference\apk-integration\jadx-headless-mcp-0.7.1-all.jar'
$java = Join-Path $JavaHome 'bin\java.exe'
$aapt = Join-Path $AndroidSdk 'build-tools\35.0.0\aapt2.exe'
foreach ($required in @($resolvedApk, $java, $jar, $aapt)) {
    if (-not (Test-Path -LiteralPath $required)) { throw "Missing prerequisite: $required" }
}
New-Item -ItemType Directory -Path $output -Force | Out-Null
$previousJava = $env:JAVA_HOME
$previousJadx = $env:REA_JADX_MCP_JAR
try {
    $env:JAVA_HOME = $JavaHome
    $env:REA_JADX_MCP_JAR = $jar
    & node (Join-Path $ReaRoot 'scripts\rea.mjs') inspect-android-package $resolvedApk --json |
        Set-Content -LiteralPath (Join-Path $output 'rea-package-evidence.json') -Encoding utf8
    if ($LASTEXITCODE -ne 0) { Write-Warning 'REA package inspection unavailable. Its diagnostic is retained; proceeding with direct JADX and aapt2.' }
} finally {
    $env:JAVA_HOME = $previousJava
    $env:REA_JADX_MCP_JAR = $previousJadx
}
& $aapt dump badging $resolvedApk | Set-Content -LiteralPath (Join-Path $output 'badging.txt') -Encoding utf8
& $aapt dump resources $resolvedApk | Set-Content -LiteralPath (Join-Path $output 'resources.txt') -Encoding utf8
$classes = @(
    'com.samsung.android.app.notes.memolist.MemoListActivity',
    'com.samsung.android.app.notes.memolist.MemoListBaseActivity',
    'com.samsung.android.app.notes.composer.ComposerBaseActivity',
    'com.samsung.android.support.senl.nt.app.main.noteslist.NotesFragment',
    'com.samsung.android.support.senl.nt.app.main.noteslist.adapter.NotesAdapter',
    'com.samsung.android.support.senl.nt.app.main.noteslist.presenter.AdapterManager'
)
& $java -Xmx4g --class-path $jar (Join-Path $PSScriptRoot 'InspectSamsung.java') $resolvedApk $output @classes
if ($LASTEXITCODE -ne 0) { throw 'Focused JADX inspection failed; inspect the engine diagnostic.' }
@{
    apk = $resolvedApk
    apkSha256 = (Get-FileHash -LiteralPath $resolvedApk -Algorithm SHA256).Hash
    engineJar = $jar
    engineSha256 = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash
    observation = 'Static inspection only; target APK not executed.'
} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $output 'provenance.json') -Encoding utf8
Write-Output "Inspection saved to $output"
