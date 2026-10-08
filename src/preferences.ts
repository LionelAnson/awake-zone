import { DEFAULT_SCHEDULE, validateSchedule, type Schedule } from './time';
export interface Preferences extends Schedule {
  theme: 'system'; alwaysOnTop: boolean; startAtLogin: boolean;
  widgetSize: 'four' | 'six'; positionLocked: boolean; desktopMode: boolean;
}
export const DEFAULT_PREFERENCES: Preferences = {
  ...DEFAULT_SCHEDULE, theme: 'system', alwaysOnTop: false, startAtLogin: false,
  widgetSize: 'six', positionLocked: true, desktopMode: false,
};
export function validatePreferences(value: unknown): Preferences {
  if (!value || typeof value !== 'object') throw new Error('本地设置格式无效。');
  const s = { ...DEFAULT_PREFERENCES, ...value } as Preferences;
  if (typeof s.wake !== 'string' || typeof s.sleep !== 'string' || !['system', 'light', 'dark'].includes(s.theme) || typeof s.alwaysOnTop !== 'boolean' || typeof s.startAtLogin !== 'boolean') throw new Error('本地设置格式无效。');
  validateSchedule(s);
  if (!['four', 'six'].includes(s.widgetSize) || typeof s.positionLocked !== 'boolean' || typeof s.desktopMode !== 'boolean') throw new Error('组件设置无效。');
  return { wake: s.wake, sleep: s.sleep, theme: 'system', startAtLogin: s.startAtLogin, alwaysOnTop: s.desktopMode ? false : s.alwaysOnTop,
    widgetSize: s.widgetSize, positionLocked: s.positionLocked, desktopMode: s.desktopMode };
}
