# Dot source: . .\scripts\enter-env.ps1
# Only updates this PowerShell process, never the machine/user environment.
$ErrorActionPreference = 'Stop'
$taskTools = Join-Path $env:LOCALAPPDATA 'PersonalDayAndroidToolchain'
if (-not $env:JAVA_HOME) {
    $candidate = Join-Path $taskTools 'corretto\jdk17.0.20_12'
    if (Test-Path -LiteralPath "$candidate\bin\java.exe") { $env:JAVA_HOME = $candidate }
}
if (-not $env:ANDROID_HOME) {
    $candidate = Join-Path $taskTools 'sdk'
    if (Test-Path -LiteralPath "$candidate\platforms\android-36\android.jar") { $env:ANDROID_HOME = $candidate }
}
if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath "$env:JAVA_HOME\bin\java.exe")) {
    throw 'Set JAVA_HOME to an installed JDK 17 directory.'
}
if (-not $env:ANDROID_HOME -or -not (Test-Path -LiteralPath "$env:ANDROID_HOME\platforms\android-36\android.jar")) {
    throw 'Set ANDROID_HOME to an Android SDK with platform 36 and build-tools 35.0.0.'
}
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:PATH"
