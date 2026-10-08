//! Windows desktop integration. Shell layout is not a portable API; keep this
//! module isolated and report attachment failures instead of claiming success.
use tauri::{PhysicalPosition, WebviewWindow};

#[cfg(windows)]
mod native {
    use super::*;
    use std::{
        ptr::null_mut,
        sync::atomic::{AtomicBool, Ordering},
        time::Duration,
    };
    use windows_sys::Win32::{
        Foundation::{GetLastError, SetLastError, HWND, LPARAM, POINT, RECT},
        Graphics::Gdi::{
            ClientToScreen, CreateRoundRectRgn, DeleteObject, ScreenToClient, SetWindowRgn,
        },
        UI::{
            Input::KeyboardAndMouse::{GetAsyncKeyState, VK_LBUTTON},
            WindowsAndMessaging::*,
        },
    };
    static DRAGGING: AtomicBool = AtomicBool::new(false);
    fn hwnd(window: &WebviewWindow) -> Result<HWND, String> {
        window
            .hwnd()
            .map(|h| h.0 as HWND)
            .map_err(|e| e.to_string())
    }
    fn wide(s: &str) -> Vec<u16> {
        s.encode_utf16().chain(Some(0)).collect()
    }
    unsafe extern "system" fn find_host(h: HWND, data: LPARAM) -> i32 {
        let class = wide("SHELLDLL_DefView");
        if !FindWindowExW(h, null_mut(), class.as_ptr(), std::ptr::null()).is_null()
            && IsWindowVisible(h) != 0
        {
            *(data as *mut HWND) = h;
            return 0;
        }
        1
    }
    unsafe fn desktop_host() -> HWND {
        let mut host: HWND = null_mut();
        EnumWindows(Some(find_host), &mut host as *mut HWND as LPARAM);
        if host.is_null() {
            GetShellWindow()
        } else {
            host
        }
    }
    pub fn position(window: &WebviewWindow) -> Result<PhysicalPosition<i32>, String> {
        let mut r: RECT = unsafe { std::mem::zeroed() };
        if unsafe { GetWindowRect(hwnd(window)?, &mut r) } == 0 {
            return Err("无法读取组件位置。".into());
        }
        Ok(PhysicalPosition::new(r.left, r.top))
    }
    pub fn client_position(window: &WebviewWindow) -> Result<PhysicalPosition<i32>, String> {
        let mut point = POINT { x: 0, y: 0 };
        if unsafe { ClientToScreen(hwnd(window)?, &mut point) } == 0 {
            return Err("无法读取组件内容坐标。".into());
        }
        Ok(PhysicalPosition::new(point.x, point.y))
    }
    pub fn set_position(
        window: &WebviewWindow,
        position: PhysicalPosition<i32>,
    ) -> Result<(), String> {
        let h = hwnd(window)?;
        let mut p = POINT {
            x: position.x,
            y: position.y,
        };
        unsafe {
            if (GetWindowLongPtrW(h, GWL_STYLE) as u32 & WS_CHILD) != 0 {
                let parent = GetParent(h);
                if !parent.is_null() && ScreenToClient(parent, &mut p) == 0 {
                    return Err("无法换算桌面坐标。".into());
                }
            }
            if SetWindowPos(
                h,
                null_mut(),
                p.x,
                p.y,
                0,
                0,
                SWP_NOSIZE | SWP_NOZORDER | SWP_NOACTIVATE,
            ) == 0
            {
                return Err("无法移动桌面组件。".into());
            }
        }
        Ok(())
    }
    pub fn apply(window: &WebviewWindow, enabled: bool) -> Result<(), String> {
        let h = hwnd(window)?;
        let position = position(window)?;
        unsafe {
            let old_style = GetWindowLongPtrW(h, GWL_STYLE);
            let attached = old_style as u32 & WS_CHILD != 0;
            let target = if enabled { desktop_host() } else { null_mut() };
            if enabled && target.is_null() {
                return Err("找不到 Windows 桌面，请在资源管理器启动后重试。".into());
            }
            if enabled == attached && (!enabled || GetParent(h) == target) {
                return Ok(());
            }
            crate::live_glass::stop();
            // Remove topmost while this is still a top-level window. Once it
            // is a desktop child, HWND_NOTOPMOST may leave WS_EX_TOPMOST set.
            if enabled
                && !attached
                && SetWindowPos(
                    h,
                    HWND_NOTOPMOST,
                    0,
                    0,
                    0,
                    0,
                    SWP_NOMOVE | SWP_NOSIZE | SWP_NOACTIVATE,
                ) == 0
            {
                return Err("无法取消置顶以返回桌面。".into());
            }
            let style = if enabled {
                (old_style as u32 & !WS_POPUP) | WS_CHILD
            } else {
                (old_style as u32 & !WS_CHILD) | WS_POPUP
            };
            SetWindowLongPtrW(h, GWL_STYLE, style as isize);
            SetLastError(0);
            SetParent(h, target);
            let error = GetLastError();
            if error != 0 {
                SetWindowLongPtrW(h, GWL_STYLE, old_style);
                return Err(format!("无法切换桌面模式（Windows 错误 {error}）。"));
            }
            if SetWindowPos(
                h,
                if enabled { HWND_TOP } else { HWND_NOTOPMOST },
                0,
                0,
                0,
                0,
                SWP_NOMOVE | SWP_NOSIZE | SWP_NOACTIVATE | SWP_FRAMECHANGED,
            ) == 0
            {
                return Err("无法更新桌面组件层级。".into());
            }
        }
        set_position(window, position)
    }
    pub fn dragging() -> bool {
        DRAGGING.load(Ordering::Relaxed)
    }
    pub fn round_corners(window: &WebviewWindow, radius: f64) -> Result<(), String> {
        if unsafe { GetWindowLongPtrW(hwnd(window)?, GWL_STYLE) as u32 & WS_CHILD } == 0 {
            return Ok(());
        }
        let size = window.outer_size().map_err(|e| e.to_string())?;
        let diameter =
            (radius * 2.0 * window.scale_factor().map_err(|e| e.to_string())?).round() as i32;
        unsafe {
            let region = CreateRoundRectRgn(
                0,
                0,
                size.width as i32 + 1,
                size.height as i32 + 1,
                diameter,
                diameter,
            );
            if region.is_null() {
                return Err("无法创建圆角区域。".into());
            }
            if SetWindowRgn(hwnd(window)?, region, 1) == 0 {
                DeleteObject(region);
                return Err("无法应用组件圆角。".into());
            }
        }
        Ok(())
    }
    // Keep the main window's visibility and z-order in Win32. Tao's generic
    // flag updates reconstruct styles and can discard our external WS_CHILD.
    pub fn set_topmost(window: &WebviewWindow, enabled: bool) -> Result<(), String> {
        if unsafe {
            SetWindowPos(
                hwnd(window)?,
                if enabled {
                    HWND_TOPMOST
                } else {
                    HWND_NOTOPMOST
                },
                0,
                0,
                0,
                0,
                SWP_NOMOVE | SWP_NOSIZE | SWP_NOACTIVATE,
            )
        } == 0
        {
            return Err("无法更新置顶状态。".into());
        }
        Ok(())
    }
    pub fn show(window: &WebviewWindow, embedded: bool) -> Result<(), String> {
        apply(window, embedded)?;
        unsafe {
            ShowWindow(hwnd(window)?, SW_SHOWNOACTIVATE);
        }
        Ok(())
    }
    pub fn hide(window: &WebviewWindow) -> Result<(), String> {
        unsafe {
            ShowWindow(hwnd(window)?, SW_HIDE);
        }
        Ok(())
    }
    pub fn start_drag(window: WebviewWindow) -> Result<(), String> {
        if DRAGGING.swap(true, Ordering::SeqCst) {
            return Ok(());
        }
        let start = match position(&window) {
            Ok(p) => p,
            Err(e) => {
                DRAGGING.store(false, Ordering::SeqCst);
                return Err(e);
            }
        };
        let mut cursor = POINT { x: 0, y: 0 };
        if unsafe { GetCursorPos(&mut cursor) } == 0 {
            DRAGGING.store(false, Ordering::SeqCst);
            return Err("无法读取鼠标位置。".into());
        }
        std::thread::spawn(move || {
            while unsafe { GetAsyncKeyState(VK_LBUTTON as i32) } < 0 {
                let mut current = POINT { x: 0, y: 0 };
                if unsafe { GetCursorPos(&mut current) } == 0 {
                    break;
                }
                if unsafe { GetAsyncKeyState(VK_LBUTTON as i32) } >= 0 {
                    break;
                }
                if set_position(
                    &window,
                    PhysicalPosition::new(
                        start.x + current.x - cursor.x,
                        start.y + current.y - cursor.y,
                    ),
                )
                .is_err()
                {
                    break;
                }
                std::thread::sleep(Duration::from_millis(16));
            }
            DRAGGING.store(false, Ordering::SeqCst);
        });
        Ok(())
    }
    pub fn handle(window: &WebviewWindow) -> Result<usize, String> {
        Ok(hwnd(window)? as usize)
    }
}

