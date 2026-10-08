import { expect, it } from 'vitest';
import { validatePreferences } from '../src/preferences';
it('migrates previous preferences to a locked six-slot widget', () => {
  const p = validatePreferences({ wake: '08:00', sleep: '00:00', theme: 'dark', alwaysOnTop: false });
  expect(p.widgetSize).toBe('six');
  expect(p.positionLocked).toBe(true);
  expect(p.theme).toBe('system');
  expect(p.startAtLogin).toBe(false);
});
it('desktop embedding always disables topmost', () => {
  const p = validatePreferences({ wake: '08:00', sleep: '00:00', theme: 'light', alwaysOnTop: true, desktopMode: true });
  expect(p.alwaysOnTop).toBe(false);
});
it('rejects an unsupported footprint', () => {
  expect(() => validatePreferences({ wake: '08:00', sleep: '00:00', theme: 'dark', alwaysOnTop: false, widgetSize: 'eight' })).toThrow();
});
it('preserves the login startup choice and rejects non-boolean settings', () => {
  expect(validatePreferences({ wake: '08:00', sleep: '00:00', theme: 'system', startAtLogin: true }).startAtLogin).toBe(true);
  expect(() => validatePreferences({ startAtLogin: 'yes' })).toThrow();
});
