// Isolated Tauri + real desktop capture. Only our own test pattern region is saved.
import {chromium} from 'playwright';
import {spawn,execFileSync} from 'node:child_process';
import {readFileSync,writeFileSync,mkdirSync} from 'node:fs';
import {resolve,join} from 'node:path';
import {setTimeout as delay} from 'node:timers/promises';
import assert from 'node:assert/strict';
const base=resolve('output/live-capture');mkdirSync(base,{recursive:true});
const out=join(base,new Date().toISOString().replace(/[:.]/g,'-'));mkdirSync(out);
const build=JSON.parse(readFileSync(process.env.PD_TEST_BUILD||'output/hotkey-toggle/latest-tauri-build.json','utf8'));
const captureBuild=readFileSync('output/glass-window/latest-build.txt','utf8').replace(/^\uFEFF/,'').trim();
const control=join(out,'pattern.txt'),meta=join(out,'pattern.json');
const read=p=>JSON.parse(readFileSync(p,'utf8').replace(/^\uFEFF/,''));
const ps=args=>execFileSync('powershell.exe',['-NoProfile',...args],{encoding:'utf8',windowsHide:true}).trim();
const action=(h,a,...extra)=>JSON.parse(ps(['-File',resolve('scripts/window-probe.ps1'),'-Handle',String(h),'-Action',a,...extra]));
writeFileSync(join(out,'state.json'),JSON.stringify({preferences:{wake:'08:00',sleep:'00:00',theme:'system',desktopMode:false,alwaysOnTop:true,positionLocked:false,widgetSize:'six',startAtLogin:false},position:[180,240]}));
writeFileSync(control,'#101010');
let pat,child,browser,page;const result={build,rounds:[]};
const invoke=(cmd,args={})=>page.evaluate(({cmd,args})=>window.__TAURI_INTERNALS__.invoke(cmd,args),{cmd,args});
async function wait(fn){for(let i=0;i<100;i++){try{const v=await fn();if(v)return v;}catch{}await delay(100);}throw Error('Timed out');}
try{
 pat=spawn('powershell.exe',['-NoProfile','-STA','-File',resolve('diagnostics/winappsdk-backdrop/pattern.ps1'),'-ControlFile',control,'-MetadataFile',meta],{windowsHide:true,stdio:'ignore'});
 const ph=(await wait(()=>read(meta))).hwnd;action(ph,'top');action(ph,'focus-test-app');
 child=spawn(build.exe,[],{windowsHide:true,stdio:'ignore',env:{...process.env,PD_PROBE_ROOT:out,WEBVIEW2_ADDITIONAL_BROWSER_ARGUMENTS:'--remote-debugging-port=9225'}});
 browser=await wait(()=>chromium.connectOverCDP('http://127.0.0.1:9225'));
 page=await wait(async()=>{for(const p of browser.contexts().flatMap(c=>c.pages()))if(await p.evaluate(()=>document.documentElement.dataset.page==='widget'))return p;});
 await page.locator('#personal-time').filter({hasText:/\d{2}:\d{2}/}).waitFor();
 if(process.env.PD_TEST_CURRENT_CSS==='1'){const css=readFileSync('src/style.css','utf8');await page.addStyleTag({content:css});result.cssOverride=css;}
 const h=(await invoke('platform_info')).windowHandle;action(h,'move','-X','180','-Y','240');action(h,'top');
 await wait(async()=>{const s=await invoke('live_glass_status');if(s.error)throw Error(JSON.stringify(s));return s.frames>5;});
 for(const pattern of ['#101010','#808080','#f0f0f0','#ff0000','#00ff00','#0000ff','bw-8-0','bw-40-0','bw-40-20','rgb-80-0','rgb-80-40']){
  if(pattern==='bw-8-0'){await invoke('return_to_desktop');await delay(200);await invoke('toggle_window_mode');action(h,'move','-X','180','-Y','240');result.desktopRoundtrip=true;}
  writeFileSync(control,pattern);await wait(()=>read(meta).pattern===pattern);await delay(1200);
  const status=await invoke('live_glass_status');assert.equal(status.error,0);assert.equal(await page.locator('#error').isVisible(),false,await page.locator('#error').textContent());
  let ink=await page.evaluate(()=>document.documentElement.dataset.ink);
  const expectedInk=pattern==='#101010'?'white':(['#808080','#f0f0f0'].includes(pattern)?'black':null);
  if(expectedInk)await wait(async()=>await page.evaluate(()=>document.documentElement.dataset.ink)===expectedInk);
  ink=await page.evaluate(()=>document.documentElement.dataset.ink);
  console.log('sample',pattern,JSON.stringify(status),ink);
  const position=action(h,'inspect');assert(position.hitInside);assert.equal(read(meta).foreground,ph);
  assert(position.x>=80&&position.y>=160&&position.x+position.width<640&&position.y+position.height<580);
  // Hide only widget lettering to measure the native background; settings page is untouched.
  await page.locator('#app').evaluate(el=>el.style.visibility='hidden');await delay(200);
  const label=pattern.replace('#','color-'),prefix=join(out,label);
  execFileSync(join(captureBuild,'capture.exe'),[prefix,String(position.x+12),String(position.y+12),String(position.width-24),String(position.height-24)],{windowsHide:true});
  const desktop=read(prefix+'-capture.json');assert.equal(desktop.foregroundBefore,ph);assert.equal(desktop.foregroundAfter,ph);
  await page.locator('#app').evaluate(el=>el.style.visibility='');
  result.rounds.push({pattern,status,ink,position,desktop,prefix});console.log(pattern,status.frames,ink);
 }
 // Capture the real composed clock on our synthetic background, not a browser screenshot.
 await delay(300);const p=action(h,'inspect');assert.equal(read(meta).foreground,ph);
 execFileSync(join(captureBuild,'capture.exe'),[join(out,'clock'),String(p.x+8),String(p.y+8),String(p.width-16),String(p.height-16)],{windowsHide:true});
 await invoke('hide_window');await delay(500);let a=await invoke('live_glass_status');await delay(500);let b=await invoke('live_glass_status');assert.equal(a.frames,b.frames);result.hiddenCapturePaused=true;
 result.completed=true;
}catch(e){result.error=String(e);process.exitCode=1;}
finally{
 if(page)try{await invoke('probe_exit');}catch{}
 await browser?.close();await delay(300);if(child?.exitCode===null)child.kill();
 writeFileSync(control,'exit');await delay(300);if(pat?.exitCode===null)pat.kill();
 writeFileSync(join(out,'result.json'),JSON.stringify(result,null,2));writeFileSync(join(base,'latest.txt'),out);console.log('RESULT',out,result.completed,result.error??'');
}
