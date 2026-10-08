import {chromium} from 'playwright';
import {execFileSync} from 'node:child_process';
import {readFileSync,writeFileSync,mkdirSync} from 'node:fs';
import {resolve,join} from 'node:path';
import {setTimeout as delay} from 'node:timers/promises';
import assert from 'node:assert/strict';
const releaseVersion=JSON.parse(readFileSync('package.json','utf8')).version.replaceAll('.','');
const out=resolve('output/hotkey-reliability/installed-check-'+releaseVersion);mkdirSync(out,{recursive:true});
const ps=args=>execFileSync('powershell.exe',['-NoProfile',...args],{windowsHide:true,encoding:'utf8'}).trim();
const action=(h,a,...extra)=>JSON.parse(ps(['-File',resolve('scripts/window-probe.ps1'),'-Handle',String(h),'-Action',a,...extra]));
const result={checks:[]};let browser,page,h;
const invoke=(cmd,args={})=>page.evaluate(({cmd,args})=>window.__TAURI_INTERNALS__.invoke(cmd,args),{cmd,args});
async function wait(fn){for(let i=0;i<100;i++){try{const v=await fn();if(v)return v;}catch{}await delay(100);}throw Error('Timeout');}
try{
 browser=await wait(()=>chromium.connectOverCDP('http://127.0.0.1:9226'));
 page=await wait(async()=>{for(const p of browser.contexts().flatMap(c=>c.pages()))if(await p.evaluate(()=>document.documentElement.dataset.page==='widget'))return p;});
 await page.locator('#personal-time').filter({hasText:/\d{2}:\d{2}/}).waitFor();
 result.shortcutBefore=await invoke('shortcut_status');assert.equal(result.shortcutBefore.registered,true);
 h=(await invoke('platform_info')).windowHandle;result.hwnd=h;result.before=await invoke('load_preferences');result.initial=action(h,'inspect');
 assert.equal(await page.locator('#error').isVisible(),false);
 if(!result.before.desktopMode){await wait(async()=>(await invoke('live_glass_status')).frames>5);result.checks.push('cold-floating-start-live');action(h,'hotkey');await wait(async()=>(await invoke('load_preferences')).desktopMode);}
 result.desktopAnchor=action(h,'inspect');
 action(h,'hotkey');await wait(async()=>!(await invoke('load_preferences')).desktopMode);
 await wait(async()=>(await invoke('live_glass_status')).frames>5);assert.equal((await invoke('live_glass_status')).error,0);const glass=await invoke('live_glass_status');if('borderlessAccess' in glass){assert.equal(glass.borderlessAccess,4);assert.equal(glass.borderRequired,0);assert.equal(glass.borderError,0);result.borderless=glass;}assert(action(h,'inspect').topmost);result.checks.push('actual-Win-Alt-X-starts-live-glass');
 action(h,'close');await wait(()=>!action(h,'inspect').visible);await delay(400);let a=await invoke('live_glass_status');await delay(400);assert.equal((await invoke('live_glass_status')).frames,a.frames);result.checks.push('close-hides-and-pauses');
 action(h,'hotkey');await wait(()=>action(h,'inspect').visible&&action(h,'inspect').topmost);await wait(async()=>(await invoke('live_glass_status')).frames>a.frames+5);const resumed=await invoke('live_glass_status');if('borderRequired' in resumed)assert.equal(resumed.borderRequired,0);result.checks.push('hidden-floating-restored-by-actual-shortcut');
 action(h,'hotkey');await wait(async()=>(await invoke('load_preferences')).desktopMode);const returned=action(h,'inspect');assert(returned.child&&!returned.topmost);assert.equal(returned.x,result.desktopAnchor.x);assert.equal(returned.y,result.desktopAnchor.y);result.checks.push('actual-shortcut-restores-desktop-anchor');
 await invoke('open_settings');await delay(400);const settings=browser.contexts().flatMap(c=>c.pages()).find(p=>p.url().includes('settings='));assert(settings);assert.equal(await settings.locator('#wake').inputValue(),result.before.wake);assert.equal(await settings.locator('.help').filter({hasText:'Win + Alt + X'}).count(),1);await settings.evaluate(()=>window.__TAURI_INTERNALS__.invoke('close_settings'));result.checks.push('settings-opens-with-existing-schedule');
 const holdBefore=await invoke('shortcut_status');
 execFileSync('python',[resolve('scripts/send-clock-shortcut.py'),'--hold-ms','1500'],{windowsHide:true});
 await wait(async()=>(await invoke('shortcut_status')).completed===holdBefore.completed+1);
 assert.equal((await invoke('shortcut_status')).received,holdBefore.received+1);assert(!(await invoke('load_preferences')).desktopMode);
 result.checks.push('actual-shortcut-held-1500ms-one-transition');
 action(h,'minimize');await delay(2500);assert(action(h,'inspect').minimized);
 const minimizedState=JSON.parse(readFileSync(join(process.env.APPDATA,'com.personalday.widget/state.json'),'utf8'));assert(minimizedState.position[0]>-30000&&minimizedState.position[1]>-30000);
 action(h,'hotkey');await wait(()=>{const s=action(h,'inspect');return s.visible&&s.topmost&&!s.minimized;});
 await wait(async()=>(await invoke('live_glass_status')).frames>5);
 action(h,'hotkey');await wait(async()=>(await invoke('load_preferences')).desktopMode);
 result.checks.push('minimized-position-preserved-and-shortcut-restores-floating');
 for(const held of [false,true]){
  const before=await invoke('shortcut_status');
  const sent=JSON.parse(execFileSync('python',[resolve('scripts/send-clock-shortcut.py'),'--count','20',...(held?['--hold-modifiers']:[])],{encoding:'utf8',windowsHide:true}));
  await wait(async()=>(await invoke('shortcut_status')).completed===before.completed+20);
  const after=await invoke('shortcut_status');assert.equal(after.received,before.received+20);assert.equal(after.failed,0);assert((await invoke('load_preferences')).desktopMode);
  result.checks.push(held?'actual-shortcut-20-held-modifier-presses':'actual-shortcut-20-fast-presses');
 }
 result.shortcutAfter=await invoke('shortcut_status');
 if(!result.before.desktopMode){action(h,'hotkey');await wait(async()=>!(await invoke('load_preferences')).desktopMode);action(h,'move','-X',String(result.initial.x),'-Y',String(result.initial.y));await delay(2200);}
 result.after=await invoke('load_preferences');assert.deepEqual(result.after,result.before);assert.match(await page.locator('#percent').innerText(),/^\d+\.\d{2}%$/);result.checks.push('preferences-and-two-decimals-preserved');result.passed=true;
}catch(e){result.error=String(e);process.exitCode=1;}
finally{await browser?.close();writeFileSync(join(out,'result.json'),JSON.stringify(result,null,2));console.log(JSON.stringify(result,null,2));}
