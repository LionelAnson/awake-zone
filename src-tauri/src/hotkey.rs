//! Keep WM_HOTKEY off the window thread: rebuilding capture must not delay
//! reception of the next press. Window mutations still run serially on the UI.
use serde::Serialize;
use std::{
    collections::VecDeque,
    ptr::null_mut,
    sync::{
        atomic::{AtomicBool, Ordering},
        mpsc, Arc, Mutex,
    },
    thread::JoinHandle,
    time::{Instant, SystemTime, UNIX_EPOCH},
};
use tauri::{AppHandle, Manager};
use windows_sys::Win32::{
    Foundation::GetLastError,
    UI::{
        Input::KeyboardAndMouse::{
            RegisterHotKey, UnregisterHotKey, MOD_ALT, MOD_CONTROL, MOD_NOREPEAT, MOD_SHIFT,
            MOD_WIN,
        },
        WindowsAndMessaging::{
            GetForegroundWindow, MsgWaitForMultipleObjectsEx, PeekMessageW, MSG,
            MWMO_INPUTAVAILABLE, PM_REMOVE, QS_ALLINPUT, WM_HOTKEY,
        },
    },
};

pub const LABEL: &str = "Win+Alt+X";

#[derive(Default, Serialize)]
pub struct Status {
    registered: bool,
    received: u64,
    completed: u64,
    failed: u64,
    recent: VecDeque<serde_json::Value>,
}

pub struct Listener {
    stop: Arc<AtomicBool>,
    worker: Mutex<Option<JoinHandle<()>>>,
    status: Arc<Mutex<Status>>,
}

impl Listener {
    pub fn start(app: AppHandle, alternate: bool) -> Result<Self, String> {
        let stop = Arc::new(AtomicBool::new(false));
        let status = Arc::new(Mutex::new(Status::default()));
        let stopping = stop.clone();
        let stats = status.clone();
        let (ready_tx, ready_rx) = mpsc::sync_channel(1);
        let worker = std::thread::Builder::new().name("personal-day-hotkey".into()).spawn(move || {
            let id = 1;
            let (modifiers, key) = if alternate {
                (MOD_CONTROL | MOD_ALT | MOD_SHIFT | MOD_NOREPEAT, b'Z')
            } else {
                (MOD_WIN | MOD_ALT | MOD_NOREPEAT, b'X')
            };
            // HWND=NULL associates the registration with this thread's queue.
            if unsafe { RegisterHotKey(null_mut(), id, modifiers, key as u32) } == 0 {
                let code = unsafe { GetLastError() };
                let reason = if code == 1409 { "该组合已被其他程序占用" } else { "Windows 未能注册该组合" };
                let _ = ready_tx.send(Err(format!("{reason}（Windows 错误 {code}）。")));
                return;
            }
            stats.lock().unwrap().registered = true;
            let _ = ready_tx.send(Ok(()));
            while !stopping.load(Ordering::Acquire) {
                let mut msg: MSG = unsafe { std::mem::zeroed() };
                while unsafe { PeekMessageW(&mut msg, null_mut(), 0, 0, PM_REMOVE) } != 0 {
                    if msg.message != WM_HOTKEY || msg.wParam != id as usize { continue; }
                    let started = Instant::now();
                    let timestamp = SystemTime::now().duration_since(UNIX_EPOCH).unwrap_or_default().as_millis();
                    let foreground = unsafe { GetForegroundWindow() } as usize;
                    let sequence = { let mut s = stats.lock().unwrap(); s.received += 1; s.received };
                    let report = stats.clone();
                    let handle = app.clone();
                    let queued = app.run_on_main_thread(move || {
                        let waited = started.elapsed().as_millis();
                        let result = super::toggle_desktop_topmost(&handle);
                        let error = result.as_ref().err().cloned();
                        if let Some(window) = handle.get_webview_window("main") { super::report(&window, result); }
                        let mut s = report.lock().unwrap();
                        s.completed += 1;
                        if error.is_some() { s.failed += 1; }
                        if s.recent.len() == 64 { s.recent.pop_front(); }
                        s.recent.push_back(serde_json::json!({"sequence":sequence,"receivedAtMs":timestamp,"foreground":foreground,"waitMs":waited,"totalMs":started.elapsed().as_millis(),"error":error}));
                    });
                    if let Err(error) = queued {
                        let mut s = stats.lock().unwrap();
                        s.failed += 1;
                        s.completed += 1;
                        if s.recent.len() == 64 { s.recent.pop_front(); }
                        s.recent.push_back(serde_json::json!({"sequence":sequence,"error":error.to_string()}));
                    }
                }
                // Wake on new input, with a bounded timeout for clean shutdown.
                unsafe { MsgWaitForMultipleObjectsEx(0, std::ptr::null(), 50, QS_ALLINPUT, MWMO_INPUTAVAILABLE); }
            }
            unsafe { UnregisterHotKey(null_mut(), id); }
            stats.lock().unwrap().registered = false;
        }).map_err(|e| e.to_string())?;
        match ready_rx.recv().map_err(|e| e.to_string())? {
            Ok(()) => Ok(Self {
                stop,
                worker: Mutex::new(Some(worker)),
                status,
            }),
            Err(error) => {
                let _ = worker.join();
                Err(error)
            }
        }
    }

    pub fn stop(&self) {
        self.stop.store(true, Ordering::Release);
        if let Some(worker) = self.worker.lock().unwrap().take() {
            let _ = worker.join();
        }
    }

    pub fn status(&self) -> serde_json::Value {
        serde_json::to_value(&*self.status.lock().unwrap()).unwrap()
    }
}

impl Drop for Listener {
    fn drop(&mut self) {
        self.stop();
    }
}
