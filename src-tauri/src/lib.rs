use serde::{Deserialize, Serialize};
use std::{fs, path::PathBuf, sync::Mutex, time::Duration};
use tauri::{
    menu::{Menu, MenuItem},
    tray::TrayIconBuilder,
    AppHandle, Emitter, Manager, PhysicalPosition, State, WebviewWindow,
};
use tauri_plugin_autostart::ManagerExt;
mod backdrop;
mod desktop_layer;
#[cfg(windows)]
mod hotkey;
mod live_glass;
mod placement;
mod wallpaper;

static STARTUP_READY: std::sync::atomic::AtomicBool = std::sync::atomic::AtomicBool::new(false);

#[tauri::command]
fn shortcut_status(app: AppHandle) -> serde_json::Value {
    #[cfg(windows)]
    if let Some(listener) = app.try_state::<hotkey::Listener>() {
        return listener.status();
    }
    serde_json::json!({"registered":false})
}

#[tauri::command]
fn startup_ready() -> bool {
    STARTUP_READY.load(std::sync::atomic::Ordering::Acquire)
}

fn diagnostic_dir() -> Option<PathBuf> {
    #[cfg(feature = "window-diagnostics")]
    {
        return std::env::var_os("PD_PROBE_ROOT").map(PathBuf::from);
    }
    #[cfg(not(feature = "window-diagnostics"))]
    {
        None
    }
}

#[tauri::command]
fn probe_exit(app: AppHandle) -> Result<(), String> {
    if diagnostic_dir().is_none() {
        return Err("Diagnostic build required".into());
    }
    app.exit(0);
    Ok(())
}

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
#[serde(default)]
struct Preferences {
    wake: String,
    sleep: String,
    theme: String,
    always_on_top: bool,
    widget_size: String,
    position_locked: bool,
    desktop_mode: bool,
    start_at_login: bool,
}
impl Default for Preferences {
    fn default() -> Self {
        Self {
            wake: "08:00".into(),
            sleep: "00:00".into(),
            theme: "system".into(),
            always_on_top: false,
            widget_size: "six".into(),
            position_locked: true,
            desktop_mode: cfg!(windows),
            start_at_login: false,
        }
    }
}
fn valid_time(s: &str) -> bool {
    let b = s.as_bytes();
    b.len() == 5
        && b[2] == b':'
        && [b[0], b[1], b[3], b[4]].iter().all(u8::is_ascii_digit)
        && s[..2].parse::<u8>().is_ok_and(|v| v < 24)
        && s[3..].parse::<u8>().is_ok_and(|v| v < 60)
}
fn validate(p: &Preferences) -> Result<(), String> {
    if !valid_time(&p.wake) || !valid_time(&p.sleep) || p.wake == p.sleep {
        return Err("起床和入睡必须是不同的有效时刻。".into());
    }
    if p.theme != "system" {
        return Err("主题设置无效。".into());
    }
    if p.widget_size != "four" && p.widget_size != "six" {
        return Err("组件尺寸无效。".into());
    }
    if p.desktop_mode && p.always_on_top {
        return Err("桌面模式下不能同时置顶。".into());
    }
    if p.desktop_mode && !cfg!(windows) {
        return Err("当前平台暂不支持嵌入桌面。".into());
    }
    Ok(())
}

