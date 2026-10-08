param([int]$X=1400,[int]$Y=600,[string]$ColorFile)
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
Add-Type @'
using System;using System.Runtime.InteropServices;
public class BackdropTestNative { [DllImport("user32.dll")] public static extern bool SetProcessDpiAwarenessContext(IntPtr c); }
'@
[void][BackdropTestNative]::SetProcessDpiAwarenessContext([IntPtr]::new(-4))
$form=New-Object System.Windows.Forms.Form
$form.Text='Personal Day - background test (auto close)'
$form.StartPosition='Manual'
$form.Location=New-Object System.Drawing.Point($X,$Y)
$form.ClientSize=New-Object System.Drawing.Size(480,350)
$form.BackColor=[System.Drawing.Color]::DarkSlateBlue
$script:backgroundSpec='stripes'
$form.Add_Paint({param($sender,$eventArgs)
  if($script:backgroundSpec -eq 'stripes'){for($i=0;$i -lt 480;$i+=40){$eventArgs.Graphics.FillRectangle([System.Drawing.Brushes]::IndianRed,$i,0,20,350)}}
})
$timer=New-Object System.Windows.Forms.Timer
$script:deadline=[DateTime]::UtcNow.AddSeconds(60)
$timer.Interval=200
$timer.Add_Tick({
  if([DateTime]::UtcNow -ge $script:deadline){$form.Close();return}
  if($ColorFile -and (Test-Path -LiteralPath $ColorFile)){
    $next=[IO.File]::ReadAllText($ColorFile).Trim()
    if($next -ne $script:backgroundSpec){
      $script:backgroundSpec=$next
      $form.BackColor=if($next -eq 'stripes'){[System.Drawing.Color]::DarkSlateBlue}else{[System.Drawing.ColorTranslator]::FromHtml($next)}
      $form.Invalidate()
    }
  }
})
$timer.Start()
[System.Windows.Forms.Application]::Run($form)
$timer.Dispose();$form.Dispose()
