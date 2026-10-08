import { expect, it } from 'vitest';
import { calculateDay as calculate } from '../src/time';
const calculateDay: typeof calculate = (schedule, now) => calculate(schedule, now, Intl.DateTimeFormat().resolvedOptions().timeZone);
const schedule = { wake: '22:00', sleep: '06:00' };
it('runs in New York', () => expect(new Date('2026-01-01T12:00:00').getTimezoneOffset()).toBe(300));
it('product default remains Beijing even on a New York system', () => {
  const s = calculate({ wake: '08:00', sleep: '00:00' }, new Date('2026-09-28T04:00:00Z'));
  expect(s.personalTime).toBe('06:00');
  expect(s.percent).toBe(25);
  expect(s.start.toISOString()).toBe('2026-09-28T00:00:00.000Z');
});
it('spring forward: 8 wall hours are 7 actual hours', () => {
  const s = calculateDay(schedule, new Date('2026-03-08T03:00:00-04:00'));
  expect(s.durationMs).toBe(7 * 3600000);
  expect(s.progress).toBe(4 / 7);
  expect(s.remainingMs).toBe(3 * 3600000);
});
it('fall back: 8 wall hours are 9 actual hours', () => {
  const first = calculateDay(schedule, new Date('2026-11-01T01:30:00-04:00'));
  const second = calculateDay(schedule, new Date('2026-11-01T01:30:00-05:00'));
  expect(first.durationMs).toBe(9 * 3600000);
  expect(first.progress).toBe(3.5 / 9);
  expect(second.progress).toBe(4.5 / 9);
  expect(second.start.getTime()).toBe(first.start.getTime());
});
it('next wake is constructed by calendar date on 23/25 hour days', () => {
  for (const [date, hours] of [['2026-03-08T01:00:00', 23], ['2026-11-01T01:00:00', 25]] as const) {
    const s = calculateDay({ wake: '08:00', sleep: '00:00' }, new Date(date));
    expect(s.nextWake.getTime() - s.start.getTime()).toBe(hours * 3600000);
  }
});
it('skipped times move forward; duplicate times choose first occurrence', () => {
  const spring = calculateDay({ wake: '02:30', sleep: '10:00' }, new Date('2026-03-08T03:30:00-04:00'));
  expect(spring.progress).toBe(0);
  expect(spring.start.getHours()).toBe(3);
  const fall = calculateDay({ wake: '01:30', sleep: '10:00' }, new Date('2026-11-01T01:30:00-05:00'));
  expect(fall.now.getTime() - fall.start.getTime()).toBe(3600000);
});
it('reports a schedule collapsed by the DST gap', () => {
  expect(() => calculateDay({ wake: '02:30', sleep: '03:00' }, new Date('2026-03-08T04:00:00'))).toThrow('夏令时');
});

