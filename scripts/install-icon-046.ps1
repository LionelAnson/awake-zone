$ErrorActionPreference='Stop'
$installDir=Join-Path (Join-Path 'E:' ([string]::Concat([char]0x5E94,[char]0x7528))) 'Personal Day'
$installed=Join-Path $installDir 'personal-day.exe'
$out=Join-Path $PWD 'output/icon-046/install'
New-Item -ItemType Directory -Force -Path $out | Out-Null
$state=Join-Path $env:APPDATA 'com.personalday.widget/state.json'
$startupKey='HKCU:\Software\Microsoft\Windows\CurrentVersion\Run'
$before=@{stateHash=(Get-FileHash -LiteralPath $state).Hash;startup=(Get-Item $startupKey).GetValue('Personal Day');exeHash=(Get-FileHash -LiteralPath $installed).Hash;state=Get-Content -Raw -LiteralPath $state | ConvertFrom-Json}
[IO.File]::WriteAllText((Join-Path $out 'before.json'),($before | ConvertTo-Json -Depth 8),[Text.UTF8Encoding]::new($false))
# Ask the current instance to persist its latest position before replacing only its EXE.
$targets=@(Get-CimInstance Win32_Process -Filter "Name='personal-day.exe'" | Where-Object { $_.ExecutablePath -eq $installed })
foreach($target in $targets){
 $env:PD_CLOSING_PID=[string]$target.ProcessId
 @'
import ctypes as c,ctypes.wintypes as w,os,time
u=c.windll.user32;pid=int(os.environ['PD_CLOSING_PID']);CB=c.WINFUNCTYPE(w.BOOL,w.HWND,w.LPARAM)
@CB
def child(h,l):
 p=w.DWORD();u.GetWindowThreadProcessId(h,c.byref(p))
 if p.value==pid:
  t=c.create_unicode_buffer(256);u.GetWindowTextW(h,t,256)
  if 'Personal Day' in t.value:u.PostMessageW(h,0x10,0,0)
 return True
@CB
def top(h,l):
 child(h,l);u.EnumChildWindows(h,child,0);return True
u.EnumWindows(top,0);time.sleep(.6)
'@ | python -
 Remove-Item Env:PD_CLOSING_PID
 Stop-Process -Id $target.ProcessId
}
[IO.File]::WriteAllBytes((Join-Path $out 'state-before.json'),[IO.File]::ReadAllBytes($state))
$stableHash=(Get-FileHash -LiteralPath $state).Hash
$setup=Join-Path $PWD 'release/Personal Day_0.4.6_x64-setup.exe'
Copy-Item -LiteralPath 'output/playwright/backdrop-runs/build-target/release/bundle/nsis/Personal Day_0.4.6_x64-setup.exe' -Destination $setup -Force
$installer=Start-Process -FilePath $setup -ArgumentList '/S',("/D=$installDir") -WindowStyle Hidden -PassThru
$installer.WaitForExit()
if($installer.ExitCode -ne 0){throw "Installer failed: $($installer.ExitCode)"}
$after=@{installerExit=$installer.ExitCode;stateHash=(Get-FileHash -LiteralPath $state).Hash;startup=(Get-Item $startupKey).GetValue('Personal Day');exeHash=(Get-FileHash -LiteralPath $installed).Hash;version=(Get-Item -LiteralPath $installed).VersionInfo.FileVersion;state=Get-Content -Raw -LiteralPath $state | ConvertFrom-Json}
[IO.File]::WriteAllText((Join-Path $out 'after.json'),($after | ConvertTo-Json -Depth 8),[Text.UTF8Encoding]::new($false))
if($after.stateHash -ne $stableHash -or $after.startup -ne $before.startup){throw 'Settings or autostart changed during installation'}
$previousWebViewArgs=$env:WEBVIEW2_ADDITIONAL_BROWSER_ARGUMENTS
try{
 $env:WEBVIEW2_ADDITIONAL_BROWSER_ARGUMENTS='--remote-debugging-port=9226'
 $clock=Start-Process -FilePath $installed -ArgumentList '--autostart' -WindowStyle Hidden -PassThru
}finally{
 if($null -eq $previousWebViewArgs){Remove-Item Env:WEBVIEW2_ADDITIONAL_BROWSER_ARGUMENTS -ErrorAction SilentlyContinue}else{$env:WEBVIEW2_ADDITIONAL_BROWSER_ARGUMENTS=$previousWebViewArgs}
}
@{pid=$clock.Id;version=$after.version;stateUnchanged=($after.stateHash -eq $stableHash);startupUnchanged=($after.startup -eq $before.startup)} | ConvertTo-Json