#[derive(Clone, Debug, Default, Serialize, Deserialize)]
#[serde(default)]
struct SavedState {
    preferences: Preferences,
    position: Option<(i32, i32)>,
    anchor: Option<CornerAnchor>,
}
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
struct CornerAnchor {
    monitor: String,
    right: f64,
    bottom: f64,
}
struct LocalState {
    path: PathBuf,
    saved: Mutex<SavedState>,
    startup_warning: Mutex<Option<String>>,
}
fn write_state(path: &PathBuf, saved: &SavedState) -> Result<(), String> {
    let bytes = serde_json::to_vec_pretty(saved).map_err(|e| e.to_string())?;
    // Keep the previous valid copy so a interrupted write can recover on next launch.
    if path.exists() {
        fs::copy(path, path.with_extension("json.bak")).map_err(|e| e.to_string())?;
    }
    fs::write(path, bytes).map_err(|e| format!("无法保存本地设置：{e}"))
}
fn read_state(path: &PathBuf) -> Result<SavedState, String> {
    let bytes = fs::read(path).map_err(|e| e.to_string())?;
    let mut saved: SavedState = serde_json::from_slice(&bytes).map_err(|e| e.to_string())?;
    saved.preferences.theme = "system".into();
    if saved.anchor.as_ref().is_some_and(|a| {
        !a.right.is_finite() || !a.bottom.is_finite() || a.right < 0.0 || a.bottom < 0.0
    }) {
        saved.anchor = None;
    }
    if saved.preferences.desktop_mode {
        saved.preferences.always_on_top = false;
    }
    validate(&saved.preferences)?;
    Ok(saved)
}

#[tauri::command]
fn load_preferences(state: State<LocalState>) -> Result<Preferences, String> {
    Ok(state
        .saved
        .lock()
        .map_err(|e| e.to_string())?
        .preferences
        .clone())
}
#[tauri::command]
fn startup_warning(state: State<LocalState>) -> Result<Option<String>, String> {
    Ok(state
        .startup_warning
        .lock()
        .map_err(|e| e.to_string())?
        .take())
}
#[tauri::command]
fn save_preferences(
    app: AppHandle,
    state: State<LocalState>,
    preferences: Preferences,
) -> Result<(), String> {
    validate(&preferences)?;
    let window = app.get_webview_window("main").ok_or("组件窗口不可用。")?;
    remember_position(&window)?;
    let mut saved = state.saved.lock().map_err(|e| e.to_string())?;
    let old = saved.preferences.clone();
    if let Err(error) = desktop_layer::apply(&window, preferences.desktop_mode) {
        let _ = desktop_layer::apply(&window, old.desktop_mode);
        let _ = backdrop::apply(&window, !old.desktop_mode);
        let _ = desktop_layer::set_topmost(&window, old.always_on_top);
        return Err(error);
    }
    if let Err(error) = backdrop::apply(&window, !preferences.desktop_mode) {
        let _ = desktop_layer::apply(&window, old.desktop_mode);
        let _ = backdrop::apply(&window, !old.desktop_mode);
        let _ = desktop_layer::set_topmost(&window, old.always_on_top);
        return Err(error);
    }
    if let Err(error) = desktop_layer::set_topmost(&window, preferences.always_on_top) {
        let _ = desktop_layer::apply(&window, old.desktop_mode);
        let _ = backdrop::apply(&window, !old.desktop_mode);
        let _ = desktop_layer::set_topmost(&window, old.always_on_top);
        return Err(error);
    }
    let mut next = saved.clone();
    if preferences.start_at_login != old.start_at_login {
        if let Err(error) = set_autostart(&app, preferences.start_at_login) {
            let _ = desktop_layer::apply(&window, old.desktop_mode);
            let _ = backdrop::apply(&window, !old.desktop_mode);
            let _ = desktop_layer::set_topmost(&window, old.always_on_top);
            return Err(error);
        }
    }
    next.preferences = preferences;
    if let Err(error) = write_state(&state.path, &next) {
        let _ = desktop_layer::apply(&window, old.desktop_mode);
        let _ = backdrop::apply(&window, !old.desktop_mode);
        let _ = desktop_layer::set_topmost(&window, old.always_on_top);
        let _ = set_autostart(&app, old.start_at_login);
        return Err(error);
    }
    *saved = next;
    drop(saved);
    ensure_visible(&window)?;
    app.emit("preferences-changed", ())
        .map_err(|e| e.to_string())?;
    Ok(())
}

