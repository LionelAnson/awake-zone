import { Temporal } from '@js-temporal/polyfill';

export const TIME_ZONE = 'Asia/Shanghai';
export interface Schedule { wake: string; sleep: string }
export const DEFAULT_SCHEDULE: Schedule = { wake: '08:00', sleep: '00:00' };

export function parseTime(value: string): number {
  if (!/^([01]\d|2[0-3]):[0-5]\d$/.test(value)) throw new Error('请输入有效的时刻（HH:mm）。');
  const [h, m] = value.split(':').map(Number);
  return h * 60 + m;
}

export function validateSchedule(schedule: Schedule): void {
  if (parseTime(schedule.wake) === parseTime(schedule.sleep)) {
    throw new Error('起床时间与入睡时间不能相同。');
  }
}

function boundary(date: Temporal.PlainDate, dayOffset: number, minutes: number, timeZone: string): Date {
  const day = date.add({ days: dayOffset });
  // Calendar arithmetic. Compatible disambiguation moves skipped times forward
  // through the DST gap and chooses the first occurrence of repeated times.
  return new Date(Temporal.ZonedDateTime.from({
    timeZone, year: day.year, month: day.month, day: day.day,
    hour: Math.floor(minutes / 60), minute: minutes % 60,
  }, { disambiguation: 'compatible' }).epochMilliseconds);
}

export interface DaySnapshot {
  phase: 'awake' | 'rest';
  now: Date; start: Date; end: Date; nextWake: Date;
  durationMs: number; remainingMs: number;
  progress: number; personalMinutes: number; personalTime: string;
  percent: number; percentText: string;
}

/** Pure calculation. All displayed progress values derive from this single snapshot. */
export function calculateDay(schedule: Schedule, now: Date = new Date(), timeZone = TIME_ZONE): DaySnapshot {
  validateSchedule(schedule);
  if (!Number.isFinite(now.getTime())) throw new Error('当前系统时间无效。');
  const wake = parseTime(schedule.wake);
  const sleep = parseTime(schedule.sleep);
  const date = Temporal.Instant.fromEpochMilliseconds(now.getTime()).toZonedDateTimeISO(timeZone).toPlainDate();
  const todayWake = boundary(date, 0, wake, timeZone);
  const offset = now.getTime() >= todayWake.getTime() ? 0 : -1;
  const start = boundary(date, offset, wake, timeZone);
  const end = boundary(date, offset + (sleep < wake ? 1 : 0), sleep, timeZone);
  const nextWake = boundary(date, offset + 1, wake, timeZone);
  const durationMs = end.getTime() - start.getTime();
  if (durationMs <= 0) throw new Error('该日期的夏令时变化使清醒时段无效，请调整作息。');
  const phase = now.getTime() >= end.getTime() ? 'rest' : 'awake';
  const progress = phase === 'rest' ? 1 : Math.max(0, Math.min(1, (now.getTime() - start.getTime()) / durationMs));
  const personalMinutes = phase === 'rest' ? 1440 : Math.min(1439, Math.floor(progress * 1440));
  // Two decimals, truncated rather than rounded: never claim completion early.
  const percent = phase === 'rest' ? 100 : Math.min(99.99, Math.floor(progress * 10000) / 100);
  return {
    phase, now: new Date(now), start, end, nextWake, durationMs,
    remainingMs: Math.max(0, end.getTime() - now.getTime()), progress,
    personalMinutes, personalTime: `${String(Math.floor(personalMinutes / 60)).padStart(2, '0')}:${String(personalMinutes % 60).padStart(2, '0')}`,
    percent, percentText: `${percent.toFixed(2)}%`,
  };
}

export function remainingText(ms: number): string {
  const minutes = Math.ceil(Math.max(0, ms) / 60_000);
  return `${Math.floor(minutes / 60)} 小时 ${minutes % 60} 分钟`;
}

export function scheduleText(schedule: Schedule): string {
  return `${schedule.wake} — ${parseTime(schedule.sleep) < parseTime(schedule.wake) ? '次日 ' : ''}${schedule.sleep}`;
}

export function scheduleDurationText(schedule: Schedule): string {
  validateSchedule(schedule);
  const wake = parseTime(schedule.wake);
  const sleep = parseTime(schedule.sleep);
  const minutes = (sleep - wake + 1440) % 1440;
  return `${Math.floor(minutes / 60)} 小时 ${minutes % 60} 分钟`;
}
