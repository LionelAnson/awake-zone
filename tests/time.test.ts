import { describe, expect, it } from 'vitest';
import { calculateDay, DEFAULT_SCHEDULE, remainingText } from '../src/time';

describe('local planned waking day', () => {
  it.each([
    ['2026-09-28T08:00:00', '00:00', 0, 'awake'],
    ['2026-09-28T12:00:00', '06:00', 25, 'awake'],
    ['2026-09-28T14:00:00', '09:00', 37.5, 'awake'],
    ['2026-09-28T16:00:00', '12:00', 50, 'awake'],
    ['2026-09-28T20:00:00', '18:00', 75, 'awake'],
    ['2026-09-29T00:00:00', '24:00', 100, 'rest'],
    ['2026-09-29T07:59:59', '24:00', 100, 'rest'],
    ['2026-09-29T08:00:00', '00:00', 0, 'awake'],
  ])('%s -> %s (%s%%)', (date, personal, percent, phase) => {
    const s = calculateDay(DEFAULT_SCHEDULE, new Date(date));
    expect(s.personalTime).toBe(personal);
    expect(s.percent).toBe(percent);
    expect(s.percentText).toBe(`${percent.toFixed(2)}%`);
    expect(s.progress * 100).toBe(percent);
    expect(s.phase).toBe(phase);
  });
  it('does not reset at midnight for 10:00–02:00', () => {
    const s = calculateDay({ wake: '10:00', sleep: '02:00' }, new Date('2026-09-29T00:00:00'));
    expect(s.personalTime).toBe('21:00');
    expect(s.percent).toBe(87.5);
    expect(s.start.getDate()).toBe(28);
    expect(remainingText(s.remainingMs)).toBe('2 小时 0 分钟');
  });
  it('uses today for a same-date bedtime', () => {
    const s = calculateDay({ wake: '06:00', sleep: '22:00' }, new Date('2026-09-28T14:00:00'));
    expect(s.percent).toBe(50);
    expect(s.end.getDate()).toBe(28);
  });
  it.each(['2027-01-01T01:00:00', '2028-03-01T01:00:00'])('crosses calendar boundaries: %s', date => {
    const s = calculateDay({ wake: '10:00', sleep: '02:00' }, new Date(date));
    expect(s.personalTime).toBe('22:30');
    expect(s.percent).toBe(93.75);
  });
  it('never rounds up before bedtime', () => {
    const s = calculateDay(DEFAULT_SCHEDULE, new Date('2026-09-28T23:59:59.999'));
    expect(s.personalTime).toBe('23:59');
    expect(s.percentText).toBe('99.99%');
    expect(s.progress).toBeLessThan(1);
    expect(s.remainingMs).toBe(1);
  });
  it('recomputes directly after sleep/restart/time change', () => {
    for (const date of ['2026-09-28T23:00:00', '2026-10-02T12:00:00', '2026-09-28T12:00:00']) {
      const s = calculateDay(DEFAULT_SCHEDULE, new Date(date));
      expect(s.progress).toBe(date.includes('23:00') ? 15 / 16 : 0.25);
    }
  });
  it.each([
    { wake: '08:00', sleep: '08:00' }, { wake: '24:00', sleep: '08:00' },
    { wake: '8:00', sleep: '00:00' }, { wake: '08:60', sleep: '00:00' },
  ])('rejects invalid schedule %j', schedule => {
    expect(() => calculateDay(schedule)).toThrow();
  });
  it('rejects an invalid current date', () => {
    expect(() => calculateDay(DEFAULT_SCHEDULE, new Date(NaN))).toThrow();
  });
});