fn set_autostart(app: &AppHandle, enabled: bool) -> Result<(), String> {
    if diagnostic_dir().is_some() {
        return Ok(());
    }
    let manager = app.autolaunch();
    (if enabled {
        manager.enable()
    } else {
        manager.disable()
    })
    .map_err(|e| format!("无法更新开机自启：{e}"))?;
    // auto-launch 0.5 writes an unquoted Windows path. Quote it so installation
    // paths containing spaces are interpreted as one executable at login.
    #[cfg(windows)]
    if enabled {
        use winreg::{
            enums::{HKEY_CURRENT_USER, KEY_SET_VALUE},
            RegKey,
        };
        let exe = std::env::current_exe().map_err(|e| e.to_string())?;
        let command = format!("\"{}\" --autostart", exe.display());
        RegKey::predef(HKEY_CURRENT_USER)
            .open_subkey_with_flags(
                "Software\\Microsoft\\Windows\\CurrentVersion\\Run",
                KEY_SET_VALUE,
            )
            .and_then(|key| key.set_value("Personal Day", &command))
            .map_err(|e| format!("无法保存自启路径：{e}"))?;
    }
    Ok(())
}

fn anchor_for(window: &WebviewWindow, p: PhysicalPosition<i32>) -> Result<CornerAnchor, String> {
    let monitors = window.available_monitors().map_err(|e| e.to_string())?;
    let monitor = monitors
        .iter()
        .find(|m| {
            let r = m.work_area();
            p.x >= r.position.x
                && p.y >= r.position.y
                && i64::from(p.x) < i64::from(r.position.x) + i64::from(r.size.width)
                && i64::from(p.y) < i64::from(r.position.y) + i64::from(r.size.height)
        })
        .or_else(|| monitors.first())
        .ok_or("未找到可用屏幕。")?;
    let r = monitor.work_area();
    let size = window.outer_size().map_err(|e| e.to_string())?;
    Ok(CornerAnchor {
        monitor: monitor.name().cloned().unwrap_or_default(),
        right: (f64::from(r.position.x) + f64::from(r.size.width)
            - f64::from(p.x)
            - f64::from(size.width))
        .max(0.0)
            / monitor.scale_factor(),
        bottom: (f64::from(r.position.y) + f64::from(r.size.height)
            - f64::from(p.y)
            - f64::from(size.height))
        .max(0.0)
            / monitor.scale_factor(),
    })
}

