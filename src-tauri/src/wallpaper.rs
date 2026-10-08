use serde::Serialize;
use tauri::WebviewWindow;

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Snapshot {
    image_key: String,
    image_data: Option<String>,
    mode: i32,
    color: String,
    left: i32,
    top: i32,
    width: i32,
    height: i32,
    client_x: i32,
    client_y: i32,
}

#[tauri::command]
pub async fn wallpaper_snapshot(
    window: WebviewWindow,
    known_key: Option<String>,
) -> Result<Option<Snapshot>, String> {
    tauri::async_runtime::spawn_blocking(move || read(&window, known_key))
        .await
        .map_err(|e| e.to_string())?
}

#[cfg(not(windows))]
fn read(_: &WebviewWindow, _: Option<String>) -> Result<Option<Snapshot>, String> {
    Ok(None)
}

#[cfg(windows)]
fn read(window: &WebviewWindow, known_key: Option<String>) -> Result<Option<Snapshot>, String> {
    read_primary(window, known_key.clone()).or_else(|primary| {
        read_fallback(window, known_key)
            .map_err(|fallback| format!("{primary}；备用读取：{fallback}"))
    })
}

#[cfg(windows)]
fn read_primary(
    window: &WebviewWindow,
    known_key: Option<String>,
) -> Result<Option<Snapshot>, String> {
    use windows::{
        core::{PCWSTR, PWSTR},
        Win32::{
            Foundation::RECT,
            System::Com::{
                CoCreateInstance, CoInitializeEx, CoTaskMemFree, CoUninitialize, CLSCTX_ALL,
                COINIT_APARTMENTTHREADED,
            },
            UI::Shell::{DesktopWallpaper, IDesktopWallpaper},
        },
    };
    struct ComGuard(bool);
    impl Drop for ComGuard {
        fn drop(&mut self) {
            if self.0 {
                unsafe {
                    CoUninitialize();
                }
            }
        }
    }
    unsafe fn take_string(p: PWSTR) -> Result<String, String> {
        if p.is_null() {
            return Ok(String::new());
        }
        let text = p.to_string().map_err(|e| e.to_string());
        CoTaskMemFree(Some(p.0.cast()));
        text
    }
    let origin = crate::desktop_layer::client_position(window)?;
    unsafe {
        let initialized = CoInitializeEx(None, COINIT_APARTMENTTHREADED);
        // RPC_E_CHANGED_MODE means the calling thread already has a COM apartment.
        if initialized.is_err() && initialized.0 != 0x80010106_u32 as i32 {
            return Err(initialized.to_string());
        }
        let _guard = ComGuard(initialized.is_ok());
        let desktop: IDesktopWallpaper = CoCreateInstance(&DesktopWallpaper, None, CLSCTX_ALL)
            .map_err(|e| format!("创建壁纸接口失败：{e}"))?;
        let count = desktop
            .GetMonitorDevicePathCount()
            .map_err(|e| format!("枚举壁纸屏幕失败：{e}"))?;
        let mut displays = Vec::new();
        for i in 0..count {
            // The shell retains entries for disconnected displays. GetMonitorRECT
            // returns E_FAIL for those entries; one stale display must not abort
            // reading the wallpaper for an attached display.
            let Ok(id_ptr) = desktop.GetMonitorDevicePathAt(i) else {
                continue;
            };
            let id = take_string(id_ptr)?;
            let wide: Vec<u16> = id.encode_utf16().chain(Some(0)).collect();
            let Ok(rect) = desktop.GetMonitorRECT(PCWSTR(wide.as_ptr())) else {
                continue;
            };
            if rect.right <= rect.left || rect.bottom <= rect.top {
                continue;
            }
            displays.push((wide, rect));
        }
        let (id, rect) = displays
            .iter()
            .find(|(_, r)| {
                origin.x >= r.left && origin.x < r.right && origin.y >= r.top && origin.y < r.bottom
            })
            .ok_or("未找到组件所在的有效壁纸屏幕。")?;
        let path = take_string(
            desktop
                .GetWallpaper(PCWSTR(id.as_ptr()))
                .map_err(|e| format!("读取当前屏幕壁纸失败：{e}"))?,
        )?;
        let mode = desktop
            .GetPosition()
            .map_err(|e| format!("读取壁纸布局失败：{e}"))?
            .0;
        let bg = desktop
            .GetBackgroundColor()
            .map_err(|e| format!("读取桌面底色失败：{e}"))?
            .0;
        let canvas = if mode == 5 {
            displays.iter().fold(*rect, |r, (_, m)| RECT {
                left: r.left.min(m.left),
                top: r.top.min(m.top),
                right: r.right.max(m.right),
                bottom: r.bottom.max(m.bottom),
            })
        } else {
            *rect
        };
        let (image_key, image_data) = image_payload(&path, known_key.as_deref())?;
        Ok(Some(Snapshot {
            image_key,
            image_data,
            mode,
            color: format!("rgb({} {} {})", bg & 255, (bg >> 8) & 255, (bg >> 16) & 255),
            left: canvas.left,
            top: canvas.top,
            width: canvas.right - canvas.left,
            height: canvas.bottom - canvas.top,
            client_x: origin.x,
            client_y: origin.y,
        }))
    }
}

