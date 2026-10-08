import './style.css';
import { invoke } from '@tauri-apps/api/core';
import { calculateDay, TIME_ZONE } from './time';
import { DEFAULT_PREFERENCES, validatePreferences, type Preferences } from './preferences';
import { startWallpaper } from './wallpaper';
import { desktop, settingsPage, desktopSupported, dragWindow, hideWindow, loadPreferences, openSettings,
  closeSettings, onPreferencesChanged, onDesktopError, resizeWindow, savePreferences } from './desktop';

const $ = <T extends HTMLElement>(id: string) => document.getElementById(id) as T;
document.documentElement.dataset.page = settingsPage ? 'settings' : 'widget';
let preferences: Preferences = { ...DEFAULT_PREFERENCES };
let saving = false;
let nativeBackdrop = false;
let desktopLayerSupported = false;
const timeFormat = new Intl.DateTimeFormat('en-GB', { timeZone: TIME_ZONE, hour: '2-digit', minute: '2-digit', hourCycle: 'h23' });
const dateFormat = new Intl.DateTimeFormat('zh-CN', { timeZone: TIME_ZONE, month: 'numeric', day: 'numeric' });
function fit() { return resizeWindow(preferences, !$('error').hidden, !$('rest-note').hidden); }
let appError: string | null = null;
let wallpaperError: string | null = null;
function updateErrorDisplay() {
  const message = appError ?? wallpaperError;
  $('error').textContent = message ?? '';
  $('error').hidden = message === null;
  void fit().catch(console.error);
}
function reportError(error: unknown) {
  appError = error instanceof Error ? error.message : String(error);
  updateErrorDisplay();
}
function clearError() { appError = null; updateErrorDisplay(); }
function reportWallpaperStatus(message: string | null) { wallpaperError = message; updateErrorDisplay(); }
function render() {
  try {
    const snapshot = calculateDay(preferences, new Date());
    $('regular-time').textContent = timeFormat.format(snapshot.now);
    $('personal-time').textContent = snapshot.personalTime;
    $('percent').textContent = snapshot.percentText;
    $('progress-fill').style.width = `${snapshot.progress * 100}%`;
    $('progress').setAttribute('aria-valuenow', String(snapshot.progress * 100));
    $('progress').setAttribute('aria-valuetext', snapshot.percentText);
    const rest = snapshot.phase === 'rest';
    $('phase').textContent = rest ? '计划休息时段' : '计划清醒日';
    const changedPhase = $('rest-note').hidden === rest;
    $('rest-note').hidden = !rest;
    $('rest-note').textContent = rest ? `上一清醒日已结束 · 下次起床 ${dateFormat.format(snapshot.nextWake)} ${timeFormat.format(snapshot.nextWake)}` : '';
    document.documentElement.dataset.size = preferences.widgetSize;
    document.documentElement.dataset.locked = String(preferences.positionLocked);
    $('return-desktop').hidden = !desktopLayerSupported || settingsPage || preferences.desktopMode;
    const mode = nativeBackdrop && !preferences.desktopMode ? 'floating' : 'desktop';
    if (document.documentElement.dataset.mode !== mode) {
      document.documentElement.dataset.mode = mode;
      window.dispatchEvent(new Event('wallpaper-refresh'));
    }
    $('lock').setAttribute('aria-pressed', String(preferences.positionLocked));
    $('lock').title = preferences.positionLocked ? '解锁位置，然后拖动顶栏' : '锁定位置';
    if (changedPhase) void fit().catch(reportError);
  } catch (error) { reportError(error); }
}
function updateTopOption() {
  const embedded = $<HTMLInputElement>('desktop-mode').checked;
  $<HTMLInputElement>('always-on-top').disabled = embedded;
  if (embedded) $<HTMLInputElement>('always-on-top').checked = false;
}
function populateSettings() {
  $<HTMLInputElement>('wake').value = preferences.wake;
  $<HTMLInputElement>('sleep').value = preferences.sleep;
  $<HTMLInputElement>('start-at-login').checked = preferences.startAtLogin;
  $<HTMLSelectElement>('widget-size').value = preferences.widgetSize;
  $<HTMLInputElement>('position-locked').checked = preferences.positionLocked;
  $<HTMLInputElement>('desktop-mode').checked = preferences.desktopMode;
  $<HTMLInputElement>('always-on-top').checked = preferences.alwaysOnTop;
  updateTopOption();
}
async function save(next: Preferences) {
  if (saving) return false;
  saving = true;
  try {
    await savePreferences(next);
    preferences = next;
    clearError(); render(); await fit();
    return true;
  } finally { saving = false; }
}
$('settings-button').addEventListener('click', () => void openSettings().catch(reportError));
$('cancel').addEventListener('click', () => void closeSettings().catch(reportError));
$('hide').addEventListener('click', () => void hideWindow().catch(reportError));
$('return-desktop').addEventListener('click', () => {
  if (saving) return;
  saving = true;
  $<HTMLButtonElement>('return-desktop').disabled = true;
  void invoke('return_to_desktop').then(reloadPreferences).catch(reportError).finally(() => {
    saving = false;
    $<HTMLButtonElement>('return-desktop').disabled = false;
  });
});
$('lock').addEventListener('click', () => void save({ ...preferences, positionLocked: !preferences.positionLocked }).catch(reportError));
$('drag-handle').addEventListener('mousedown', event => {
  if (!preferences.positionLocked && event.button === 0 && !(event.target as HTMLElement).closest('button')) void dragWindow().catch(reportError);
});
$('desktop-mode').addEventListener('change', updateTopOption);
$('settings-form').addEventListener('submit', event => {
  event.preventDefault();
  void (async () => {
    const next = validatePreferences({ ...preferences,
      wake: $<HTMLInputElement>('wake').value, sleep: $<HTMLInputElement>('sleep').value,
      theme: 'system', startAtLogin: $<HTMLInputElement>('start-at-login').checked, widgetSize: $<HTMLSelectElement>('widget-size').value,
      positionLocked: $<HTMLInputElement>('position-locked').checked,
      desktopMode: $<HTMLInputElement>('desktop-mode').checked,
      alwaysOnTop: $<HTMLInputElement>('always-on-top').checked,
    });
    if (await save(next)) await closeSettings();
  })().catch(reportError);
});
document.addEventListener('keydown', event => {
  if (event.key === 'Escape' && settingsPage) void closeSettings().catch(reportError);
});
async function reloadPreferences() {
  preferences = await loadPreferences(); render();
  if (settingsPage) populateSettings();
  await fit();
}
async function start() {
  if (desktop) {
    // WebView can load before Tauri's setup has registered and initialized state.
    // Wait asynchronously rather than displaying a false settings error/defaults.
    let ready = false;
    for (let attempt = 0; attempt < 180; attempt++) {
      if (await invoke<boolean>('startup_ready')) { ready = true; break; }
      await new Promise(resolve => setTimeout(resolve, 100));
    }
    if (!ready) throw new Error('时钟初始化尚未完成，请退出后重新打开。');
    try {
      const platform = await invoke<{ nativeBackdrop: boolean; desktopSupported: boolean }>('platform_info');
      nativeBackdrop = platform.nativeBackdrop;
      desktopLayerSupported = platform.desktopSupported;
    }
    catch (error) { console.error(error); }
  }
  try { preferences = await loadPreferences(); } catch (error) { reportError(`无法读取设置，暂用默认作息。${String(error)}`); }
  $('settings-panel').hidden = !settingsPage;
  if (settingsPage) {
    populateSettings();
    $<HTMLInputElement>('desktop-mode').disabled = !(await desktopSupported());
    $<HTMLInputElement>('start-at-login').disabled = !desktop;
  }
  render();
  await fit();
  await onPreferencesChanged(() => void reloadPreferences().catch(reportError));
  if (!settingsPage) await onDesktopError(reportError);
  // Clock updates must continue even while a Windows wallpaper request is pending.
  if (desktop && !settingsPage) void startWallpaper(reportWallpaperStatus, color => {
    if (!nativeBackdrop || preferences.desktopMode) document.documentElement.dataset.ink = color;
  }, () => !nativeBackdrop || preferences.desktopMode).catch(reportError);
  let sampling = false;
  async function refreshBackdropInk() {
    if (!nativeBackdrop || !desktop || settingsPage || preferences.desktopMode || sampling) return;
    sampling = true;
    try {
      const brightness = await invoke<number | null>('backdrop_brightness');
      if (brightness !== null && !preferences.desktopMode) {
        const threshold = document.documentElement.dataset.ink === 'white' ? .2 : .16;
        document.documentElement.dataset.ink = brightness < threshold ? 'white' : 'black';
      }
    } catch (error) { reportError(error); }
    finally { sampling = false; }
  }
  if (desktop && !settingsPage) { void refreshBackdropInk(); setInterval(() => void refreshBackdropInk(), 700); }
  setInterval(render, 1000);
  window.addEventListener('focus', render);
  window.addEventListener('pageshow', render);
  document.addEventListener('visibilitychange', render);
  let resizeTimer: ReturnType<typeof setTimeout>;
  window.addEventListener('resize', () => {
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(() => void fit().catch(reportError), 100);
  });
  if (!desktop) $<HTMLButtonElement>('hide').disabled = true;
}
void start().catch(reportError);
