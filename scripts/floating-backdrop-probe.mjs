// Read the native desktop composition, not a WebView screenshot, to assess blur.
import { chromium } from 'playwright';
import { spawn, execFileSync } from 'node:child_process';
import { resolve, join } from 'node:path';
import { readFileSync, writeFileSync } from 'node:fs';
import { setTimeout as delay } from 'node:timers/promises';
import assert from 'node:assert/strict';
const ps = args => execFileSync('powershell.exe', ['-NoProfile', ...args], { encoding: 'utf8', windowsHide: true }).trim();
if (ps(['-Command', '(Get-Process personal-day -ErrorAction SilentlyContinue | Measure-Object).Count']) !== '0') throw new Error('Close Personal Day first');
const state = join(process.env.APPDATA, 'com.personalday.widget/state.json');
const original = readFileSync(state, 'utf8');
const startup = ps(['-Command', "[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false); (Get-Item 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Run').GetValue('Personal Day')"]);
let browser, testWindow;
const colorFile=resolve('output/playwright/backdrop-test-color.txt');
writeFileSync(colorFile,'stripes');
const child = spawn(resolve('src-tauri/target/release/personal-day.exe'), [], { windowsHide: true, stdio: 'ignore', env: { ...process.env, WEBVIEW2_ADDITIONAL_BROWSER_ARGUMENTS: '--remote-debugging-port=9223' } });
try {
  for (let n = 0; n < 60; n++) { try { browser = await chromium.connectOverCDP('http://127.0.0.1:9223'); break; } catch { await delay(500); } }
  if (!browser) throw new Error('No WebView');
  const page = browser.contexts().flatMap(c=>c.pages()).find(p=>!p.url().includes('settings='));
  await page.locator('#personal-time').filter({hasText:/\d{2}:\d{2}/}).waitFor();
  const invoke = (cmd,args={})=>page.evaluate(({cmd,args})=>window.__TAURI_INTERNALS__.invoke(cmd,args),{cmd,args});
  await invoke('save_preferences', { preferences: { ...await invoke('load_preferences'), desktopMode: true, alwaysOnTop: false } });
  await invoke('save_preferences', { preferences: { ...await invoke('load_preferences'), desktopMode: false, alwaysOnTop: true, positionLocked: true } });
  await page.addStyleTag({content:'#wallpaper-viewport { display:none !important }'});
  console.log('CSS', await page.evaluate(()=>({app:getComputedStyle(document.getElementById('app')).backgroundColor, root:getComputedStyle(document.documentElement).backgroundColor, url:location.href})));
  const handle = (await invoke('platform_info')).windowHandle;
  if(process.env.NO_REDIRECTION) ps(['-ExecutionPolicy','Bypass','-File',resolve('scripts/probe-backdrop.ps1'),'-Handle',String(handle),'-Effect','noredirect']);
  if(process.env.TRANSPARENT_HOST) ps(['-ExecutionPolicy','Bypass','-File',resolve('scripts/probe-backdrop.ps1'),'-Handle',String(handle),'-Effect','transparent']);
  const probe = (h,action,x=0,y=0)=>JSON.parse(ps(['-ExecutionPolicy','Bypass','-File',resolve('scripts/window-probe.ps1'),'-Handle',String(h),'-Action',action,'-X',String(x),'-Y',String(y)]));
  const rect = probe(handle,'inspect');
  console.log('FLOAT',rect);
  testWindow=spawn('powershell.exe',['-NoProfile','-ExecutionPolicy','Bypass','-File',resolve('scripts/backdrop-test-window.ps1'),'-X',String(rect.x-40),'-Y',String(rect.y-60),'-ColorFile',colorFile],{windowsHide:true,stdio:'ignore'});
  await delay(2000);
  await page.screenshot({path:'output/playwright/floating-probe-webview.png'});
  probe(handle,'top');
  await delay(800);
  for (const effect of (process.env.LIVE_ONLY ? ['none'] : ['none','reset','transparent','blur','acrylic','dwm','active'])) {
    if(effect!=='none') console.log(effect,ps(['-ExecutionPolicy','Bypass','-File',resolve('scripts/probe-backdrop.ps1'),'-Handle',String(handle),'-Effect',effect]));
    await delay(600);
    console.log(effect,ps(['-ExecutionPolicy','Bypass','-File',resolve('scripts/capture-widget.ps1'),'-WidgetProcessId',String(child.pid),'-Path',resolve(`output/playwright/floating-probe-${effect}.png`)]));
    if(effect==='dwm') console.log(execFileSync('python',['-c',`from PIL import ImageGrab; ImageGrab.grab(bbox=(${rect.x},${rect.y},${rect.x+rect.width},${rect.y+rect.height}),include_layered_windows=True).save('output/playwright/floating-probe-pil.png')`],{windowsHide:true,encoding:'utf8'}));
  }
  if(process.env.LIVE_ONLY){
    for(const [color,ink] of [['#202020','white'],['#e0e0e0','black']]){
      writeFileSync(colorFile,color);await delay(1800);
      const brightness=await invoke('backdrop_brightness');
      console.log('background',color,'brightness',brightness,'ink',await page.evaluate(()=>document.documentElement.dataset.ink));
      assert.ok(color==='#202020'?brightness<.08:brightness>.5,'window must show the actual application behind it');
      assert.equal(await page.evaluate(()=>document.documentElement.dataset.ink),ink);
      ps(['-ExecutionPolicy','Bypass','-File',resolve('scripts/capture-widget.ps1'),'-WidgetProcessId',String(child.pid),'-Path',resolve(`output/playwright/live-backdrop-${ink}.png`)]);
    }
    console.log('PASS: live background and adaptive text respond to an independent application window');
  }
  await invoke('hide_window'); await delay(500);
  console.log('without widget',ps(['-ExecutionPolicy','Bypass','-File',resolve('scripts/capture-widget.ps1'),'-WidgetProcessId',String(child.pid),'-Path',resolve('output/playwright/floating-probe-underneath.png')]));
} finally {
  testWindow?.kill(); await browser?.close(); child.kill(); await delay(800); writeFileSync(state,original);
  const encoded=Buffer.from(startup).toString('base64');
  ps(['-Command',`$v=[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('${encoded}')); if($v){Set-ItemProperty 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Run' -Name 'Personal Day' -Value $v}else{Remove-ItemProperty 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Run' -Name 'Personal Day' -ErrorAction SilentlyContinue}`]);
}
