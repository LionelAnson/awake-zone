import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { invoke } from '@tauri-apps/api/core';
import { startWallpaper } from '../src/wallpaper';

vi.mock('@tauri-apps/api/core', () => ({ invoke: vi.fn() }));
vi.mock('@tauri-apps/api/window', () => ({ getCurrentWindow: () => ({ onMoved: vi.fn().mockResolvedValue(() => {}) }) }));
vi.mock('../src/wallpaper-contrast', () => ({ wallpaperTextColor: () => 'black' }));
const snapshot = { imageKey: 'wallpaper-a', imageData: 'data:image/png;base64,fixture', mode: 4, color: '#000', left: 0, top: 0, width: 1920, height: 1080, clientX: 1500, clientY: 750 };
let viewport: { hidden: boolean }, layer: { style: Record<string, string> }, decodeFails: boolean;

beforeEach(() => {
  vi.useFakeTimers(); vi.mocked(invoke).mockReset();
  viewport = { hidden: true }; layer = { style: {} }; decodeFails = false;
  vi.stubGlobal('document', { getElementById: (id: string) => id === 'wallpaper-viewport' ? viewport : layer, addEventListener: vi.fn() });
  vi.stubGlobal('window', { addEventListener: vi.fn() });
  vi.stubGlobal('devicePixelRatio', 1.25);
  vi.stubGlobal('innerWidth', 240); vi.stubGlobal('innerHeight', 128);
  vi.stubGlobal('Image', class {
    src = ''; naturalWidth = 1920; naturalHeight = 1080;
    decode() { return decodeFails ? Promise.reject(new Error('partial file')) : Promise.resolve(); }
  });
});
afterEach(() => { vi.clearAllTimers(); vi.useRealTimers(); vi.unstubAllGlobals(); });

it('retries transient startup failures without leaving an error banner', async () => {
  vi.mocked(invoke).mockRejectedValueOnce('0x80004005').mockRejectedValueOnce('0x80004005').mockResolvedValue(snapshot);
  const status = vi.fn();
  await startWallpaper(status);
  await vi.advanceTimersByTimeAsync(10000);
  expect(viewport.hidden).toBe(false);
  expect(status).not.toHaveBeenCalled();
  expect(invoke).toHaveBeenCalledTimes(3);
});

it('stops sampling wallpaper while floating and resumes the cached desktop image on return', async () => {
  let active = true;
  vi.mocked(invoke).mockResolvedValueOnce(snapshot).mockResolvedValue({ ...snapshot, imageData: null });
  await startWallpaper(vi.fn(), vi.fn(), () => active);
  active = false;
  await vi.advanceTimersByTimeAsync(5000);
  expect(viewport.hidden).toBe(true);
  expect(invoke).toHaveBeenCalledTimes(1);
  active = true;
  await vi.advanceTimersByTimeAsync(5000);
  expect(viewport.hidden).toBe(false);
  expect(invoke).toHaveBeenCalledTimes(2);
});

it('discards an in-flight wallpaper response after switching to floating mode', async () => {
  let active = true;
  let finish!: (value: typeof snapshot) => void;
  vi.mocked(invoke).mockImplementation(() => new Promise(resolve => { finish = resolve; }));
  const started = startWallpaper(vi.fn(), vi.fn(), () => active);
  await vi.advanceTimersByTimeAsync(0);
  active = false; finish(snapshot); await started;
  expect(viewport.hidden).toBe(true);
  expect(layer.style.backgroundImage).toBeUndefined();
});

it('retains a good wallpaper on failure and clears its warning after recovery with a cached image', async () => {
  vi.mocked(invoke).mockResolvedValueOnce(snapshot).mockRejectedValueOnce('0x80004005').mockRejectedValueOnce('0x80004005').mockRejectedValueOnce('0x80004005').mockResolvedValue({ ...snapshot, imageData: null });
  const status = vi.fn();
  await startWallpaper(status);
  const background = layer.style.backgroundImage;
  await vi.advanceTimersByTimeAsync(15000);
  expect(viewport.hidden).toBe(false);
  expect(layer.style.backgroundImage).toBe(background);
  expect(status).toHaveBeenLastCalledWith(expect.stringContaining('0x80004005'));
  await vi.advanceTimersByTimeAsync(5000);
  expect(status).toHaveBeenLastCalledWith(null);
  expect(layer.style.backgroundImage).toBe(background);
});

it('does not commit the new cache key until the changed image decodes successfully', async () => {
  vi.mocked(invoke).mockResolvedValueOnce(snapshot).mockResolvedValue({ ...snapshot, imageKey: 'wallpaper-b', imageData: 'data:image/png;base64,new-image' });
  await startWallpaper(vi.fn());
  decodeFails = true;
  await vi.advanceTimersByTimeAsync(5000);
  expect(layer.style.backgroundImage).toContain('fixture');
  decodeFails = false;
  await vi.advanceTimersByTimeAsync(5000);
  expect(invoke).toHaveBeenLastCalledWith('wallpaper_snapshot', { knownKey: 'wallpaper-a' });
  expect(layer.style.backgroundImage).toContain('new-image');
});
