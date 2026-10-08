// Tests the real Windows release. Briefly sends Win+D twice and moves the mouse
// to test the widget's locked/unlocked drag behavior; restores position/settings.
import { chromium } from 'playwright';
import { spawn, execFileSync } from 'node:child_process';
import { readFileSync, mkdirSync, writeFileSync } from 'node:fs';
import { resolve, join } from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';
import assert from 'node:assert/strict';

if (process.platform !== 'win32') throw new Error('Windows only.');
const ps = args => {
  if (args[0] === '-Command') args = ['-Command', '[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false); ' + args[1]];
  return execFileSync('powershell.exe', ['-NoProfile', ...args], { encoding: 'utf8', windowsHide: true }).trim();
};
if (ps(['-Command', '(Get-Process personal-day -ErrorAction SilentlyContinue | Measure-Object).Count']) !== '0') throw new Error('Close Personal Day before testing.');
const exe = resolve('src-tauri/target/release/personal-day.exe');
const stateFile = join(process.env.APPDATA, 'com.personalday.widget', 'state.json');
const originalState = readFileSync(stateFile, 'utf8');
const startupBackup = ps(['-Command', "$key=Get-Item 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Run'; $approved=Get-Item 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\StartupApproved\\Run' -ErrorAction SilentlyContinue; @{value=$key.GetValue('Personal Day'); approved=if($approved){$approved.GetValue('Personal Day')}else{$null}} | ConvertTo-Json -Compress"]);
const results = [];
let child, browser, page, handle, original, originalPosition, desktopShown = false;
let completed = false;
const pass = name => { results.push(name); console.log('PASS:', name); };
const probe = (action = 'inspect', x = 100, y = 100, h = handle) => JSON.parse(ps([
  '-ExecutionPolicy', 'Bypass', '-File', resolve('scripts/window-probe.ps1'),
  '-Action', action, '-Handle', String(h), '-X', String(x), '-Y', String(y),
]));
const invoke = (command, args = {}, target = page) => target.evaluate(({ command, args }) => window.__TAURI_INTERNALS__.invoke(command, args), { command, args });
const preferences = () => invoke('load_preferences');
async function update(fields) { await invoke('save_preferences', { preferences: { ...await preferences(), ...fields } }); await delay(500); }
async function fit(expectedWidth) {
  await delay(400);
  const d = await page.evaluate(() => ({ width: innerWidth, height: innerHeight, sw: document.documentElement.scrollWidth, sh: document.documentElement.scrollHeight }));
  assert.ok(d.width >= expectedWidth && d.width <= expectedWidth + 2, JSON.stringify(d));
  assert.ok(d.sw <= d.width && d.sh <= d.height, JSON.stringify(d));
}
async function launch() {
  child = spawn(exe, [], { windowsHide: true, stdio: 'ignore', env: { ...process.env, WEBVIEW2_ADDITIONAL_BROWSER_ARGUMENTS: '--remote-debugging-port=9223' } });
  for (let n = 0; n < 60; n++) {
    if (child.exitCode !== null) throw new Error('App exited during startup');
    try { browser = await chromium.connectOverCDP('http://127.0.0.1:9223'); break; } catch { await delay(500); }
  }
  assert.ok(browser, 'WebView2 debug endpoint');
  page = browser.contexts().flatMap(c => c.pages()).find(p => !p.url().includes('settings='));
  await page.locator('#personal-time').filter({ hasText: /\d{2}:\d{2}/ }).waitFor();
  handle = (await invoke('platform_info')).windowHandle;
  assert.ok(handle);
  await delay(700);
}
async function stop() {
  if (browser) { await browser.close(); browser = undefined; }
  if (child && child.exitCode === null) { child.kill(); await delay(1000); }
}
try {
  await launch();
  original = await preferences(); originalPosition = probe();
  await update({ desktopMode: true, alwaysOnTop: false, widgetSize: 'six', positionLocked: true });
  await page.locator('#wallpaper-viewport').waitFor({ state: 'visible' });
  assert.equal(await page.locator('#error').isVisible(), false);
  const wallpaperResult = await page.evaluate(async () => {
    const first = await window.__TAURI_INTERNALS__.invoke('wallpaper_snapshot', { knownKey: null });
    const cached = await window.__TAURI_INTERNALS__.invoke('wallpaper_snapshot', { knownKey: first.imageKey });
    return { loaded: first.imageData?.startsWith('data:image/'), cached: cached.imageData === null };
  });
  assert.equal(wallpaperResult.loaded, true); assert.equal(wallpaperResult.cached, true);
  pass('wallpaper sampling and cache succeed with the current stale monitor entry present');
  await page.evaluate(() => {
    // Tauri's invoke is immutable; intercept only this command's local transport.
    const original = window.fetch;
    const endpoint = window.__TAURI_INTERNALS__.convertFileSrc('wallpaper_snapshot', 'ipc');
    let failures = 3;
    window.fetch = function(input, ...args) {
      if (String(input) === endpoint && failures-- > 0) {
        return Promise.resolve(new Response('test E_FAIL (0x80004005)', {
          headers: { 'Tauri-Response': 'error', 'Content-Type': 'text/plain' },
        }));
      }
      return original.call(this, input, ...args);
    };
    window.dispatchEvent(new Event('focus'));
  });
  await page.locator('#error').filter({ hasText: 'test E_FAIL' }).waitFor({ state: 'visible', timeout: 25000 });
  assert.equal(await page.locator('#wallpaper-viewport').isVisible(), true);
  // A poll just before the backoff boundary defers recovery to the next poll.
  await page.locator('#error').waitFor({ state: 'hidden', timeout: 20000 });
  pass('three injected wallpaper failures retain the image; recovery clears the visible warning');
  for (const [instant, percent] of [
    ['2026-09-30T12:00:00+08:00','25.00%'],
    ['2026-09-30T23:59:59.999+08:00','99.99%'],
    ['2026-10-01T00:00:00+08:00','100.00%'],
    ['2026-10-01T08:00:00+08:00','0.00%'],
  ]) {
    await page.clock.setFixedTime(new Date(instant));
    await page.evaluate(() => window.dispatchEvent(new Event('focus')));
    assert.equal(await page.locator('#percent').innerText(), percent);
  }
  await page.clock.setFixedTime(new Date('2026-09-30T12:00:00+08:00'));
  await page.evaluate(() => window.dispatchEvent(new Event('focus')));
  await fit(240);
  pass('native percentages have two decimal places and never reach 100.00% before bedtime');
  const beforeHotkey = probe();
  probe('hotkey');
  assert.equal(probe().topmost, true); assert.equal(probe().child, false);
  assert.equal((await preferences()).desktopMode, false);
  assert.equal((await preferences()).positionLocked, false);
  probe('move', beforeHotkey.x - 180, beforeHotkey.y - 100);
  await delay(2300);
  probe('hotkey');
  const afterHotkey = probe();
  assert.equal(afterHotkey.topmost, false); assert.equal(afterHotkey.child, true);
  assert.equal(afterHotkey.x + afterHotkey.width, beforeHotkey.x + beforeHotkey.width);
  assert.equal(afterHotkey.y + afterHotkey.height, beforeHotkey.y + beforeHotkey.height);
  assert.equal((await preferences()).positionLocked, true);
  pass('real Win+Alt+X returns a moved floating widget to its saved desktop bottom-right anchor, including a held key');
  const embedded = probe();
  assert.equal(embedded.child, true); assert.ok(embedded.parent); assert.equal(embedded.topmost, false);
  pass('six-slot widget is a desktop child, not topmost; no clipping at current DPI/text scale');

  await page.getByRole('button', { name: '设置', exact: true }).click();
  await delay(600);
  const settings = browser.contexts().flatMap(c => c.pages()).find(p => p.url().includes('settings='));
  assert.ok(settings, 'separate settings WebView');
  await settings.getByRole('textbox', { name: '计划起床' }).waitFor();
  const settingsHandle = (await invoke('platform_info', {}, settings)).windowHandle;
  assert.equal(probe().width, embedded.width);
  probe('move', embedded.x, embedded.y, settingsHandle);
  assert.equal(probe().hitInside, false);
  assert.equal(probe('inspect', 0, 0, settingsHandle).child, false);
  pass('ordinary settings window covers the widget; opening it preserves widget footprint');

  probe('desktop'); desktopShown = true;
  const shown = probe();
  assert.equal(shown.visible, true); assert.equal(shown.hitInside, true);
  pass('actual Win+D leaves widget visible and hit-testable on desktop');
  // Move inward from the requested bottom-right anchor, so the visibility
  // repair does not legitimately recenter the widget during this drag check.
  const locked = probe('drag', -40, -30);
  assert.equal(locked.x, shown.x); assert.equal(locked.y, shown.y);
  await page.getByRole('button', { name: '锁定位置', exact: true }).click();
  await delay(400);
  const moved = probe('drag', -40, -30);
  assert.equal(moved.x, shown.x - 40); assert.equal(moved.y, shown.y - 30);
  await delay(2200);
  assert.deepEqual(JSON.parse(readFileSync(stateFile, 'utf8')).position, [moved.x, moved.y]);
  pass('real mouse drag is blocked while locked; unlocked drag moves and saves physical position');
  probe('desktop'); desktopShown = false;

  await invoke('open_settings');
  // Allow the asynchronous refresh-settings event to repopulate the form before editing.
  await delay(500);
  await settings.getByRole('textbox', { name: '计划起床' }).fill('10:00');
  await settings.getByRole('textbox', { name: '计划入睡' }).fill('10:00');
  await settings.getByRole('button', { name: '保存设置' }).click();
  await settings.locator('#error').filter({ hasText: '起床时间与入睡时间不能相同。' }).waitFor();
  assert.equal(await settings.locator('#error').innerText(), '起床时间与入睡时间不能相同。');
  await settings.getByRole('textbox', { name: '计划入睡' }).fill('02:00');
  await settings.getByRole('combobox', { name: '组件大小' }).selectOption('four');
  await settings.getByRole('checkbox', { name: '登录系统后自动启动' }).check();
  await settings.getByRole('checkbox', { name: '锁定位置', exact: true }).check();
  await settings.getByRole('button', { name: '保存设置' }).click();
  await fit(160);
  assert.equal((await preferences()).widgetSize, 'four');
  assert.equal((await preferences()).wake, '10:00'); assert.equal((await preferences()).sleep, '02:00');
  const startup = ps(['-Command', "Get-ItemPropertyValue 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Run' -Name 'Personal Day'"]);
  assert.equal(startup, `"${exe}" --autostart`);
  pass('separate settings reject invalid schedule and save footprint, schedule, lock and a quoted login startup command');
  assert.equal(await page.locator('#remaining, #schedule-label').count(), 0);
  await page.evaluate(() => {
    window.testOriginalFetch = window.fetch;
    const endpoint = window.__TAURI_INTERNALS__.convertFileSrc('wallpaper_snapshot', 'ipc');
    window.fetch = function(input, ...args) {
      if (String(input) === endpoint && window.testWallpaper) return Promise.resolve(new Response(JSON.stringify(window.testWallpaper), {
        headers: { 'Tauri-Response': 'ok', 'Content-Type': 'application/json' },
      }));
      return window.testOriginalFetch.call(this, input, ...args);
    };
  });
  // Synthetic images exercise native WebView canvas sampling without changing
  // the user's actual wallpaper. Split image checks local rather than global brightness.
  for (const [shade, x, ink] of [[30, 0, 'white'], [128, 0, 'black'], [230, 0, 'black'], ['split', 0, 'white'], ['split', 450, 'black']]) {
    const fixtureImage = await page.evaluate(({ shade, x }) => {
      const canvas = document.createElement('canvas'); canvas.width = 800; canvas.height = 300;
      const ctx = canvas.getContext('2d');
      ctx.fillStyle = shade === 'split' ? '#202020' : `rgb(${shade} ${shade} ${shade})`; ctx.fillRect(0, 0, 800, 300);
      if (shade === 'split') { ctx.fillStyle = '#dddddd'; ctx.fillRect(400, 0, 400, 300); }
      window.testWallpaper = { imageKey: `fixture-${shade}-${x}`, imageData: canvas.toDataURL(), mode: 2, color: '#808080', left: 0, top: 0, width: 800, height: 300, clientX: x, clientY: 0 };
      window.dispatchEvent(new Event('focus'));
      return window.testWallpaper.imageData;
    }, { shade, x });
    await page.waitForFunction(image => document.getElementById('wallpaper-image').style.backgroundImage.includes(image), fixtureImage);
    await page.waitForFunction(ink => document.documentElement.dataset.ink === ink, ink);
    for (const colorScheme of ['light', 'dark']) {
      await page.emulateMedia({ colorScheme });
      const style = await page.locator('#app').evaluate(el => ({ color: getComputedStyle(el).color, bg: getComputedStyle(el).backgroundColor }));
      assert.equal(style.color, ink === 'white' ? 'rgb(255, 255, 255)' : 'rgb(0, 0, 0)');
      assert.equal(style.bg, 'rgba(0, 0, 0, 0)');
    }
  }
  await page.evaluate(() => { window.fetch = window.testOriginalFetch; delete window.testWallpaper; window.dispatchEvent(new Event('focus')); });
  await delay(600);
  pass('untinted wallpaper blur uses white text on dark regions, black on gray/bright; local crop sampling ignores system theme');
  mkdirSync('output/playwright', { recursive: true });
  await page.screenshot({ path: 'output/playwright/windows-widget-four.png' });
  const small = probe();
  await update({ widgetSize: 'six' }); await fit(240);
  const large = probe();
  assert.ok(Math.abs(small.x + small.width - large.x - large.width) <= 1);
  assert.ok(Math.abs(small.y + small.height - large.y - large.height) <= 1);
  pass('changing footprint preserves the bottom and right screen margins while locked');
  await page.screenshot({ path: 'output/playwright/windows-widget-six.png' });
  pass('both compact footprints render without overflow in native WebView2');
  assert.equal(probe().child, true, 'settings and resizing preserve desktop child style');

  probe('close'); await delay(400);
  assert.equal(probe().visible, false); assert.equal(child.exitCode, null);
  const second = spawn(exe, [], { windowsHide: true, stdio: 'ignore' });
  await delay(1300);
  assert.equal(probe().visible, true); assert.equal(second.exitCode, 0);
  assert.equal(probe().child, true);
  pass('close hides without exiting; a second launch shows the same embedded widget');
  probe('move', 30000, 30000); await delay(2400);
  const repaired = probe(); assert.ok(repaired.x < 30000 && repaired.y < 30000);
  pass('embedded off-screen coordinates recover to a visible monitor');
  await stop(); await launch(); await fit(240);
  assert.equal(probe().child, true);
  assert.equal((await preferences()).positionLocked, true);
  assert.equal((await preferences()).wake, '10:00'); assert.equal((await preferences()).sleep, '02:00');
  assert.equal(probe().x, repaired.x); assert.equal(probe().y, repaired.y);
  pass('restart restores desktop embedding, size, schedule, theme, lock and position');
  await update({ desktopMode: false, alwaysOnTop: true });
  const floating = probe();
  assert.equal(floating.child, false); assert.equal(floating.parent, 0);
  assert.equal(floating.topmost, true); assert.equal(floating.visible, true);
  await update({ alwaysOnTop: false });
  assert.equal(probe().topmost, false); assert.equal(probe().visible, true);
  await update({ desktopMode: true, alwaysOnTop: false });
  assert.equal(probe().child, true); assert.equal(probe().visible, true);
  assert.equal(probe().topmost, false);
  pass('switching between desktop and floating modes preserves visibility and applies topmost correctly');
  await update({ startAtLogin: false });
  assert.equal(ps(['-Command', "(Get-Item 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Run').GetValue('Personal Day')"]), '');
  pass('disabling login startup removes this app registration');
  completed = true;
} finally {
  if (desktopShown) { try { probe('desktop'); } catch {} }
  if (page && original && child?.exitCode === null) {
    try {
      await invoke('save_preferences', { preferences: original });
      probe('move', originalPosition.x, originalPosition.y);
      await invoke('hide_window');
    } catch (error) { console.error('Restore failed:', error); }
  }
  await stop();
  writeFileSync(stateFile, originalState);
  // Restore only this application's entry. Encode data instead of interpolating
  // a path into PowerShell code, so spaces and special characters remain literal.
  const encoded = Buffer.from(startupBackup).toString('base64');
  ps(['-Command', `$entry=([Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('${encoded}')) | ConvertFrom-Json); $key='HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Run'; if($null -eq $entry.value){Remove-ItemProperty -LiteralPath $key -Name 'Personal Day' -ErrorAction SilentlyContinue}else{Set-ItemProperty -LiteralPath $key -Name 'Personal Day' -Value $entry.value}; $approved='HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\StartupApproved\\Run'; if($null -eq $entry.approved){Remove-ItemProperty -LiteralPath $approved -Name 'Personal Day' -ErrorAction SilentlyContinue}else{Set-ItemProperty -LiteralPath $approved -Name 'Personal Day' -Value ([byte[]]$entry.approved)}`]);
  mkdirSync('output/playwright', { recursive: true });
  writeFileSync('output/playwright/windows-desktop-smoke.json', JSON.stringify({ version: '0.3.2', date: new Date().toISOString(), completed, passed: results,
    limitations: ['Adaptive text tests use synthetic wallpaper snapshots; Windows theme is emulated', 'No actual logoff/reboot', 'No actual tray menu clicks', 'No Explorer restart', 'No virtual desktop switch', 'No physical monitor unplug', 'No real suspend/resume', 'Installer not exercised'] }, null, 2));
}