#[cfg(windows)]
pub use native::*;

#[cfg(not(windows))]
pub fn apply(_: &WebviewWindow, enabled: bool) -> Result<(), String> {
    if enabled {
        Err("当前平台暂不支持嵌入桌面，请使用固定小窗。".into())
    } else {
        Ok(())
    }
}
#[cfg(not(windows))]
pub fn position(window: &WebviewWindow) -> Result<PhysicalPosition<i32>, String> {
    window.outer_position().map_err(|e| e.to_string())
}
#[cfg(not(windows))]
pub fn set_position(window: &WebviewWindow, p: PhysicalPosition<i32>) -> Result<(), String> {
    window.set_position(p).map_err(|e| e.to_string())
}
#[cfg(not(windows))]
pub fn dragging() -> bool {
    false
}
#[cfg(not(windows))]
pub fn round_corners(_: &WebviewWindow, _: f64) -> Result<(), String> {
    Ok(())
}
#[cfg(not(windows))]
pub fn start_drag(window: WebviewWindow) -> Result<(), String> {
    window.start_dragging().map_err(|e| e.to_string())
}
#[cfg(not(windows))]
pub fn handle(_: &WebviewWindow) -> Result<usize, String> {
    Ok(0)
}
#[cfg(not(windows))]
pub fn set_topmost(window: &WebviewWindow, enabled: bool) -> Result<(), String> {
    window.set_always_on_top(enabled).map_err(|e| e.to_string())
}
#[cfg(not(windows))]
pub fn show(window: &WebviewWindow, _: bool) -> Result<(), String> {
    window.unminimize().map_err(|e| e.to_string())?;
    window.show().map_err(|e| e.to_string())?;
    window.set_focus().map_err(|e| e.to_string())
}
#[cfg(not(windows))]
pub fn hide(window: &WebviewWindow) -> Result<(), String> {
    window.hide().map_err(|e| e.to_string())
}