// Preserve reachable user placement; recover only an inaccessible drag header.
// Locked desktop mode continues to use its saved corner anchor.
fn ensure_visible(window: &WebviewWindow) -> Result<(), String> {
    if desktop_layer::dragging() || window.is_minimized().map_err(|e| e.to_string())? {
        return Ok(());
    }
    let position = desktop_layer::position(window)?;
    let size = window.outer_size().map_err(|e| e.to_string())?;
    let monitors = window.available_monitors().map_err(|e| e.to_string())?;
    if monitors.is_empty() {
        return Ok(());
    }
    if window.label() == "main" {
        let state = window.state::<LocalState>();
        let saved = state.saved.lock().map_err(|e| e.to_string())?;
        if saved.preferences.position_locked && saved.preferences.desktop_mode {
            let anchor = saved.anchor.clone();
            drop(saved);
            let primary = window.primary_monitor().map_err(|e| e.to_string())?;
            let monitor = anchor
                .as_ref()
                .and_then(|a| monitors.iter().find(|m| m.name() == Some(&a.monitor)))
                .or(primary.as_ref())
                .unwrap_or(&monitors[0]);
            let r = monitor.work_area();
            let (right, bottom) = anchor.map(|a| (a.right, a.bottom)).unwrap_or((24.0, 24.0));
            let available_x = r.size.width.saturating_sub(size.width);
            let available_y = r.size.height.saturating_sub(size.height);
            let x = r.position.x + available_x as i32
                - (right * monitor.scale_factor())
                    .round()
                    .clamp(0.0, f64::from(available_x)) as i32;
            let y = r.position.y + available_y as i32
                - (bottom * monitor.scale_factor())
                    .round()
                    .clamp(0.0, f64::from(available_y)) as i32;
            if position != PhysicalPosition::new(x, y) {
                desktop_layer::set_position(window, PhysicalPosition::new(x, y))?;
            }
            return Ok(());
        }
    }
    let areas: Vec<_> = monitors
        .iter()
        .map(|monitor| {
            let r = monitor.work_area();
            placement::Area {
                x: r.position.x,
                y: r.position.y,
                width: r.size.width,
                height: r.size.height,
                scale: monitor.scale_factor(),
            }
        })
        .collect();
    let recovered = placement::recover((position.x, position.y), (size.width, size.height), &areas);
    if recovered != (position.x, position.y) {
        desktop_layer::set_position(window, PhysicalPosition::new(recovered.0, recovered.1))?;
    }
    Ok(())
}
fn remember_position(window: &WebviewWindow) -> Result<(), String> {
    // Windows parks iconic windows at (-32000, -32000). This is not a user
    // position and must never replace the floating position or desktop anchor.
    if window.is_minimized().map_err(|e| e.to_string())? {
        return Ok(());
    }
    let p = desktop_layer::position(window)?;
    let state = window.state::<LocalState>();
    let mut saved = state.saved.lock().map_err(|e| e.to_string())?;
    if saved.position != Some((p.x, p.y)) {
        let mut next = saved.clone();
        next.position = Some((p.x, p.y));
        if next.preferences.desktop_mode && !next.preferences.position_locked {
            next.anchor = Some(anchor_for(window, p)?);
        }
        write_state(&state.path, &next)?;
        *saved = next;
    }
    Ok(())
}
fn show_window(window: &WebviewWindow) -> Result<(), String> {
    let embedded = window
        .state::<LocalState>()
        .saved
        .lock()
        .map_err(|e| e.to_string())?
        .preferences
        .desktop_mode;
    desktop_layer::show(window, embedded)?;
    // Restore first, then measure/clamp the real window rectangle.
    ensure_visible(window)?;
    Ok(())
}
#[tauri::command]
fn resize_window(
    window: WebviewWindow,
    width: f64,
    height: f64,
    radius: f64,
) -> Result<(), String> {
    if !width.is_finite()
        || !(160.0..=960.0).contains(&width)
        || !height.is_finite()
        || !(128.0..=1650.0).contains(&height)
        || !radius.is_finite()
        || !(0.0..=32.0).contains(&radius)
    {
        return Err("窗口尺寸无效。".into());
    }
    window
        .set_size(tauri::LogicalSize::new(width, height))
        .map_err(|e| e.to_string())?;
    if window.label() == "main" {
        desktop_layer::round_corners(&window, radius)?;
    }
    ensure_visible(&window).map_err(|e| e.to_string())
}
#[tauri::command]
fn open_settings(app: AppHandle) -> Result<(), String> {
    let settings = app
        .get_webview_window("settings")
        .ok_or("设置窗口不可用。")?;
    settings.show().map_err(|e| e.to_string())?;
    settings.unminimize().map_err(|e| e.to_string())?;
    settings.set_focus().map_err(|e| e.to_string())?;
    settings
        .emit("refresh-settings", ())
        .map_err(|e| e.to_string())
}
#[tauri::command]
fn close_settings(window: WebviewWindow) -> Result<(), String> {
    window.hide().map_err(|e| e.to_string())
}
#[tauri::command]
fn start_widget_drag(window: WebviewWindow, state: State<LocalState>) -> Result<(), String> {
    if state
        .saved
        .lock()
        .map_err(|e| e.to_string())?
        .preferences
        .position_locked
    {
        return Ok(());
    }
    desktop_layer::start_drag(window)
}
#[tauri::command]
fn platform_info(window: WebviewWindow) -> Result<serde_json::Value, String> {
    Ok(
        serde_json::json!({"desktopSupported": cfg!(windows), "nativeBackdrop": cfg!(all(windows, any(feature = "experimental-backdrop", feature = "live-capture"))), "windowHandle": desktop_layer::handle(&window)?}),
    )
}
#[tauri::command]
fn hide_window(window: WebviewWindow) -> Result<(), String> {
    remember_position(&window)?;
    desktop_layer::hide(&window)
}
fn report(window: &WebviewWindow, result: Result<(), String>) {
    if let Err(error) = result {
        eprintln!("{error}");
        let _ = window.emit("desktop-error", error);
    }
}

