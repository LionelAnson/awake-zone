use tauri::WebviewWindow;

#[cfg(all(windows, feature = "live-capture"))]
unsafe extern "C" {
    fn pd_glass_start(window: *mut std::ffi::c_void) -> i32;
    fn pd_glass_stop();
    fn pd_glass_brightness() -> f64;
    fn pd_glass_error() -> i32;
    fn pd_glass_frames() -> u64;
    fn pd_glass_border_access() -> i32;
    fn pd_glass_border_required() -> i32;
    fn pd_glass_border_error() -> i32;
}

pub fn stop() {
    #[cfg(all(windows, feature = "live-capture"))]
    unsafe {
        pd_glass_stop();
    }
}

#[cfg(all(windows, feature = "live-capture"))]
pub fn apply(window: &WebviewWindow, enabled: bool) -> Result<(), String> {
    if !enabled {
        stop();
        return Ok(());
    }
    // Clear both the Win32 erase background and the WebView default fill.
    window
        .as_ref()
        .window()
        .set_background_color(None)
        .map_err(|e| e.to_string())?;
    window
        .as_ref()
        .set_background_color(Some(tauri::window::Color(0, 0, 0, 0)))
        .map_err(|e| e.to_string())?;
    let result = unsafe { pd_glass_start(window.hwnd().map_err(|e| e.to_string())?.0) };
    if result < 0 {
        return Err(format!("实时磨砂启动失败：0x{:08X}", result as u32));
    }
    Ok(())
}

#[tauri::command]
pub fn live_glass_status() -> serde_json::Value {
    #[cfg(all(windows, feature = "live-capture"))]
    unsafe {
        return serde_json::json!({"error":pd_glass_error(), "brightness":pd_glass_brightness(), "frames":pd_glass_frames(), "borderlessAccess":pd_glass_border_access(), "borderRequired":pd_glass_border_required(), "borderError":pd_glass_border_error()});
    }
    #[cfg(not(all(windows, feature = "live-capture")))]
    serde_json::json!({"error":0,"brightness":-1,"frames":0})
}

#[cfg(all(windows, feature = "live-capture"))]
pub fn brightness() -> Result<Option<f64>, String> {
    unsafe {
        let hr = pd_glass_error();
        if hr < 0 {
            return Err(format!(
                "实时磨砂已停止：0x{:08X}。请返回桌面后重试。",
                hr as u32
            ));
        }
        let value = pd_glass_brightness();
        Ok((value >= 0.0).then_some(value))
    }
}
