param([ValidateSet('inspect','move','close','minimize','desktop','drag','hotkey','top','focus-test-app')] [string]$Action = 'inspect', [long]$Handle, [int]$X = 100, [int]$Y = 100, [switch]$AlternateHotkey, [int]$HoldMs = 750)
$ErrorActionPreference = 'Stop'
Add-Type @'
using System;
using System.Runtime.InteropServices;
public class PersonalDayWindowProbe {
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
  [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
  [StructLayout(LayoutKind.Sequential)] public struct Point { public int X, Y; }
  [DllImport("user32.dll")] public static extern bool SetProcessDpiAwarenessContext(IntPtr context);
  [StructLayout(LayoutKind.Sequential)] public struct Rect { public int Left, Top, Right, Bottom; }
  [DllImport("user32.dll")] public static extern bool IsIconic(IntPtr hwnd);
  [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr hwnd);
  [DllImport("user32.dll")] public static extern bool GetClientRect(IntPtr hwnd, out Rect rect);
  [DllImport("user32.dll")] public static extern bool ClientToScreen(IntPtr hwnd, ref Point point);
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hwnd, out Rect rect);
  [DllImport("user32.dll")] public static extern int GetWindowLong(IntPtr hwnd, int index);
  [DllImport("user32.dll")] public static extern bool SetWindowPos(IntPtr hwnd, IntPtr after, int x, int y, int cx, int cy, uint flags);
  [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr hwnd, uint msg, IntPtr w, IntPtr l);
  [DllImport("user32.dll")] public static extern IntPtr GetParent(IntPtr hwnd);
  [DllImport("user32.dll")] public static extern bool IsChild(IntPtr parent, IntPtr child);
  [DllImport("user32.dll")] public static extern IntPtr WindowFromPoint(Point point);
  [DllImport("user32.dll")] public static extern bool ScreenToClient(IntPtr hwnd, ref Point point);
  [DllImport("user32.dll")] public static extern bool GetCursorPos(out Point point);
  [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
  [DllImport("user32.dll")] public static extern void mouse_event(uint flags, uint x, uint y, uint data, UIntPtr extra);
  [DllImport("user32.dll")] public static extern void keybd_event(byte key, byte scan, uint flags, UIntPtr extra);
}
'@
[void][PersonalDayWindowProbe]::SetProcessDpiAwarenessContext([IntPtr]::new(-4))
$windowHandle = [IntPtr]::new($Handle)
if ($Action -eq 'top') { [void][PersonalDayWindowProbe]::SetWindowPos($windowHandle,[IntPtr]::new(-1),0,0,0,0,0x13) }
if ($Action -eq 'move') {
  $point = New-Object PersonalDayWindowProbe+Point
  $point.X = $X; $point.Y = $Y
  if (([PersonalDayWindowProbe]::GetWindowLong($windowHandle, -16) -band 0x40000000) -ne 0) {
    [void][PersonalDayWindowProbe]::ScreenToClient([PersonalDayWindowProbe]::GetParent($windowHandle), [ref]$point)
  }
  [void][PersonalDayWindowProbe]::SetWindowPos($windowHandle, [IntPtr]::Zero, $point.X, $point.Y, 0, 0, 0x15)
}
if ($Action -eq 'minimize') { [void][PersonalDayWindowProbe]::PostMessage($windowHandle,0x112,[IntPtr]::new(0xF020),[IntPtr]::Zero) }
if ($Action -eq 'close') { [void][PersonalDayWindowProbe]::PostMessage($windowHandle, 0x10, [IntPtr]::Zero, [IntPtr]::Zero) }
if ($Action -eq 'hotkey') {
  $keys=if($AlternateHotkey){@(0x11,0x10,0x12,0x5A)}else{@(0x5B,0x12,0x58)}
  try {
    foreach ($key in $keys) { [PersonalDayWindowProbe]::keybd_event($key,0,0,[UIntPtr]::Zero) }
    Start-Sleep -Milliseconds $HoldMs
  } finally {
    [array]::Reverse($keys)
    foreach ($key in $keys) { [PersonalDayWindowProbe]::keybd_event($key,0,2,[UIntPtr]::Zero) }
  }
  Start-Sleep -Milliseconds 300
}
if ($Action -eq 'desktop') {
  [PersonalDayWindowProbe]::keybd_event(0x5B, 0, 0, [UIntPtr]::Zero)
  [PersonalDayWindowProbe]::keybd_event(0x44, 0, 0, [UIntPtr]::Zero)
  [PersonalDayWindowProbe]::keybd_event(0x44, 0, 2, [UIntPtr]::Zero)
  [PersonalDayWindowProbe]::keybd_event(0x5B, 0, 2, [UIntPtr]::Zero)
  Start-Sleep -Milliseconds 800
}
$rect = New-Object PersonalDayWindowProbe+Rect
[void][PersonalDayWindowProbe]::GetWindowRect($windowHandle, [ref]$rect)
if ($Action -eq 'focus-test-app') {
  # Only call on the independently named pattern window, never on the clock.
  [void][PersonalDayWindowProbe]::SetWindowPos($windowHandle,[IntPtr]::new(-1),0,0,0,0,0x53)
  [void][PersonalDayWindowProbe]::SetForegroundWindow($windowHandle)
  if ([PersonalDayWindowProbe]::GetForegroundWindow() -ne $windowHandle) {
    $old=New-Object PersonalDayWindowProbe+Point
    [void][PersonalDayWindowProbe]::GetCursorPos([ref]$old)
    [void][PersonalDayWindowProbe]::SetCursorPos(($rect.Left+60),($rect.Top+16))
    [PersonalDayWindowProbe]::mouse_event(2,0,0,0,[UIntPtr]::Zero)
    [PersonalDayWindowProbe]::mouse_event(4,0,0,0,[UIntPtr]::Zero)
    [void][PersonalDayWindowProbe]::SetCursorPos($old.X,$old.Y)
  }
  [void][PersonalDayWindowProbe]::SetWindowPos($windowHandle,[IntPtr]::new(-2),0,0,0,0,0x13)
}
if ($Action -eq 'drag') {
  $cursor = New-Object PersonalDayWindowProbe+Point
  [void][PersonalDayWindowProbe]::GetCursorPos([ref]$cursor)
  try {
    [void][PersonalDayWindowProbe]::SetCursorPos($rect.Left + 35, $rect.Top + 18)
    [PersonalDayWindowProbe]::mouse_event(2,0,0,0,[UIntPtr]::Zero)
    Start-Sleep -Milliseconds 150
    [void][PersonalDayWindowProbe]::SetCursorPos($rect.Left + 35 + $X, $rect.Top + 18 + $Y)
    Start-Sleep -Milliseconds 250
  } finally {
    [PersonalDayWindowProbe]::mouse_event(4,0,0,0,[UIntPtr]::Zero)
    Start-Sleep -Milliseconds 100
    [void][PersonalDayWindowProbe]::SetCursorPos($cursor.X,$cursor.Y)
  }
  [void][PersonalDayWindowProbe]::GetWindowRect($windowHandle, [ref]$rect)
}
$hitPoint = New-Object PersonalDayWindowProbe+Point
$hitPoint.X = $rect.Left + 20; $hitPoint.Y = $rect.Top + 45
$hit = [PersonalDayWindowProbe]::WindowFromPoint($hitPoint)
$client=New-Object PersonalDayWindowProbe+Rect
$origin=New-Object PersonalDayWindowProbe+Point
[void][PersonalDayWindowProbe]::GetClientRect($windowHandle,[ref]$client)
[void][PersonalDayWindowProbe]::ClientToScreen($windowHandle,[ref]$origin)
@{ minimized=[PersonalDayWindowProbe]::IsIconic($windowHandle); clientX=$origin.X;clientY=$origin.Y;clientWidth=$client.Right;clientHeight=$client.Bottom; visible = [PersonalDayWindowProbe]::IsWindowVisible($windowHandle); topmost = (([PersonalDayWindowProbe]::GetWindowLong($windowHandle, -20) -band 8) -ne 0); parent = [PersonalDayWindowProbe]::GetParent($windowHandle).ToInt64(); child = (([PersonalDayWindowProbe]::GetWindowLong($windowHandle, -16) -band 0x40000000) -ne 0); hitInside = ($hit -eq $windowHandle -or [PersonalDayWindowProbe]::IsChild($windowHandle,$hit)); x = $rect.Left; y = $rect.Top; width = $rect.Right - $rect.Left; height = $rect.Bottom - $rect.Top } | ConvertTo-Json -Compress
