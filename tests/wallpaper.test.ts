import { expect, it } from 'vitest';
import { wallpaperLayout } from '../src/wallpaper';
const sample = { mode: 4, color: '#000', left: -1920, top: 0, width: 1920, height: 1080, clientX: -400, clientY: 700 };
it('aligns the wallpaper on a negative-coordinate monitor with text/DPI scaling', () => {
  const layout = wallpaperLayout(sample, 1.25, 3000, 2000);
  expect(layout.left).toBe('-1216px'); expect(layout.top).toBe('-560px');
  expect(layout.width).toBe('1536px'); expect(layout.height).toBe('864px');
});
it.each([[0, '2400px 1600px'], [1, '2400px 1600px'], [2, '100% 100%'], [3, 'contain'], [4, 'cover'], [5, 'cover']])('maps Windows wallpaper mode %s', (mode, size) => {
  const layout = wallpaperLayout({ ...sample, mode }, 1.25, 3000, 2000);
  expect(layout.backgroundSize).toBe(size);
  expect(layout.backgroundRepeat).toBe(mode === 1 ? 'repeat' : 'no-repeat');
});
