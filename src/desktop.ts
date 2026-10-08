import { invoke, isTauri } from '@tauri-apps/api/core';
import { listen } from '@tauri-apps/api/event';
import { getCurrentWindow } from '@tauri-apps/api/window';
import { DEFAULT_PREFERENCES, validatePreferences, type Preferences } from './preferences';
export const desktop = isTauri();
export const settingsPage = new URLSearchParams(location.search).has('settings');
const KEY = 'personal-day.preferences.v1';
let lastWindowSize = '';
export async function loadPreferences(): Promise<Preferences> {
  if (desktop) return validatePreferences(await invoke('load_preferences'));
  const saved = localStorage.getItem(KEY);
  return saved ? validatePreferences(JSON.parse(saved)) : { ...DEFAULT_PREFERENCES };
}
export async function savePreferences(value: Preferences): Promise<void> {
  const settings = validatePreferences(value);
  if (desktop) await invoke('save_preferences', { preferences: settings });
  else localStorage.setItem(KEY, JSON.stringify(settings));
}
export async function resizeWindow(preferences: Preferences, hasError = false, resting = false): Promise<void> {
  if (desktop) {
    // Windows text scaling can add WebView zoom on top of monitor DPI. Preserve
    // that accessibility setting while reserving the chosen CSS footprint.
    const monitorScale = await getCurrentWindow().scaleFactor();
    const zoom = Math.max(1, window.devicePixelRatio / monitorScale);
    const width = Math.ceil((settingsPage ? 340 : preferences.widgetSize === 'four' ? 160 : 240) * zoom);
    const height = Math.ceil(((settingsPage ? 550 : 128 + (resting ? 30 : 0)) + (hasError ? 70 : 0)) * zoom);
    const key = `${width}x${height}:${preferences.desktopMode}`;
    if (key === lastWindowSize) return;
    lastWindowSize = key;
    try { await invoke('resize_window', { width, height, radius: 8 * zoom }); }
    catch (error) { lastWindowSize = ''; throw error; }
  }
}
export async function hideWindow(): Promise<void> { if (desktop) await invoke('hide_window'); }
export async function dragWindow(): Promise<void> { if (desktop) await invoke('start_widget_drag'); }
export async function openSettings(): Promise<void> {
  if (desktop) await invoke('open_settings');
  else window.open('/?settings=1', 'personal-day-settings', 'width=340,height=520');
}
export async function closeSettings(): Promise<void> {
  if (desktop) await invoke('close_settings'); else window.close();
}
export async function onPreferencesChanged(callback: () => void): Promise<void> {
  if (desktop) {
    await listen('preferences-changed', callback);
    if (settingsPage) await listen('refresh-settings', callback);
  } else window.addEventListener('storage', event => { if (event.key === KEY) callback(); });
}
export async function desktopSupported(): Promise<boolean> {
  return desktop && (await invoke<{ desktopSupported: boolean }>('platform_info')).desktopSupported;
}
export async function onDesktopError(callback: (message: string) => void): Promise<void> {
  if (!desktop) return;
  await listen<string>('desktop-error', event => callback(event.payload));
  const warning = await invoke<string | null>('startup_warning');
  if (warning) callback(warning);
}
