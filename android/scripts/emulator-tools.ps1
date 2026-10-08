param(
    [Parameter(Mandatory)][ValidatePattern('^emulator-[0-9]+$')][string]$Serial,
    [Parameter(Mandatory)][string]$EvidenceDir
)
# Dot-source this helper. It refuses physical devices and records only the selected AVD.
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'enter-env.ps1')
$script:DayAdb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
$script:DaySerial = $Serial
$script:DayEvidence = [IO.Path]::GetFullPath($EvidenceDir)
[IO.Directory]::CreateDirectory($script:DayEvidence) | Out-Null
$emulated = & $script:DayAdb -s $Serial shell getprop ro.kernel.qemu
if ($LASTEXITCODE -ne 0 -or "$emulated".Trim() -ne '1') { throw 'Selected device is not a running emulator' }

function Invoke-DayAdb([string[]]$DeviceArgs) {
    [PSCustomObject]@{utc=[DateTimeOffset]::UtcNow.ToString('o'); serial=$script:DaySerial; args=$DeviceArgs} |
        ConvertTo-Json -Compress | Add-Content -LiteralPath (Join-Path $script:DayEvidence 'commands.jsonl') -Encoding utf8
    $lines = & $script:DayAdb -s $script:DaySerial @DeviceArgs 2>&1
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $lines" }
    $lines
}

function Get-DayUi {
    Invoke-DayAdb @('shell','uiautomator','dump','/sdcard/personalday-ui.xml') | Out-Null
    $path = Join-Path $script:DayEvidence 'latest-ui.xml'
    Invoke-DayAdb @('pull','/sdcard/personalday-ui.xml',$path) | Out-Null
    [xml](Get-Content -LiteralPath $path -Raw)
}

function Save-DayFrame([ValidatePattern('^[a-zA-Z0-9_-]+$')][string]$Name) {
    $before = Invoke-DayAdb @('shell','date','+%s')
    $ui = Get-DayUi
    $ui.Save((Join-Path $script:DayEvidence "$Name.xml"))
    Invoke-DayAdb @('shell','screencap','-p','/sdcard/personalday-screen.png') | Out-Null
    Invoke-DayAdb @('pull','/sdcard/personalday-screen.png',(Join-Path $script:DayEvidence "$Name.png")) | Out-Null
    $after = Invoke-DayAdb @('shell','date','+%s')
    [PSCustomObject]@{name=$Name;beforeUnix="$before".Trim();afterUnix="$after".Trim()} |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $script:DayEvidence "$Name-time.json") -Encoding utf8
    $ui.SelectNodes('//node') | Where-Object { $_.text -or $_.'content-desc' } |
        Select-Object text,resource-id,content-desc,bounds
}

function Tap-DayText([string]$Text) {
    $ui = Get-DayUi
    $node = $ui.SelectNodes('//node') | Where-Object { $_.text -eq $Text -and $_.enabled -eq 'true' } | Select-Object -First 1
    if (-not $node) { throw "Visible enabled text not found: $Text" }
    if ($node.bounds -notmatch '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') { throw 'No valid bounds' }
    $x = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
    $y = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
    Invoke-DayAdb @('shell','input','tap',"$x","$y") | Out-Null
}
