import { invoke } from '@tauri-apps/api/core';
import { getCurrentWindow } from '@tauri-apps/api/window';
import { wallpaperTextColor } from './wallpaper-contrast';

interface WallpaperSnapshot {
  imageKey: string; imageData: string | null;
  mode: number; color: string; left: number; top: number;
  width: number; height: number; clientX: number; clientY: number;
}

export function wallpaperLayout(s: Omit<WallpaperSnapshot, 'imageKey' | 'imageData'>, scale: number, imageWidth: number, imageHeight: number) {
  const nativeSize = `${imageWidth / scale}px ${imageHeight / scale}px`;
  return {
    left: `${(s.left - s.clientX) / scale}px`, top: `${(s.top - s.clientY) / scale}px`,
    width: `${s.width / scale}px`, height: `${s.height / scale}px`, backgroundColor: s.color,
    backgroundSize: s.mode === 2 ? '100% 100%' : s.mode === 3 ? 'contain' : s.mode >= 4 ? 'cover' : nativeSize,
    backgroundPosition: s.mode === 1 ? '0 0' : 'center', backgroundRepeat: s.mode === 1 ? 'repeat' : 'no-repeat',
  };
}

export async function startWallpaper(onStatus: (message: string | null) => void, onTextColor: (color: 'black' | 'white') => void = () => {}, isActive: () => boolean = () => true) {
  const viewport = document.getElementById('wallpaper-viewport')!;
  const layer = document.getElementById('wallpaper-image')!;
  let imageKey: string | null = null, width = 0, height = 0, busy = false, pending = false, lastError = '';
  let failures = 0, retryAfter = 0;
  let decodedImage: HTMLImageElement | null = null;
  async function refresh() {
    if (!isActive()) {
      viewport.hidden = true;
      if (lastError) onStatus(null);
      lastError = ''; failures = 0; retryAfter = 0;
      return;
    }
    if (busy) { pending = true; return; }
    if (Date.now() < retryAfter) return;
    busy = true;
    try {
      const snapshot = await invoke<WallpaperSnapshot | null>('wallpaper_snapshot', { knownKey: imageKey });
      if (!isActive()) return;
      if (!snapshot) return;
      if (snapshot.imageData !== null) {
        if (snapshot.imageData) {
          const img = new Image(); img.src = snapshot.imageData; await img.decode();
          width = img.naturalWidth; height = img.naturalHeight;
          decodedImage = img;
          layer.style.backgroundImage = `url("${snapshot.imageData}")`;
        } else { layer.style.backgroundImage = 'none'; width = 0; height = 0; decodedImage = null; }
        imageKey = snapshot.imageKey;
      }
      Object.assign(layer.style, wallpaperLayout(snapshot, devicePixelRatio, width, height));
      viewport.hidden = false;
      onTextColor(wallpaperTextColor(snapshot, decodedImage, innerWidth * devicePixelRatio, innerHeight * devicePixelRatio));
      if (lastError) onStatus(null);
      lastError = '';
      failures = 0; retryAfter = 0;
    } catch (error) {
      // Retain a usable sample during a transient shell/file failure. Do not
      // expand the widget for a one-off startup error or a wallpaper transition.
      if (imageKey === null) viewport.hidden = true;
      failures++; retryAfter = Date.now() + 5000;
      if (failures >= 3) {
        const message = `壁纸暂时无法更新，正在重试：${String(error)}`;
        if (message !== lastError) onStatus(message);
        lastError = message;
      }
    } finally {
      busy = false;
      if (pending) { pending = false; void refresh(); }
    }
  }
  let timer: ReturnType<typeof setTimeout>;
  const scheduleRefresh = () => { clearTimeout(timer); timer = setTimeout(() => void refresh(), 100); };
  await getCurrentWindow().onMoved(scheduleRefresh);
  window.addEventListener('resize', scheduleRefresh);
  window.addEventListener('focus', scheduleRefresh);
  window.addEventListener('wallpaper-refresh', scheduleRefresh);
  document.addEventListener('visibilitychange', scheduleRefresh);
  setInterval(() => void refresh(), 5000);
  await refresh();
}
