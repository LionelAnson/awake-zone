param(
    [string[]]$Tasks = @(':core:test', ':app:testDebugUnitTest', ':app:lintDebug', ':app:assembleDebug'),
    [switch]$WriteLocks
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'enter-env.ps1')
$sourceRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$toolRoot = Join-Path $env:LOCALAPPDATA 'PersonalDayAndroidToolchain'
# JDK 17 on Windows can misread non-ASCII classpaths in Gradle worker argfiles.
# Use a fresh ASCII copy; never delete, mirror-delete, or move the original project.
$runId = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
$buildRoot = Join-Path $toolRoot "runs\$runId"
if ($buildRoot -match '[^\x00-\x7F]') { throw 'An ASCII LOCALAPPDATA path is required by this helper.' }
[IO.Directory]::CreateDirectory($buildRoot) | Out-Null
$manifest = @()
foreach ($file in [IO.Directory]::EnumerateFiles($sourceRoot, '*', [IO.SearchOption]::AllDirectories)) {
    $relative = [IO.Path]::GetRelativePath($sourceRoot, $file)
    if ($relative -match '(^|[\\/])(build|\.gradle|\.kotlin|artifacts)([\\/]|$)' -or $relative -eq 'local.properties') { continue }
    if ($relative -match '\.(keystore|jks)$') { continue }
    $destination = Join-Path $buildRoot $relative
    [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($destination)) | Out-Null
    [IO.File]::WriteAllBytes($destination, [IO.File]::ReadAllBytes($file))
    $manifest += [PSCustomObject]@{ path = $relative; sha256 = (Get-FileHash -LiteralPath $file).Hash }
}
$artifactRoot = Join-Path $sourceRoot "artifacts\$runId"
[IO.Directory]::CreateDirectory($artifactRoot) | Out-Null
$manifest | ConvertTo-Json -Depth 4 | Set-Content (Join-Path $artifactRoot 'source-sha256.json') -Encoding utf8
$arguments = @($Tasks) + @('--max-workers=1', '--console=plain')
if ($WriteLocks) { $arguments += '--write-locks' }
Write-Output "Build copy: $buildRoot"
Push-Location $buildRoot
try {
    & .\gradlew.bat @arguments 2>&1 | Tee-Object -FilePath (Join-Path $artifactRoot 'build.log')
    $buildExit = $LASTEXITCODE
} finally { Pop-Location }
foreach ($module in @('core', 'app')) {
    foreach ($sub in @('build\test-results', 'build\reports')) {
        $from = Join-Path $buildRoot "$module\$sub"
        if (Test-Path -LiteralPath $from) {
            foreach ($file in [IO.Directory]::EnumerateFiles($from, '*', [IO.SearchOption]::AllDirectories)) {
                $target = Join-Path $artifactRoot "$module\$sub\$([IO.Path]::GetRelativePath($from, $file))"
                [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($target)) | Out-Null
                [IO.File]::WriteAllBytes($target, [IO.File]::ReadAllBytes($file))
            }
        }
    }
    $lock = Join-Path $buildRoot "$module\gradle.lockfile"
    if ($WriteLocks -and $buildExit -eq 0 -and (Test-Path -LiteralPath $lock)) {
        [IO.File]::WriteAllBytes((Join-Path $sourceRoot "$module\gradle.lockfile"), [IO.File]::ReadAllBytes($lock))
    }
}
$apk = Join-Path $buildRoot 'app\build\outputs\apk\debug\app-debug.apk'
if ($buildExit -eq 0 -and (Test-Path -LiteralPath $apk)) {
    $metadata = Get-Content (Join-Path $buildRoot 'app\build\outputs\apk\debug\output-metadata.json') -Raw | ConvertFrom-Json
    $version = $metadata.elements[0].versionName
    if ($version -notmatch '^\d+\.\d+\.\d+$') { throw 'Unexpected APK versionName' }
    $destination = Join-Path $artifactRoot "PersonalDay-Android-$version-debug.apk"
    [IO.File]::WriteAllBytes($destination, [IO.File]::ReadAllBytes($apk))
    Get-FileHash -LiteralPath $destination | Format-List | Out-String | Set-Content (Join-Path $artifactRoot 'apk-sha256.txt')
    Write-Output "APK: $destination"
}
[PSCustomObject]@{ runId = $runId; buildRoot = $buildRoot; exitCode = $buildExit; tasks = $Tasks } |
    ConvertTo-Json | Set-Content (Join-Path $artifactRoot 'run.json') -Encoding utf8
if ($buildExit -ne 0) { throw "Build failed with exit code $buildExit. Evidence: $artifactRoot" }