#[cfg(windows)]
fn read_fallback(
    window: &WebviewWindow,
    known_key: Option<String>,
) -> Result<Option<Snapshot>, String> {
    use windows_sys::Win32::{
        Graphics::Gdi::{GetSysColor, COLOR_DESKTOP},
        UI::WindowsAndMessaging::{SystemParametersInfoW, SPI_GETDESKWALLPAPER},
    };
    use winreg::{enums::HKEY_CURRENT_USER, RegKey};
    let monitors = window.available_monitors().map_err(|e| e.to_string())?;
    // SPI reports the common/primary wallpaper, not a per-monitor image. Only
    // use it with one attached monitor instead of sampling the wrong image.
    if monitors.len() != 1 {
        return Err("多屏环境等待系统壁纸接口恢复。".into());
    }
    let monitor = &monitors[0];
    let mut buffer = vec![0u16; 32768];
    if unsafe {
        SystemParametersInfoW(
            SPI_GETDESKWALLPAPER,
            buffer.len() as u32,
            buffer.as_mut_ptr().cast(),
            0,
        )
    } == 0
    {
        return Err(format!(
            "读取系统壁纸路径失败：{}",
            std::io::Error::last_os_error()
        ));
    }
    let end = buffer.iter().position(|&c| c == 0).unwrap_or(buffer.len());
    let path = String::from_utf16_lossy(&buffer[..end]);
    let desktop = RegKey::predef(HKEY_CURRENT_USER)
        .open_subkey("Control Panel\\Desktop")
        .map_err(|e| e.to_string())?;
    let style: String = desktop
        .get_value("WallpaperStyle")
        .map_err(|e| e.to_string())?;
    let tiled: String = desktop
        .get_value("TileWallpaper")
        .unwrap_or_else(|_| "0".into());
    let mode = if tiled == "1" {
        1
    } else {
        match style.as_str() {
            "0" => 0,
            "2" => 2,
            "6" => 3,
            "10" => 4,
            "22" => 5,
            _ => return Err("未知的系统壁纸布局。".into()),
        }
    };
    let color = unsafe { GetSysColor(COLOR_DESKTOP) };
    let origin = crate::desktop_layer::client_position(window)?;
    let (image_key, image_data) = image_payload(&path, known_key.as_deref())?;
    Ok(Some(Snapshot {
        image_key,
        image_data,
        mode,
        color: format!(
            "rgb({} {} {})",
            color & 255,
            (color >> 8) & 255,
            (color >> 16) & 255
        ),
        left: monitor.position().x,
        top: monitor.position().y,
        width: monitor.size().width as i32,
        height: monitor.size().height as i32,
        client_x: origin.x,
        client_y: origin.y,
    }))
}

#[cfg(windows)]
fn image_payload(path: &str, known_key: Option<&str>) -> Result<(String, Option<String>), String> {
    use base64::Engine;
    use std::{
        collections::hash_map::DefaultHasher,
        hash::{Hash, Hasher},
        time::UNIX_EPOCH,
    };
    let mut hasher = DefaultHasher::new();
    path.hash(&mut hasher);
    if !path.is_empty() {
        let meta = std::fs::metadata(&path).map_err(|e| format!("无法读取当前壁纸：{e}"))?;
        if meta.len() > 64 * 1024 * 1024 {
            return Err("当前壁纸超过 64 MB，暂不能取样。".into());
        }
        meta.len().hash(&mut hasher);
        meta.modified()
            .ok()
            .and_then(|t| t.duration_since(UNIX_EPOCH).ok())
            .hash(&mut hasher);
    }
    let image_key = format!("{:x}", hasher.finish());
    let image_data = if known_key == Some(image_key.as_str()) {
        None
    } else if path.is_empty() {
        Some(String::new())
    } else {
        let bytes = std::fs::read(&path).map_err(|e| e.to_string())?;
        let mime = if bytes.starts_with(b"\x89PNG") {
            "image/png"
        } else if bytes.starts_with(b"BM") {
            "image/bmp"
        } else if bytes.starts_with(b"GIF8") {
            "image/gif"
        } else {
            "image/jpeg"
        };
        Some(format!(
            "data:{mime};base64,{}",
            base64::engine::general_purpose::STANDARD.encode(bytes)
        ))
    };
    Ok((image_key, image_data))
}
