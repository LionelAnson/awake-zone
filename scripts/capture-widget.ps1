param([int]$WidgetProcessId, [string]$Path)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
Add-Type @'
using System;
using System.Text;
using System.Runtime.InteropServices;
public class WidgetCapture {
  public delegate bool EnumProc(IntPtr hwnd, IntPtr data);
  [StructLayout(LayoutKind.Sequential)] public struct Rect { public int Left, Top, Right, Bottom; }
  [DllImport("user32.dll")] public static extern bool SetProcessDpiAwarenessContext(IntPtr value);
  [DllImport("user32.dll")] public static extern IntPtr GetDesktopWindow();
  [DllImport("user32.dll")] public static extern bool EnumChildWindows(IntPtr parent, EnumProc callback, IntPtr data);
  [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr hwnd, out uint pid);
  [DllImport("user32.dll", CharSet=CharSet.Unicode)] public static extern int GetWindowText(IntPtr hwnd, StringBuilder text, int count);
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hwnd, out Rect rect);
  [DllImport("user32.dll")] public static extern IntPtr GetDC(IntPtr hwnd);
  [DllImport("user32.dll")] public static extern int ReleaseDC(IntPtr hwnd, IntPtr dc);
  [DllImport("gdi32.dll")] public static extern bool BitBlt(IntPtr dest,int x,int y,int w,int h,IntPtr src,int sx,int sy,uint rop);
  public static IntPtr Find(uint pid) {
    IntPtr found = IntPtr.Zero;
    EnumChildWindows(GetDesktopWindow(), (h,d) => {
      uint owner; GetWindowThreadProcessId(h,out owner);
      var text = new StringBuilder(256); GetWindowText(h,text,256);
      if (owner == pid && text.ToString().Contains("Personal Day")) { found=h; return false; }
      return true;
    }, IntPtr.Zero);
    return found;
  }
}
'@
[void][WidgetCapture]::SetProcessDpiAwarenessContext([IntPtr]::new(-4))
$handle = [WidgetCapture]::Find($WidgetProcessId)
if ($handle -eq [IntPtr]::Zero) { throw 'Widget window not found' }
$rect = New-Object WidgetCapture+Rect
[void][WidgetCapture]::GetWindowRect($handle, [ref]$rect)
$width = $rect.Right - $rect.Left
$height = $rect.Bottom - $rect.Top
if ($Path) {
  $bitmap = New-Object System.Drawing.Bitmap($width, $height)
  $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
  try {
    $screenDc = [WidgetCapture]::GetDC([IntPtr]::Zero)
    $bitmapDc = $graphics.GetHdc()
    try { [void][WidgetCapture]::BitBlt($bitmapDc,0,0,$width,$height,$screenDc,$rect.Left,$rect.Top,0x40CC0020) }
    finally { $graphics.ReleaseHdc($bitmapDc); [void][WidgetCapture]::ReleaseDC([IntPtr]::Zero,$screenDc) }
    $bitmap.Save([IO.Path]::GetFullPath($Path), [System.Drawing.Imaging.ImageFormat]::Png)
  } finally { $graphics.Dispose(); $bitmap.Dispose() }
}
@{ handle=$handle.ToInt64(); x=$rect.Left; y=$rect.Top; width=$width; height=$height } | ConvertTo-Json -Compress
