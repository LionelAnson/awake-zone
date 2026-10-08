param([ValidateSet('open','inspect','quit','toggle','settings')][string]$Action='inspect')
$ErrorActionPreference='Stop'
Add-Type -AssemblyName UIAutomationClient
Add-Type -AssemblyName UIAutomationTypes
Add-Type @'
using System;using System.Runtime.InteropServices;
public class TrayMouse {
 [StructLayout(LayoutKind.Sequential)] public struct Point { public int X,Y; }
 [DllImport("user32.dll")] public static extern bool SetProcessDpiAwarenessContext(IntPtr value);
 [DllImport("user32.dll")] public static extern bool GetCursorPos(out Point p);
 [DllImport("user32.dll")] public static extern bool SetCursorPos(int x,int y);
 [DllImport("user32.dll")] public static extern void mouse_event(uint flags,uint x,uint y,uint data,UIntPtr extra);
 [DllImport("user32.dll")] public static extern void keybd_event(byte key,byte scan,uint flags,UIntPtr extra);
}
'@
[void][TrayMouse]::SetProcessDpiAwarenessContext([IntPtr]::new(-4))
function Click($element){
 $invoke=$null
 if($element.TryGetCurrentPattern([Windows.Automation.InvokePattern]::Pattern,[ref]$invoke)){$invoke.Invoke();Start-Sleep -Milliseconds 300;return}
 $r=$element.Current.BoundingRectangle
 if($r.Width -le 0 -or $r.Height -le 0){throw 'Tray item is not visible'}
 [void][TrayMouse]::SetCursorPos([int]($r.X+$r.Width/2),[int]($r.Y+$r.Height/2))
 [TrayMouse]::mouse_event(2,0,0,0,[UIntPtr]::Zero);[TrayMouse]::mouse_event(4,0,0,0,[UIntPtr]::Zero)
 Start-Sleep -Milliseconds 250
}
function NameCondition($name){New-Object Windows.Automation.PropertyCondition([Windows.Automation.AutomationElement]::NameProperty,$name)}
$root=[Windows.Automation.AutomationElement]::RootElement
$old=New-Object TrayMouse+Point;[void][TrayMouse]::GetCursorPos([ref]$old)
try {
 $tray=$root.FindFirst([Windows.Automation.TreeScope]::Children,(New-Object Windows.Automation.PropertyCondition([Windows.Automation.AutomationElement]::ClassNameProperty,'Shell_TrayWnd')))
 $icon=$tray.FindFirst([Windows.Automation.TreeScope]::Descendants,(NameCondition '个人时钟 / Personal Day'))
 if(!$icon){
  $chevron=$tray.FindFirst([Windows.Automation.TreeScope]::Descendants,(NameCondition '显示隐藏的图标'))
  if(!$chevron){throw 'Tray overflow button unavailable'}
  $chevron.GetCurrentPattern([Windows.Automation.InvokePattern]::Pattern).Invoke()
  Start-Sleep -Milliseconds 300
  foreach($child in $root.FindAll([Windows.Automation.TreeScope]::Children,[Windows.Automation.Condition]::TrueCondition)){
   if($child.Current.ClassName -match 'Overflow'){$icon=$child.FindFirst([Windows.Automation.TreeScope]::Descendants,(NameCondition '个人时钟 / Personal Day'));if($icon){break}}
  }
 }
 if(!$icon){
  $candidates=$root.FindAll([Windows.Automation.TreeScope]::Descendants,(New-Object Windows.Automation.PropertyCondition([Windows.Automation.AutomationElement]::ControlTypeProperty,[Windows.Automation.ControlType]::Button)))
  $icon=$candidates | Where-Object {$_.Current.Name -like '*个人时钟 / Personal Day*'} | Select-Object -First 1
 }
 if(!$icon){throw 'Personal Day tray icon unavailable'}
 Click $icon
 if($Action -eq 'open'){return}
 $menu=$root.FindFirst([Windows.Automation.TreeScope]::Children,(New-Object Windows.Automation.PropertyCondition([Windows.Automation.AutomationElement]::ControlTypeProperty,[Windows.Automation.ControlType]::Menu)))
 for($i=0;$i -lt 30 -and !$menu;$i++){Start-Sleep -Milliseconds 100;$menu=$root.FindFirst([Windows.Automation.TreeScope]::Children,(New-Object Windows.Automation.PropertyCondition([Windows.Automation.AutomationElement]::ControlTypeProperty,[Windows.Automation.ControlType]::Menu)))}
 if(!$menu){throw 'Tray menu unavailable'}
 $items=$menu.FindAll([Windows.Automation.TreeScope]::Descendants,(New-Object Windows.Automation.PropertyCondition([Windows.Automation.AutomationElement]::ControlTypeProperty,[Windows.Automation.ControlType]::MenuItem)))
 $names=@($items|ForEach-Object{$_.Current.Name})
 if($Action -ne 'inspect'){
  $name=switch($Action){'quit'{'退出'} 'toggle'{'显示 / 隐藏'} 'settings'{'设置…'}}
  $item=$items|Where-Object{$_.Current.Name -eq $name}|Select-Object -First 1
  if(!$item){throw "Menu action unavailable: $name"};Click $item
 }else{[TrayMouse]::keybd_event(27,0,0,[UIntPtr]::Zero);[TrayMouse]::keybd_event(27,0,2,[UIntPtr]::Zero)}
 @{action=$Action;items=$names;clicked=($Action -ne 'inspect')}|ConvertTo-Json -Compress
}finally{[void][TrayMouse]::SetCursorPos($old.X,$old.Y)}
