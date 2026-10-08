$ErrorActionPreference='Stop'
$installDir=Join-Path (Join-Path 'E:' ([string]::Concat([char]0x5E94,[char]0x7528))) 'Personal Day'
$installed=Join-Path $installDir 'personal-day.exe'
$state=Join-Path $env:APPDATA 'com.personalday.widget/state.json'
$startupKey='HKCU:\Software\Microsoft\Windows\CurrentVersion\Run'
$beforeHash=(Get-FileHash -LiteralPath $state).Hash
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
  if 'Awake Zone' in t.value or 'Personal Day' in t.value:u.PostMessageW(h,0x10,0,0)
 return True
@CB
def top(h,l):
 child(h,l);u.EnumChildWindows(h,child,0);return True
u.EnumWindows(top,0);time.sleep(.6)
'@ | python -
 Remove-Item Env:PD_CLOSING_PID
 Stop-Process -Id $target.ProcessId -ErrorAction SilentlyContinue
}
$clock=Start-Process -FilePath $installed -ArgumentList '--autostart' -WindowStyle Hidden -PassThru
Start-Sleep -Seconds 3
$result=@{
 pid=$clock.Id
 version=(Get-Item -LiteralPath $installed).VersionInfo.FileVersion
 exeHash=(Get-FileHash -LiteralPath $installed).Hash
 stateHash=(Get-FileHash -LiteralPath $state).Hash
 stateUnchanged=((Get-FileHash -LiteralPath $state).Hash -eq $beforeHash)
 startup=(Get-Item $startupKey).GetValue('Personal Day')
 debugPortClosed=(-not [bool](Get-NetTCPConnection -LocalPort 9226 -State Listen -ErrorAction SilentlyContinue))
 processCount=@(Get-CimInstance Win32_Process -Filter "Name='personal-day.exe'" | Where-Object { $_.ExecutablePath -eq $installed }).Count
}
$result | ConvertTo-Json -Depth 8 | Set-Content -Encoding utf8 output/icon-046/final-running.json
$result | ConvertTo-Json -Depth 8
