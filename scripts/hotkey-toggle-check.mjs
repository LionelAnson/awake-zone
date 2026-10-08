import { chromium } from 'playwright';
import { spawn, execFileSync } from 'node:child_process';
import { readFileSync,writeFileSync,mkdirSync,copyFileSync } from 'node:fs';
import { resolve,join } from 'node:path';
import { createHash } from 'node:crypto';
import { setTimeout as delay } from 'node:timers/promises';
import assert from 'node:assert/strict';
const base=resolve('output/hotkey-toggle');
const build=JSON.parse(readFileSync(join(base,'latest-tauri-build.json'),'utf8'));
const out=join(base,new Date().toISOString().replace(/[:.]/g,'-')+'-native');mkdirSync(out,{recursive:true});
const hash=p=>createHash('sha256').update(readFileSync(p)).digest('hex');
const ps=args=>execFileSync('powershell.exe',['-NoProfile',...args],{encoding:'utf8',windowsHide:true}).trim();
const windowAction=(h,action,...extra)=>JSON.parse(ps(['-ExecutionPolicy','Bypass','-File',resolve('scripts/window-probe.ps1'),'-Handle',String(h),'-Action',action,...extra]));
const real=join(process.env.APPDATA,'com.personalday.widget/state.json');
const startup=()=>ps(['-Command',"(Get-Item 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Run').GetValue('Personal Day')"]);
const before={state:hash(real),startup:startup(),installed:hash('E:/\u5e94\u7528/Personal Day/personal-day.exe')};
const anchor={monitor:'\\\\.\\DISPLAY1',right:24,bottom:24};
writeFileSync(join(out,'state.json'),JSON.stringify({preferences:{wake:'08:00',sleep:'00:00',theme:'system',desktopMode:true,alwaysOnTop:false,positionLocked:true,widgetSize:'six',startAtLogin:false},position:[1560,800],anchor},null,2));
copyFileSync(build.exe,join(out,'personal-day.exe'));
const result={build:build.manifest,exeHash:hash(build.exe),before,testShortcut:'Ctrl+Shift+Alt+Z (isolated build only; installed application uses Win+Alt+X)',checks:[]};
let child,browser,page,background;const control=join(out,'pattern.txt'),meta=join(out,'pattern-window.json');
const invoke=(cmd,args={})=>page.evaluate(({cmd,args})=>window.__TAURI_INTERNALS__.invoke(cmd,args),{cmd,args});
const check=(name,data)=>{result.checks.push({name,...data});console.log(name,JSON.stringify(data));};
async function snapshot(h){await delay(350);return {native:windowAction(h,'inspect'),preferences:await invoke('load_preferences'),state:JSON.parse(readFileSync(join(out,'state.json'),'utf8'))};}
function unchangedAnchor(s){assert.deepEqual(s.state.anchor,anchor);}
try {
 child=spawn(join(out,'personal-day.exe'),[],{windowsHide:true,env:{...process.env,PD_PROBE_ROOT:out,PD_PROBE_HOTKEY:'alternate',WEBVIEW2_ADDITIONAL_BROWSER_ARGUMENTS:'--remote-debugging-port=9224'},stdio:'ignore'});
 for(let i=0;i<60;i++){try{browser=await chromium.connectOverCDP('http://127.0.0.1:9224');break;}catch{await delay(500);}}
 assert(browser,'WebView unavailable');
 for(let attempt=0;attempt<60&&!page;attempt++) {
   for(const candidate of browser.contexts().flatMap(c=>c.pages())) {
     try { if(await candidate.evaluate(()=>document.documentElement.dataset.page==='widget')) {page=candidate;break;} } catch {}
   }
   if(!page) await delay(100);
 }
 assert(page,'Main widget document unavailable');
 await page.locator('#personal-time').filter({hasText:/\d{2}:\d{2}/}).waitFor();await delay(700);
 const platform=await invoke('platform_info');assert.equal(platform.nativeBackdrop,true);const h=platform.windowHandle;
 assert.equal(await page.locator('#error').isVisible(),false);
 assert.equal((await invoke('shortcut_status')).registered,true);
 const initial=await snapshot(h);assert(initial.native.child&&!initial.native.topmost);assert(initial.preferences.positionLocked);unchangedAnchor(initial);check('startup-desktop',initial);
 writeFileSync(control,'#808080');background=spawn('powershell.exe',['-NoProfile','-ExecutionPolicy','Bypass','-File',resolve('scripts/backdrop-pattern-window.ps1'),'-ControlFile',control,'-MetadataFile',meta],{windowsHide:true,stdio:'ignore'});await delay(1600);
 const bg=JSON.parse(readFileSync(meta,'utf8').replace(/^\uFEFF/,''));windowAction(bg.hwnd,'focus-test-app');
 const key=()=>windowAction(h,'hotkey','-AlternateHotkey','-HoldMs','80');
 key();let s=await snapshot(h);assert(!s.native.child&&s.native.topmost&&!s.preferences.positionLocked);unchangedAnchor(s);check('shortcut-one-transition',s);
 await delay(300);let bgNow=JSON.parse(readFileSync(meta,'utf8').replace(/^\uFEFF/,''));check('foreground-observation',{foreground:bgNow.foreground,expected:bg.hwnd,preserved:bgNow.foreground===bg.hwnd});
 // Native mouse drag, then wait for persistence before returning to desktop.
 windowAction(h,'move','-X',String(initial.native.x-300),'-Y',String(initial.native.y-180));await delay(2300);s=await snapshot(h);assert(s.native.x<initial.native.x-200);unchangedAnchor(s);check('floating-reposition-keeps-desktop-anchor',s);
 key();s=await snapshot(h);assert(s.native.child&&!s.native.topmost&&s.preferences.positionLocked);assert.equal(s.native.x,initial.native.x);assert.equal(s.native.y,initial.native.y);unchangedAnchor(s);check('return-to-original-desktop-position',s);
 for(let i=0;i<2;i++){key();s=await snapshot(h);assert(s.native.topmost&&!s.native.child);key();s=await snapshot(h);assert(s.native.child&&!s.native.topmost);assert.equal(s.native.x,initial.native.x);assert.equal(s.native.y,initial.native.y);}check('repeated-roundtrips',{passed:true});
 await invoke('hide_window');assert.equal(windowAction(h,'inspect').visible,false);key();s=await snapshot(h);assert(s.native.visible&&s.native.topmost);check('hidden-shortcut-restores-floating',s);
 await invoke('hide_window');key();s=await snapshot(h);assert(s.native.visible&&s.native.topmost&&!s.native.child);check('hidden-floating-shortcut-restores-floating',s);
 await page.mouse.move(-20,-20);assert.equal(await page.locator('#return-desktop').evaluate(el=>getComputedStyle(el).opacity),'0');
 await page.keyboard.press('Tab');await page.locator('#return-desktop').focus();assert.equal(await page.locator('#return-desktop').evaluate(el=>getComputedStyle(el).opacity),'1');
 await page.mouse.move(35,65);await delay(100);await page.locator('#return-desktop').hover();await page.screenshot({path:join(out,'floating-return-button.png')});await page.locator('#return-desktop').click();s=await snapshot(h);assert(s.native.child&&!s.native.topmost&&s.preferences.positionLocked);assert.equal(s.native.x,initial.native.x);assert.equal(s.native.y,initial.native.y);assert.equal(await page.locator('#return-desktop').isVisible(),false);unchangedAnchor(s);check('hover-button-returns-and-locks',s);
 await invoke('return_to_desktop');s=await snapshot(h);assert(s.native.child&&!s.native.topmost);check('return-button-command-idempotent',{passed:true});
 windowAction(h,'desktop');try{s=await snapshot(h);assert(s.native.visible&&s.native.child&&s.native.hitInside);check('win-d-desktop-visible',s);}finally{windowAction(h,'desktop');}
 await invoke('open_settings');await delay(350);const settings=browser.contexts().flatMap(c=>c.pages()).find(p=>p.url().includes('settings='));assert(settings);await settings.locator('#settings-panel').waitFor({state:'visible'});assert.equal(await settings.locator('#wake').inputValue(),'08:00');await settings.evaluate(()=>window.__TAURI_INTERNALS__.invoke('close_settings'));check('settings-open-close',{passed:true});
 windowAction(h,'close');await delay(250);assert(!windowAction(h,'inspect').visible);await invoke('toggle_window_mode');s=await snapshot(h);assert(s.native.topmost&&s.native.visible);check('close-hides-command-shows',s);
 assert.equal(await page.locator('#wallpaper-viewport').isVisible(),false);assert.equal(await page.evaluate(()=>document.documentElement.dataset.mode),'floating');assert.equal((await invoke('live_glass_status')).error,0);assert.match(await page.locator('#percent').innerText(),/^\d+\.\d{2}%$/);check('live-backdrop-and-two-decimal-clock',{passed:true});
 const prefs=await invoke('load_preferences');await invoke('save_preferences',{preferences:{...prefs,widgetSize:'four'}});await delay(500);await page.mouse.move(35,65);await delay(100);await page.locator('#return-desktop').hover();const layout=await page.evaluate(()=>({width:innerWidth,scrollWidth:document.documentElement.scrollWidth,button:document.getElementById('return-desktop').getBoundingClientRect().right}));assert(layout.scrollWidth<=layout.width&&layout.button<=layout.width);await page.screenshot({path:join(out,'four-return-button.png')});await page.locator('#return-desktop').click();s=await snapshot(h);assert(s.native.child&&s.preferences.positionLocked);unchangedAnchor(s);check('four-icon-button-layout-and-return',{layout});
 // Count every native registration event and completed transition, including bursts
 // while capture is being rebuilt. Memory diagnostics contain this chord only.
 const statusBefore=await invoke('shortcut_status');
 for(const held of [false,true]) {
   const injection=JSON.parse(execFileSync('python',[resolve('scripts/send-clock-shortcut.py'),'--alternate','--count','20','--hold-ms','40','--gap-ms','60',...(held?['--hold-modifiers']:[])],{encoding:'utf8',windowsHide:true}));
   const expected=statusBefore.received+(held?40:20);
   let stats;
   for(let i=0;i<150;i++){stats=await invoke('shortcut_status');if(stats.completed>=expected)break;await delay(100);}
   assert.equal(stats.received,expected);assert.equal(stats.completed,expected);assert.equal(stats.failed,0);
   s=await snapshot(h);assert(s.native.child&&!s.native.topmost);unchangedAnchor(s);
   check(held?'held-modifier-20-presses':'rapid-20-presses',{injection,stats,native:s.native});
 }
 key();await delay(300);windowAction(h,'minimize');await delay(2500);assert.equal(windowAction(h,'inspect').minimized,true);
 key();s=await snapshot(h);assert(s.native.visible&&s.native.topmost&&!s.native.minimized&&!s.native.child);assert(s.state.position[0]>=0&&s.state.position[1]>=0);check('minimized-shortcut-restores-floating',s);
 key();s=await snapshot(h);assert(s.native.child&&!s.native.topmost);unchangedAnchor(s);
 result.shortcutStatus=await invoke('shortcut_status');assert.equal(result.shortcutStatus.received,result.shortcutStatus.completed);assert.equal(result.shortcutStatus.failed,0);
 result.completed=true;
}catch(error){result.error=String(error);result.stack=error.stack;try{result.failureStatus=await invoke('shortcut_status');result.failurePreferences=await invoke('load_preferences');result.failureNative=windowAction((await invoke('platform_info')).windowHandle,'inspect');}catch{}process.exitCode=1;}
finally {
 if(page)try{await invoke('probe_exit');}catch{}
 await browser?.close();await delay(700);if(child?.exitCode===null){child.kill();result.forcedCleanup=true;}
 if(background){writeFileSync(control,'exit');await delay(400);if(background.exitCode===null)background.kill();}
 result.after={state:hash(real),startup:startup(),installed:hash('E:/\u5e94\u7528/Personal Day/personal-day.exe')};
 result.productionUnchanged=JSON.stringify(result.before)===JSON.stringify(result.after);
 writeFileSync(join(out,'result.json'),JSON.stringify(result,null,2));writeFileSync(join(base,'latest-native.json'),JSON.stringify({...result,out},null,2));console.log('RESULT',out,result.completed,result.error??'');
}
