// Captures real desktop composition. These images require visual inspection;
// receiving a successful API result alone does not verify backdrop blur.
import { chromium } from 'playwright';
import { spawn, execFileSync } from 'node:child_process';
import { resolve, join } from 'node:path';
import { readFileSync, writeFileSync } from 'node:fs';
import { setTimeout as delay } from 'node:timers/promises';
const ps = args => execFileSync('powershell.exe', ['-NoProfile', ...args], { encoding:'utf8', windowsHide:true });
const state = join(process.env.APPDATA, 'com.personalday.widget/state.json');
const original = readFileSync(state, 'utf8');
const child = spawn(resolve('src-tauri/target/release/personal-day.exe'), [], { windowsHide:true, stdio:'ignore', env:{ ...process.env, WEBVIEW2_ADDITIONAL_BROWSER_ARGUMENTS:'--remote-debugging-port=9223' } });
let browser, desktop = false, handle;
const probe = action => ps(['-ExecutionPolicy','Bypass','-File',resolve('scripts/window-probe.ps1'),'-Action',action,'-Handle',String(handle)]);
const capture = name => ps(['-ExecutionPolicy','Bypass','-File',resolve('scripts/capture-widget.ps1'),'-WidgetProcessId',String(child.pid),'-Path',resolve(`output/playwright/glass-native-${name}.png`)]);
try {
  for(let n=0;n<60;n++) { try { browser=await chromium.connectOverCDP('http://127.0.0.1:9223');break; } catch { await delay(500); } }
  if(!browser) throw new Error('No WebView');
  const page=browser.contexts().flatMap(c=>c.pages()).find(p=>!p.url().includes('settings='));
  await page.locator('#personal-time').filter({hasText:/\d{2}:\d{2}/}).waitFor();
  await page.locator('#wallpaper-viewport').waitFor({ state:'visible' });
  handle=await page.evaluate(async()=> (await window.__TAURI_INTERNALS__.invoke('platform_info')).windowHandle);
  probe('desktop'); desktop=true;
  await delay(800);
  for(const colorScheme of ['light','dark']) {
    await page.emulateMedia({colorScheme}); await delay(700);
    console.log(colorScheme, capture(colorScheme));
    console.log(await page.locator('#app').evaluate(el=>({color:getComputedStyle(el).color,background:getComputedStyle(el).backgroundColor,error:document.getElementById('error').textContent})));
  }
  await page.evaluate(()=>window.__TAURI_INTERNALS__.invoke('hide_window'));
  await delay(400);
  console.log('background',capture('background'));
} finally {
  if(desktop) probe('desktop');
  if(browser) await browser.close();
  child.kill(); await delay(800);
  writeFileSync(state,original);
}