#[cfg(windows)]
fn toggle_desktop_topmost(app: &AppHandle) -> Result<(), String> {
    let state = app.state::<LocalState>();
    let window = app.get_webview_window("main").ok_or("组件窗口不可用。")?;
    let mut current = load_preferences(state.clone())?;
    // A hidden floating clock must be brought forward, not sent behind the
    // foreground app where the user cannot tell that the shortcut worked.
    if !window.is_visible().map_err(|e| e.to_string())?
        || window.is_minimized().map_err(|e| e.to_string())?
    {
        current.desktop_mode = true;
    }
    let preferences = toggled_preferences(current);
    save_preferences(app.clone(), state, preferences)?;
    show_window(&window)
}

fn toggled_preferences(mut preferences: Preferences) -> Preferences {
    let floating = preferences.desktop_mode || !preferences.always_on_top;
    preferences.desktop_mode = !floating;
    preferences.always_on_top = floating;
    preferences.position_locked = !floating;
    preferences
}

#[tauri::command]
fn toggle_window_mode(app: AppHandle) -> Result<(), String> {
    #[cfg(windows)]
    {
        toggle_desktop_topmost(&app)
    }
    #[cfg(not(windows))]
    {
        let _ = app;
        Err("当前平台暂不支持桌面层切换。".into())
    }
}

#[tauri::command]
fn return_to_desktop(app: AppHandle) -> Result<(), String> {
    let state = app.state::<LocalState>();
    let mut preferences = load_preferences(state.clone())?;
    preferences.desktop_mode = true;
    preferences.always_on_top = false;
    preferences.position_locked = true;
    save_preferences(app.clone(), state, preferences)?;
    let window = app.get_webview_window("main").ok_or("组件窗口不可用。")?;
    show_window(&window)
}

