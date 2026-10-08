use tauri::WebviewWindow;

#[cfg(all(
    windows,
    feature = "experimental-backdrop",
    not(feature = "live-capture")
))]
mod native {
    use super::*;
    use std::{cell::RefCell, ffi::c_void};
    use windows::{
        core::Interface,
        System::{DispatcherQueue, DispatcherQueueController},
        Win32::System::WinRT::{
            Composition::ICompositorDesktopInterop, CreateDispatcherQueueController,
            DispatcherQueueOptions, DQTAT_COM_NONE, DQTYPE_THREAD_CURRENT,
        },
        UI::Composition::{Compositor, Desktop::DesktopWindowTarget, SpriteVisual},
    };
    use windows_sys::Win32::{
        Foundation::HWND,
        Graphics::{
            Dwm::{
                DwmSetWindowAttribute, DWMWA_USE_HOSTBACKDROPBRUSH, DWMWA_WINDOW_CORNER_PREFERENCE,
            },
            Gdi::{GetDC, GetPixel, ReleaseDC, SetWindowRgn},
        },
        System::LibraryLoader::{GetModuleHandleW, GetProcAddress},
    };
    struct LiveBackdrop {
        _queue: Option<DispatcherQueueController>,
        _compositor: Compositor,
        _target: DesktopWindowTarget,
        visual: SpriteVisual,
    }
    thread_local! { static LIVE: RefCell<Option<LiveBackdrop>> = const { RefCell::new(None) }; }
    #[cfg(feature = "backdrop-diagnostics")]
    thread_local! { static LAST_DWM: std::cell::Cell<(i32,i32)> = const { std::cell::Cell::new((0,0)) }; }
    #[repr(C)]
    struct Accent {
        state: i32,
        flags: i32,
        color: u32,
        animation: i32,
    }
    #[repr(C)]
    struct Attribute {
        attribute: i32,
        data: *mut c_void,
        size: usize,
    }
    type SetComposition = unsafe extern "system" fn(HWND, *const Attribute) -> i32;

    fn create(h: HWND) -> windows::core::Result<LiveBackdrop> {
        let queue = if DispatcherQueue::GetForCurrentThread().is_err() {
            Some(unsafe {
                CreateDispatcherQueueController(DispatcherQueueOptions {
                    dwSize: std::mem::size_of::<DispatcherQueueOptions>() as u32,
                    threadType: DQTYPE_THREAD_CURRENT,
                    apartmentType: DQTAT_COM_NONE,
                })?
            })
        } else {
            None
        };
        let compositor = Compositor::new()?;
        let interop: ICompositorDesktopInterop = compositor.cast()?;
        let target = unsafe {
            interop.CreateDesktopWindowTarget(windows::Win32::Foundation::HWND(h), false)?
        };
        let visual = compositor.CreateSpriteVisual()?;
        visual.SetRelativeSizeAdjustment(windows_numerics::Vector2 { X: 1.0, Y: 1.0 })?;
        visual.SetBrush(&compositor.CreateHostBackdropBrush()?)?;
        target.SetRoot(&visual)?;
        Ok(LiveBackdrop {
            _queue: queue,
            _compositor: compositor,
            _target: target,
            visual,
        })
    }

