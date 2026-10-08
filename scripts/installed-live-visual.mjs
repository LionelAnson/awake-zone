import {chromium} from 'playwright';
import {spawn,execFileSync} from 'node:child_process';
import {readFileSync,writeFileSync,mkdirSync} from 'node:fs';
import {resolve,join} from 'node:path';
import {setTimeout as delay} from 'node:timers/promises';
import assert from 'node:assert/strict';
const out=resolve('output/live-capture-release/installed-visual');mkdirSync(out,{recursive:true});
const read=p=>JSON.parse(readFileSync(p,'utf8').replace(/^\uFEFF/,''));
const ps=args=>execFileSync('powershell.exe',['-NoProfile',...args],{windowsHide:true,encoding:'utf8'}).trim();
const action=(h,a,...extra)=>JSON.parse(ps(['-File',resolve('scripts/window-probe.ps1'),'-Handle',String(h),'-Action',a,...extra]));
let browser,pat;const result={samples:[]},control=join(out,'pattern.txt'),meta=join(out,'pattern.json');
async function wait(fn){for(let i=0;i<100;i++){try{const v=await fn();if(v)return v;}catch{}await delay(100);}throw Error('Timeout');}
try{
 browser=await chromium.connectOverCDP('http://127.0.0.1:9226');
 const page=browser.contexts().flatMap(c=>c.pages()).find(p=>!p.url().includes('settings='));
 const invoke=(cmd)=>page.evaluate(cmd=>window.__TAURI_INTERNALS__.invoke(cmd),cmd);
 result.veil=await page.evaluate(()=>getComputedStyle(document.body,'::after').backgroundColor);assert.equal(result.veil,'rgba(216, 216, 216, 0.1)');
 const h=(await invoke('platform_info')).windowHandle;
 const r=action(h,'inspect');assert(r.visible&&r.topmost&&!r.child);
 writeFileSync(control,'#101010');
 pat=spawn('powershell.exe',['-NoProfile','-STA','-File',resolve('diagnostics/winappsdk-backdrop/pattern.ps1'),'-ControlFile',control,'-MetadataFile',meta],{windowsHide:true,stdio:'ignore'});
 const ph=(await wait(()=>read(meta))).hwnd;
 // Place the independently generated source around the existing clock, without moving the clock.
 action(ph,'move','-X',String(r.x-100),'-Y',String(r.y-150));action(ph,'focus-test-app');action(h,'top');
 const capture=join(readFileSync('output/glass-window/latest-build.txt','utf8').replace(/^\uFEFF/,'').trim(),'capture.exe');
 for(const pattern of ['#101010','#808080','bw-40-0','bw-40-20']){
  writeFileSync(control,pattern);await wait(()=>read(meta).pattern===pattern);await delay(1400);
  assert.equal(read(meta).foreground,ph);assert(action(h,'inspect').hitInside);
  const s=await invoke('live_glass_status');assert.equal(s.error,0);assert(s.frames>0);
  const label=pattern.replace('#','color-');execFileSync(capture,[join(out,label),String(r.clientX+8),String(r.clientY+8),String(r.clientWidth-16),String(r.clientHeight-16)],{windowsHide:true});
  const c=read(join(out,label+'-capture.json'));assert.equal(c.foregroundBefore,ph);assert.equal(c.foregroundAfter,ph);
  result.samples.push({pattern,status:s,ink:await page.evaluate(()=>document.documentElement.dataset.ink),capture:c});
 }
 result.passed=true;
}catch(e){result.error=String(e);process.exitCode=1;}
finally{await browser?.close();writeFileSync(control,'exit');await delay(400);if(pat?.exitCode===null)pat.kill();writeFileSync(join(out,'result.json'),JSON.stringify(result,null,2));console.log(JSON.stringify({passed:result.passed,error:result.error,samples:result.samples.length}));}
