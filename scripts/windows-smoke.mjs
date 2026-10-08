// Windows-only smoke check of the real release executable, via WebView2 CDP
// and Win32 window queries. Close Personal Day before running. The test restores
// preferences and ends only processes it starts. It does not test tray clicks.
import { chromium } from 'playwright';
import { spawn, execFileSync } from 'node:child_process';
import { readFileSync, mkdirSync, writeFileSync } from 'node:fs';
import { resolve, join } from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';
import assert from 'node:assert/strict';

if (process.platform !== 'win32') throw new Error('Run this check on Windows.');
const existing = execFileSync('powershell.exe', ['-NoProfile', '-Command', '(Get-Process personal-day -ErrorAction SilentlyContinue | Measure-Object).Count'], { encoding: 'utf8', windowsHide: true }).trim();
if (existing !== '0') throw new Error('Close Personal Day before running the native smoke check.');
const exe = resolve('src-tauri/target/release/personal-day.exe');
const stateFile = join(process.env.APPDATA, 'com.personalday.widget', 'state.json');
const results = [];
let child, browser, page, handle, initialPreferences;
let originalPosition;
const pass = name => { results.push(name); console.log(`PASS: ${name}`); };
const probe = (action = 'inspect', x = 100, y = 100) => JSON.parse(execFileSync('powershell.exe', [
  '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', resolve('scripts/window-probe.ps1'),
  '-Action', action, '-Handle', String(handle), '-X', String(x), '-Y', String(y),
], { encoding: 'utf8', windowsHide: true }).trim());
const invoke = (command, args = {}) => page.evaluate(({ command, args }) => window.__TAURI_INTERNALS__.invoke(command, args), { command, args });
async function launch() {
  child = spawn(exe, [], { windowsHide: true, stdio: 'ignore', env: {
    ...process.env, WEBVIEW2_ADDITIONAL_BROWSER_ARGUMENTS: '--remote-debugging-port=9223',
  } });
  for (let i = 0; i < 60; i++) {
    if (child.exitCode !== null) throw new Error(`App exited with ${child.exitCode}`);
    try { browser = await chromium.connectOverCDP('http://127.0.0.1:9223'); break; } catch { await delay(500); }
  }
  if (!browser) throw new Error('WebView2 debug endpoint did not start.');
  page = browser.contexts()[0].pages()[0];
  await page.locator('#personal-time').filter({ hasText: /\d{2}:\d{2}/ }).waitFor();
  handle = Number(execFileSync('powershell.exe', ['-NoProfile', '-Command', `(Get-Process -Id ${child.pid}).MainWindowHandle.ToInt64()`], { encoding: 'utf8', windowsHide: true }).trim());
  assert.ok(handle, 'Native window handle exists');
}
async function stop() {
  if (browser) { await browser.close(); browser = undefined; }
  if (child && child.exitCode === null) { child.kill(); await delay(1000); }
}
try {
  await launch();
  initialPreferences = await invoke('load_preferences');
  await delay(1000);
  const dimensions = await page.evaluate(() => ({
    width: innerWidth, height: innerHeight,
    scrollWidth: document.documentElement.scrollWidth, scrollHeight: document.documentElement.scrollHeight,
  }));
  assert.ok(dimensions.width >= 320, JSON.stringify(dimensions));
  assert.ok(dimensions.scrollWidth <= dimensions.width && dimensions.scrollHeight <= dimensions.height, JSON.stringify(dimensions));
  pass('native card fits without scrolling under current monitor DPI and text scale');
  originalPosition = probe();
  assert.equal(originalPosition.visible, true);
  pass('release executable starts and renders in WebView2');

  await page.getByRole('button', { name: '设置', exact: true }).click();
  await page.getByRole('textbox', { name: '计划起床' }).fill('10:00');
  await page.getByRole('textbox', { name: '计划入睡' }).fill('10:00');
  await page.getByRole('button', { name: '保存设置' }).click();
  assert.equal(await page.locator('#error').innerText(), '起床时间与入睡时间不能相同。');
  await page.getByRole('textbox', { name: '计划入睡' }).fill('02:00');
  await page.getByRole('combobox').selectOption('dark');
  await page.getByRole('button', { name: '保存设置' }).click();
  await page.locator('#settings-panel').waitFor({ state: 'hidden' });
  let saved = JSON.parse(readFileSync(stateFile, 'utf8'));
  assert.equal(saved.preferences.wake, '10:00');
  assert.equal(saved.preferences.sleep, '02:00');
  assert.equal(saved.preferences.theme, 'dark');
  pass('invalid schedule rejected; native settings save to disk');

  if (saved.preferences.alwaysOnTop) await page.getByRole('button', { name: '置顶', exact: true }).click();
  await page.getByRole('button', { name: '置顶', exact: true }).click();
  await delay(300);
  assert.equal(probe().topmost, true);
  await page.getByRole('button', { name: '置顶', exact: true }).click();
  await delay(300);
  assert.equal(probe().topmost, false);
  pass('pin toggle changes native WS_EX_TOPMOST in both directions');

  probe('move', 100, 100);
  await delay(2500);
  assert.deepEqual(JSON.parse(readFileSync(stateFile, 'utf8')).position, [100, 100]);
  pass('native move persists physical position');
  probe('close');
  await delay(500);
  assert.equal(probe().visible, false);
  assert.equal(child.exitCode, null);
  pass('WM_CLOSE hides the window and keeps process alive');
  const second = spawn(exe, [], { windowsHide: true, stdio: 'ignore' });
  await delay(1500);
  assert.equal(probe().visible, true);
  assert.equal(second.exitCode, 0);
  pass('second launch shows existing window and exits the duplicate process');

  probe('move', 30000, 30000);
  await delay(2500);
  const repaired = probe();
  assert.ok(repaired.x < 30000 && repaired.y < 30000);
  pass('simulated off-screen window returns to visible screen');
  await stop();
  await launch();
  assert.equal(await page.locator('#schedule-label').innerText(), '10:00 — 次日 02:00');
  assert.equal(await page.evaluate(() => document.documentElement.dataset.theme), 'dark');
  assert.equal(probe().x, repaired.x);
  assert.equal(probe().y, repaired.y);
  pass('restart restores schedule, theme and repaired position');
  mkdirSync('output/playwright', { recursive: true });
  await page.screenshot({ path: 'output/playwright/windows-native.png' });
} finally {
  if (page && initialPreferences && child?.exitCode === null) {
    try {
      await invoke('save_preferences', { preferences: initialPreferences });
      if (originalPosition) probe('move', originalPosition.x, originalPosition.y);
      await invoke('hide_window');
    } catch (error) { console.error('Restore failed:', error); }
  }
  await stop();
  mkdirSync('output/playwright', { recursive: true });
  writeFileSync('output/playwright/windows-smoke.json', JSON.stringify({ date: new Date().toISOString(), passed: results, limitations: ['No actual tray clicks', 'No physical monitor unplug', 'No real suspend/resume', 'Installer not exercised'] }, null, 2));
}