    fn apply_on_main(window: &WebviewWindow, enabled: bool) -> Result<(), String> {
        let h = window.hwnd().map_err(|e| e.to_string())?.0 as HWND;
        if enabled {
            window.set_decorations(false).map_err(|e| e.to_string())?;
            window.set_shadow(false).map_err(|e| e.to_string())?;
            let legacy = cfg!(feature = "backdrop-diagnostics")
                && std::env::var("PD_PROBE_HOST").as_deref() == Ok("legacy");
            if legacy {
                window
                    .set_background_color(Some(tauri::window::Color(0, 0, 0, 0)))
                    .map_err(|e| e.to_string())?;
            } else {
                // Window's Win32 erase handler ignores alpha. None removes that
                // explicit RGB fill; WebView None would instead restore white.
                window
                    .as_ref()
                    .window()
                    .set_background_color(None)
                    .map_err(|e| e.to_string())?;
                window
                    .as_ref()
                    .set_background_color(Some(tauri::window::Color(0, 0, 0, 0)))
                    .map_err(|e| e.to_string())?;
            }
        }
        unsafe {
            let module_name: Vec<u16> = "user32.dll\0".encode_utf16().collect();
            let module = GetModuleHandleW(module_name.as_ptr());
            let entry = GetProcAddress(module, c"SetWindowCompositionAttribute".as_ptr().cast())
                .ok_or("Windows composition entry point unavailable")?;
            let set_composition: SetComposition = std::mem::transmute(entry);
            let mut accent = Accent {
                state: if enabled { 5 } else { 0 },
                flags: 0,
                color: 0,
                animation: 0,
            };
            let data = Attribute {
                attribute: 19,
                data: (&mut accent as *mut Accent).cast(),
                size: std::mem::size_of::<Accent>(),
            };
            if set_composition(h, &data) == 0 && enabled {
                return Err("Windows host backdrop unavailable".into());
            }
            let allow: i32 = i32::from(enabled);
            let hr = DwmSetWindowAttribute(
                h,
                DWMWA_USE_HOSTBACKDROPBRUSH as u32,
                (&allow as *const i32).cast(),
                std::mem::size_of::<i32>() as u32,
            );
            #[cfg(feature = "backdrop-diagnostics")]
            LAST_DWM.with(|s| s.set((hr, s.get().1)));
            if enabled && hr < 0 {
                return Err(format!("Host backdrop enable failed: 0x{:08X}", hr as u32));
            }
            if enabled {
                SetWindowRgn(h, std::ptr::null_mut(), 1);
                let corner: u32 = 3;
                let hr = DwmSetWindowAttribute(
                    h,
                    DWMWA_WINDOW_CORNER_PREFERENCE as u32,
                    (&corner as *const u32).cast(),
                    4,
                );
                #[cfg(feature = "backdrop-diagnostics")]
                LAST_DWM.with(|s| s.set((s.get().0, hr)));
                if hr < 0 {
                    return Err(format!("Corner preference failed: 0x{:08X}", hr as u32));
                }
            }
        }
        LIVE.with(|slot| {
            let mut state = slot.borrow_mut();
            if enabled && state.is_none() {
                *state = Some(create(h).map_err(|e| format!("Cannot create live backdrop: {e}"))?);
            }
            if let Some(state) = state.as_ref() {
                state
                    .visual
                    .SetIsVisible(enabled)
                    .map_err(|e| e.to_string())?;
            }
            Ok(())
        })
    }
    pub fn apply(window: &WebviewWindow, enabled: bool) -> Result<(), String> {
        let window_clone = window.clone();
        let (tx, rx) = std::sync::mpsc::sync_channel(1);
        window
            .run_on_main_thread(move || {
                let _ = tx.send(apply_on_main(&window_clone, enabled));
            })
            .map_err(|e| e.to_string())?;
        rx.recv().map_err(|e| e.to_string())?
    }

    pub fn brightness(window: &WebviewWindow) -> Result<Option<f64>, String> {
        if !window.is_visible().map_err(|e| e.to_string())? {
            return Ok(None);
        }
        let origin = crate::desktop_layer::client_position(window)?;
        let size = window.inner_size().map_err(|e| e.to_string())?;
        let inset = (window.scale_factor().map_err(|e| e.to_string())? * 4.0).round() as i32;
        let (w, h) = (size.width as i32, size.height as i32);
        if w <= inset * 2 || h <= inset * 2 {
            return Ok(None);
        }
        // Read only a few composited margin pixels, not application text or images.
        let dc = unsafe { GetDC(std::ptr::null_mut()) };
        if dc.is_null() {
            return Err("无法读取窗口背景亮度。".into());
        }
        let mut total = 0.0;
        let mut count = 0;
        let linear = |v: u32| {
            let s = f64::from(v) / 255.0;
            if s <= 0.04045 {
                s / 12.92
            } else {
                ((s + 0.055) / 1.055).powf(2.4)
            }
        };
        for step in 1..=6 {
            for (x, y) in [
                (inset, h * step / 7),
                (w - inset, h * step / 7),
                (w * step / 7, inset),
                (w * step / 7, h - inset),
            ] {
                let color = unsafe { GetPixel(dc, origin.x + x, origin.y + y) };
                if color != u32::MAX {
                    total += 0.2126 * linear(color & 255)
                        + 0.7152 * linear((color >> 8) & 255)
                        + 0.0722 * linear((color >> 16) & 255);
                    count += 1;
                }
            }
        }
        unsafe {
            ReleaseDC(std::ptr::null_mut(), dc);
        }
        Ok(if count == 0 {
            None
        } else {
            Some(total / f64::from(count))
        })
    }

