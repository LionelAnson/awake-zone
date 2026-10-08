import { expect, it } from 'vitest';
import { calculateDay as calculate } from '../src/time';
const calculateDay: typeof calculate = (schedule, now) => calculate(schedule, now, Intl.DateTimeFormat().resolvedOptions().timeZone);
it('handles a 30 minute DST change on Lord Howe', () => {
  expect(new Date('2026-10-04T12:00:00').getTimezoneOffset()).toBe(-660);
  const s = calculateDay({ wake: '22:00', sleep: '06:00' }, new Date('2026-10-04T03:00:00'));
  expect(s.durationMs).toBe(7.5 * 3600000);
  expect(s.progress).toBe(4.5 / 7.5);
});

