import { spawnSync } from 'node:child_process';
// Set TZ before starting Node: changing it in a running Windows process is unreliable.
for (const [tz, file] of [
  ['Asia/Shanghai', 'tests/time.test.ts'],
  ['Asia/Shanghai', 'tests/preferences.test.ts'],
  ['Asia/Shanghai', 'tests/wallpaper.test.ts'],
  ['Asia/Shanghai', 'tests/wallpaper-recovery.test.ts'],
  ['Asia/Shanghai', 'tests/wallpaper-contrast.test.ts'],
  ['America/New_York', 'tests/dst.test.ts'],
  ['Australia/Lord_Howe', 'tests/half-hour-dst.test.ts'],
]) {
  const result = spawnSync(process.execPath, ['node_modules/vitest/vitest.mjs', 'run', file], {
    stdio: 'inherit', env: { ...process.env, TZ: tz },
  });
  if (result.status !== 0) process.exit(result.status ?? 1);
}
