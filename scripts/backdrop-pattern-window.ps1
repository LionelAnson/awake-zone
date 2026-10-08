param([string]$ControlFile,[string]$MetadataFile)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
Add-Type @'
using System;using System.Runtime.InteropServices;
public class PatternNative {
 [DllImport("user32.dll")] public static extern bool SetProcessDpiAwarenessContext(IntPtr c);
 [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
}
'@
[void][PatternNative]::SetProcessDpiAwarenessContext([IntPtr]::new(-4))
$form=New-Object System.Windows.Forms.Form
$form.Text='Personal Day - isolated background patterns'
$form.StartPosition='Manual'
$form.Location=New-Object System.Drawing.Point(40,100)
$form.ClientSize=New-Object System.Drawing.Size(620,500)
$form.BackColor=[Drawing.Color]::White
$script:pattern='white'
$form.Add_Paint({param($sender,$e)
 if($script:pattern -in @('stripes','bw')) {
  $e.Graphics.Clear($(if($script:pattern -eq 'bw'){[Drawing.Color]::White}else{[Drawing.Color]::Blue}))
  $brush=if($script:pattern -eq 'bw'){[Drawing.Brushes]::Black}else{[Drawing.Brushes]::Red}
  for($x=0;$x -lt 620;$x+=80){$e.Graphics.FillRectangle($brush,$x,0,40,500)}
 }
})
$timer=New-Object System.Windows.Forms.Timer
$deadline=[DateTime]::UtcNow.AddMinutes(4)
$timer.Interval=100
$timer.Add_Tick({
 if([DateTime]::UtcNow -gt $deadline){$form.Close();return}
 if(Test-Path -LiteralPath $ControlFile){
  $value=[IO.File]::ReadAllText($ControlFile).Trim()
  if($value -eq 'exit'){$form.Close();return}
  if($value -and $value -ne $script:pattern){
   $script:pattern=$value
   if($value -notin @('stripes','bw')){$form.BackColor=[Drawing.ColorTranslator]::FromHtml($value)}
   $form.Invalidate()
  }
 }
 $data=@{hwnd=$form.Handle.ToInt64();foreground=[PatternNative]::GetForegroundWindow().ToInt64();pattern=$script:pattern;bounds=@($form.Left,$form.Top,$form.Width,$form.Height);utc=[DateTime]::UtcNow.ToString('o')} | ConvertTo-Json -Compress
 [IO.File]::WriteAllText($MetadataFile+'.tmp',$data)
 Move-Item -LiteralPath ($MetadataFile+'.tmp') -Destination $MetadataFile -Force
})
$timer.Start()
try {[System.Windows.Forms.Application]::Run($form)} finally {$timer.Dispose();$form.Dispose()}