pub fn run() {
    let context = tauri::generate_context!();
    #[cfg(feature = "window-diagnostics")]
    assert!(
        diagnostic_dir().is_some()
            && context
                .config()
                .identifier
                .starts_with("com.personalday.probe"),
        "Diagnostic build requires PD_PROBE_ROOT and a separate probe identifier"
    );
    tauri::Builder::default()
        .plugin(
            tauri_plugin_autostart::Builder::new()
                .app_name("Personal Day")
                .arg("--autostart")
                .build(),
        )
        .plugin(tauri_plugin_single_instance::init(|app, _, _| {
            if let Some(window) = app.get_webview_window("main") {
                report(&window, show_window(&window).map_err(|e| e.to_string()));
            }
        }))
        .invoke_handler(tauri::generate_handler![
            startup_ready,
            shortcut_status,
            load_preferences,
            save_preferences,
            startup_warning,
            resize_window,
            hide_window,
            open_settings,
            close_settings,
            start_widget_drag,
            platform_info,
            wallpaper::wallpaper_snapshot,
            backdrop::backdrop_brightness,
            live_glass::live_glass_status,
            backdrop::probe_control,
            probe_exit,
            toggle_window_mode,
            return_to_desktop
        ])
        .setup(|app| {
            let dir = if let Some(dir) = diagnostic_dir() {
                if !dir.is_absolute()
                    || !app.config().identifier.starts_with("com.personalday.probe")
                {
                    return Err(
                        "Diagnostic run requires absolute isolated path and probe identifier"
                            .into(),
                    );
                }
                dir
            } else {
                app.path().app_config_dir()?
            };
            fs::create_dir_all(&dir)?;
            let path = dir.join("state.json");
            let (mut saved, mut warning) = if path.exists() {
                match read_state(&path) {
                    Ok(saved) => (saved, None),
                    Err(_) => {
                        let backup = read_state(&path.with_extension("json.bak")).ok();
                        // Preserve the damaged file for inspection before future writes.
                        fs::copy(&path, dir.join("state.damaged.json"))?;
                        let message = if backup.is_some() {
                            "设置文件损坏，已恢复上次备份。"
                        } else {
                            "设置文件损坏，已使用默认作息。"
                        };
                        let recovered = backup.unwrap_or_default();
                        fs::write(&path, serde_json::to_vec_pretty(&recovered)?)?;
                        (recovered, Some(message.to_string()))
                    }
                }
            } else {
                (SavedState::default(), None)
            };
            // WebView can start sending IPC while native composition initializes.
            // Register shared state before any expensive native-window operation.
            app.manage(LocalState {
                path,
                saved: Mutex::new(saved.clone()),
                startup_warning: Mutex::new(warning.clone()),
            });
            let window = app
                .get_webview_window("main")
                .ok_or("main window missing")?;
            // Old versions could have topmost enabled. Desktop placement takes priority.
            if saved.preferences.desktop_mode {
                saved.preferences.always_on_top = false;
            }
            window.set_skip_taskbar(true)?;
            if let Some((x, y)) = saved.position {
                window.set_position(PhysicalPosition::new(x, y))?;
            }
            if let Err(error) = desktop_layer::apply(&window, saved.preferences.desktop_mode) {
                saved.preferences.desktop_mode = false;
                warning = Some(format!("{error} 暂以固定小窗显示。"));
            }
            desktop_layer::set_topmost(&window, saved.preferences.always_on_top)
                .map_err(std::io::Error::other)?;
            if let Err(error) = backdrop::apply(&window, !saved.preferences.desktop_mode) {
                // A missing capture capability must not prevent access to the clock/settings.
                saved.preferences.desktop_mode = cfg!(windows);
                saved.preferences.always_on_top = false;
                let _ = desktop_layer::apply(&window, saved.preferences.desktop_mode);
                let _ = backdrop::apply(&window, false);
                warning = Some(format!("{error} 暂以桌面组件显示。"));
            }
            desktop_layer::set_topmost(&window, saved.preferences.always_on_top)
                .map_err(std::io::Error::other)?;
            *app.state::<LocalState>().saved.lock().unwrap() = saved;
            *app.state::<LocalState>().startup_warning.lock().unwrap() = warning;
            {
                let state = app.state::<LocalState>();
                let mut stored = state
                    .saved
                    .lock()
                    .map_err(|e| std::io::Error::other(e.to_string()))?;
                if stored.anchor.is_none() && stored.position.is_some() {
                    stored.anchor = Some(
                        anchor_for(
                            &window,
                            desktop_layer::position(&window).map_err(std::io::Error::other)?,
                        )
                        .map_err(std::io::Error::other)?,
                    );
                }
                if stored.preferences.start_at_login {
                    if let Err(error) = set_autostart(app.handle(), true) {
                        *state.startup_warning.lock().unwrap() = Some(error);
                    }
                }
            }

            #[cfg(windows)]
            if diagnostic_dir().is_none()
                || matches!(
                    std::env::var("PD_PROBE_HOTKEY").as_deref(),
                    Ok("1" | "alternate")
                )
            {
                let alternate = diagnostic_dir().is_some()
                    && std::env::var("PD_PROBE_HOTKEY").as_deref() == Ok("alternate");
                match hotkey::Listener::start(app.handle().clone(), alternate) {
                    Ok(listener) => {
                        app.manage(listener);
                    }
                    Err(error) => {
                        let state = app.state::<LocalState>();
                        let mut warning = state.startup_warning.lock().unwrap();
                        let message = format!(
                            "快捷键 {} 暂不可用：{error}",
                            if alternate {
                                "Ctrl+Shift+Alt+Z"
                            } else {
                                hotkey::LABEL
                            }
                        );
                        *warning = Some(
                            warning
                                .take()
                                .map(|old| format!("{old} {message}"))
                                .unwrap_or(message),
                        );
                    }
                }
            }
            let toggle = MenuItem::with_id(app, "toggle", "显示 / 隐藏", true, None::<&str>)?;
            let settings = MenuItem::with_id(app, "settings", "设置…", true, None::<&str>)?;
            #[cfg(windows)]
            let mode = MenuItem::with_id(
                app,
                "window-mode",
                format!("置顶 / 回到桌面 ({})", hotkey::LABEL),
                true,
                None::<&str>,
            )?;
            let quit = MenuItem::with_id(app, "quit", "退出", true, None::<&str>)?;
            #[cfg(windows)]
            let menu = Menu::with_items(app, &[&toggle, &mode, &settings, &quit])?;
            #[cfg(not(windows))]
            let menu = Menu::with_items(app, &[&toggle, &settings, &quit])?;
            let icon = app.default_window_icon().ok_or("app icon missing")?.clone();
            TrayIconBuilder::with_id("personal-day")
                .icon(icon)
                .tooltip("个人时钟 / Personal Day")
                .menu(&menu)
                .show_menu_on_left_click(true)
                .on_menu_event(|app, event| {
                    if let Some(window) = app.get_webview_window("main") {
                        let result = match event.id.as_ref() {
                            "toggle" => match window.is_visible() {
                                Ok(true) => hide_window(window.clone()),
                                Ok(false) => show_window(&window).map_err(|e| e.to_string()),
                                Err(e) => Err(e.to_string()),
                            },
                            "settings" => open_settings(app.clone()),
                            #[cfg(windows)]
                            "window-mode" => toggle_desktop_topmost(app),
                            "quit" => {
                                report(&window, remember_position(&window));
                                app.exit(0);
                                Ok(())
                            }
                            _ => Ok(()),
                        };
                        report(&window, result);
                    }
                })
                .build(app)?;
            show_window(&window).map_err(std::io::Error::other)?;
            let handle = app.handle().clone();
            std::thread::spawn(move || loop {
                std::thread::sleep(Duration::from_secs(2));
                let Some(window) = handle.get_webview_window("main") else {
                    break;
                };
                // All position/state work shares the UI queue with mode changes.
                // Do not hold saved-state locks on a worker that waits for UI calls.
                let _ = handle.run_on_main_thread(move || {
                    report(&window, ensure_visible(&window).map_err(|e| e.to_string()));
                    report(&window, remember_position(&window));
                });
            });
            STARTUP_READY.store(true, std::sync::atomic::Ordering::Release);
            Ok(())
        })
        .on_window_event(|window, event| {
            if let tauri::WindowEvent::CloseRequested { api, .. } = event {
                api.prevent_close();
                if window.label() == "settings" {
                    let _ = window.hide();
                    return;
                }
                if let Some(webview) = window.app_handle().get_webview_window("main") {
                    report(&webview, hide_window(webview.clone()));
                }
            }
        })
        .build(context)
        .expect("无法启动 Personal Day")
        .run(|app, event| {
            if matches!(event, tauri::RunEvent::Exit) {
                #[cfg(windows)]
                if let Some(listener) = app.try_state::<hotkey::Listener>() {
                    listener.stop();
                }
                live_glass::stop();
            }
        });
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn validate_native_preferences() {
        assert!(validate(&Preferences::default()).is_ok());
        for value in ["24:00", "08:60", "8:00", "中:文"] {
            assert!(!valid_time(value));
        }
        let mut p = Preferences::default();
        p.sleep = p.wake.clone();
        assert!(validate(&p).is_err());
    }

    #[test]
    fn shortcut_unlocks_float_and_locks_desktop_without_changing_schedule() {
        let start = Preferences {
            desktop_mode: true,
            always_on_top: false,
            wake: "10:00".into(),
            sleep: "02:00".into(),
            ..Preferences::default()
        };
        let floating = toggled_preferences(start);
        assert!(!floating.desktop_mode && floating.always_on_top && !floating.position_locked);
        let returned = toggled_preferences(floating);
        assert!(returned.desktop_mode && !returned.always_on_top && returned.position_locked);
        assert_eq!(returned.wake, "10:00");
        assert_eq!(returned.sleep, "02:00");
        let ordinary = Preferences {
            desktop_mode: false,
            always_on_top: false,
            ..returned
        };
        assert!(toggled_preferences(ordinary).always_on_top);
    }
}