    #[cfg(feature = "backdrop-diagnostics")]
    pub fn probe(window: &WebviewWindow, action: &str) -> Result<serde_json::Value, String> {
        use windows_sys::Win32::UI::WindowsAndMessaging::*;
        let h = window.hwnd().map_err(|e| e.to_string())?.0 as HWND;
        match action {
            "marker" | "host" => LIVE.with(|slot| -> Result<(), String> {
                let live = slot.borrow();
                let s = live.as_ref().ok_or("No live backdrop")?;
                if action == "marker" {
                    let brush = s
                        ._compositor
                        .CreateColorBrushWithColor(windows::UI::Color {
                            A: 255,
                            R: 255,
                            G: 0,
                            B: 255,
                        })
                        .map_err(|e| e.to_string())?;
                    s.visual.SetBrush(&brush).map_err(|e| e.to_string())?;
                } else {
                    s.visual
                        .SetBrush(
                            &s._compositor
                                .CreateHostBackdropBrush()
                                .map_err(|e| e.to_string())?,
                        )
                        .map_err(|e| e.to_string())?;
                }
                Ok(())
            })?,
            "webview-hide" => window.as_ref().hide().map_err(|e| e.to_string())?,
            "webview-show" => window.as_ref().show().map_err(|e| e.to_string())?,
            "inspect" => {}
            _ => return Err("Unknown probe action".into()),
        }
        unsafe {
            let mut client = std::mem::zeroed();
            GetClientRect(h, &mut client);
            let mut process = 0;
            let thread = GetWindowThreadProcessId(h, &mut process);
            // USE_HOSTBACKDROPBRUSH is set-only; querying it returns E_INVALIDARG.
            let set_results = LAST_DWM.with(|s| s.get());
            Ok(
                serde_json::json!({"hwnd":h as usize,"parent":GetParent(h) as usize,"owner":GetWindow(h,GW_OWNER) as usize,
                "style":format!("{:08X}",GetWindowLongPtrW(h,GWL_STYLE)),"exstyle":format!("{:08X}",GetWindowLongPtrW(h,GWL_EXSTYLE)),
                "thread":thread,"pid":process,"client":[client.left,client.top,client.right,client.bottom],
                "foreground":GetForegroundWindow() as usize,"hostBackdropSetHr":set_results.0,"cornerSetHr":set_results.1,
                "scale":window.scale_factor().map_err(|e|e.to_string())?}),
            )
        }
    }
}

#[cfg(all(
    windows,
    feature = "experimental-backdrop",
    not(feature = "live-capture")
))]
pub use native::apply;
#[cfg(not(all(
    windows,
    any(feature = "experimental-backdrop", feature = "live-capture")
)))]
pub fn apply(_: &WebviewWindow, _: bool) -> Result<(), String> {
    Ok(())
}

#[cfg(all(windows, feature = "live-capture"))]
pub use crate::live_glass::apply;

#[tauri::command]
pub async fn backdrop_brightness(window: WebviewWindow) -> Result<Option<f64>, String> {
    #[cfg(all(
        windows,
        feature = "experimental-backdrop",
        not(feature = "live-capture")
    ))]
    {
        tauri::async_runtime::spawn_blocking(move || native::brightness(&window))
            .await
            .map_err(|e| e.to_string())?
    }
    #[cfg(all(windows, feature = "live-capture"))]
    {
        let _ = window;
        crate::live_glass::brightness()
    }
    #[cfg(not(all(
        windows,
        any(feature = "experimental-backdrop", feature = "live-capture")
    )))]
    {
        let _ = window;
        Ok(None)
    }
}

#[tauri::command]
pub async fn probe_control(
    window: WebviewWindow,
    action: String,
) -> Result<serde_json::Value, String> {
    #[cfg(all(
        windows,
        feature = "backdrop-diagnostics",
        not(feature = "live-capture")
    ))]
    {
        if crate::diagnostic_dir().is_none() {
            return Err("Isolated diagnostics required".into());
        }
        let (tx, rx) = std::sync::mpsc::sync_channel(1);
        let copy = window.clone();
        window
            .run_on_main_thread(move || {
                let _ = tx.send(native::probe(&copy, &action));
            })
            .map_err(|e| e.to_string())?;
        rx.recv().map_err(|e| e.to_string())?
    }
    #[cfg(not(all(
        windows,
        feature = "backdrop-diagnostics",
        not(feature = "live-capture")
    )))]
    {
        let _ = (window, action);
        Err("Diagnostic build required".into())
    }
}
