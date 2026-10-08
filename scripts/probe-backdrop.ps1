param([long]$Handle, [ValidateSet('acrylic','blur','dwm','reset','exclude','transparent','active','noredirect')][string]$Effect)
Add-Type @'
using System;
using System.Runtime.InteropServices;
public class BackdropProbe {
  [DllImport("user32.dll")] public static extern bool SetWindowDisplayAffinity(IntPtr h,uint affinity);
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
  [StructLayout(LayoutKind.Sequential)] public struct Margins { public int Left,Right,Top,Bottom; }
  [DllImport("dwmapi.dll")] public static extern int DwmExtendFrameIntoClientArea(IntPtr hwnd, ref Margins m);
  [StructLayout(LayoutKind.Sequential)] public struct Blur { public uint Flags; public int Enable; public IntPtr Region; public int Transition; }
  [DllImport("dwmapi.dll")] public static extern int DwmEnableBlurBehindWindow(IntPtr hwnd, ref Blur b);
  [DllImport("gdi32.dll")] public static extern IntPtr CreateRectRgn(int a,int b,int c,int d);
  [DllImport("gdi32.dll")] public static extern bool DeleteObject(IntPtr h);
  [DllImport("user32.dll")] public static extern int GetWindowLong(IntPtr h,int n);
  [DllImport("user32.dll")] public static extern int SetWindowLong(IntPtr h,int n,int v);
  [DllImport("user32.dll")] public static extern bool SetWindowPos(IntPtr h,IntPtr after,int x,int y,int w,int z,uint f);
  [DllImport("user32.dll")] public static extern int SetWindowRgn(IntPtr h,IntPtr region,bool redraw);
  public static int Reset(IntPtr h) {
    Console.WriteLine("Before style {0:X8}",GetWindowLong(h,-16));
    SetWindowRgn(h,IntPtr.Zero,true);
    SetWindowLong(h,-16,GetWindowLong(h,-16) & ~0x00CF0000);
    SetWindowPos(h,IntPtr.Zero,0,0,0,0,0x37);
    Console.WriteLine("After style {0:X8}",GetWindowLong(h,-16));
    var margins=new Margins{Left=-1,Right=-1,Top=-1,Bottom=-1};DwmExtendFrameIntoClientArea(h,ref margins);
    int policy=2;DwmSetWindowAttribute(h,2,ref policy,4);
    var region=CreateRectRgn(0,0,-1,-1);
    try { var blur=new Blur {Flags=3,Enable=1,Region=region};return DwmEnableBlurBehindWindow(h,ref blur); }
    finally {DeleteObject(region);}
  }
  public static void NoRedirect(IntPtr h) {
    SetWindowLong(h,-20,GetWindowLong(h,-20) | 0x00200000);
    SetWindowLong(h,-16,GetWindowLong(h,-16) & ~0x00CF0000);
    SetWindowPos(h,IntPtr.Zero,0,0,0,0,0x37);
  }
  [StructLayout(LayoutKind.Sequential)] public struct Accent { public int State, Flags; public uint Color; public int Animation; }
  [StructLayout(LayoutKind.Sequential)] public struct Data { public int Attribute; public IntPtr DataPointer; public UIntPtr Size; }
  [DllImport("user32.dll")] public static extern int SetWindowCompositionAttribute(IntPtr hwnd, ref Data data);
  [DllImport("dwmapi.dll")] public static extern int DwmSetWindowAttribute(IntPtr hwnd, int attr, ref int value, int size);
  public static int Set(IntPtr hwnd, int state) {
    var accent = new Accent {State=state, Flags=2, Color=0x18000000};
    if(state==2) { var margins=new Margins();DwmExtendFrameIntoClientArea(hwnd,ref margins);accent.Color=0; }
    var p=Marshal.AllocHGlobal(Marshal.SizeOf(accent));
    try {
      Marshal.StructureToPtr(accent,p,false);
      var data=new Data {Attribute=19,DataPointer=p,Size=(UIntPtr)Marshal.SizeOf(accent)};
      return SetWindowCompositionAttribute(hwnd,ref data);
    } finally {Marshal.FreeHGlobal(p);}
  }
  public static int Active(IntPtr hwnd) {
    var p=Marshal.AllocHGlobal(4);
    try { Marshal.WriteInt32(p,1); var data=new Data {Attribute=15,DataPointer=p,Size=(UIntPtr)4};return SetWindowCompositionAttribute(hwnd,ref data); }
    finally {Marshal.FreeHGlobal(p);SetForegroundWindow(hwnd);}
  }
}
'@
if ($Effect -eq 'noredirect') { [BackdropProbe]::NoRedirect([IntPtr]::new($Handle)) }
elseif ($Effect -eq 'active') { [BackdropProbe]::Active([IntPtr]::new($Handle)) }
elseif ($Effect -eq 'transparent') { [BackdropProbe]::Set([IntPtr]::new($Handle),2) }
elseif ($Effect -eq 'exclude') { [BackdropProbe]::SetWindowDisplayAffinity([IntPtr]::new($Handle),0x11) }
elseif ($Effect -eq 'reset') { [BackdropProbe]::Reset([IntPtr]::new($Handle)) }
elseif ($Effect -eq 'dwm') { [void][BackdropProbe]::Set([IntPtr]::new($Handle),0); [void][BackdropProbe]::Reset([IntPtr]::new($Handle)); $value=3; [BackdropProbe]::DwmSetWindowAttribute([IntPtr]::new($Handle),38,[ref]$value,4) }
else { [BackdropProbe]::Set([IntPtr]::new($Handle), $(if($Effect -eq 'acrylic'){4}else{3})) }
