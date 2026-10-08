// Isolated native composition experiment. Never reads/writes production preferences.
import { chromium } from 'playwright';
import { spawn, execFileSync } from 'node:child_process';
import { readFileSync, writeFileSync, mkdirSync, copyFileSync, existsSync } from 'node:fs';
import { resolve, join } from 'node:path';
import { createHash } from 'node:crypto';
import { setTimeout as delay } from 'node:timers/promises';
const root=resolve('output/playwright/backdrop-runs');
const mode=process.argv[2]??'clear';
const route=process.argv[3]??'cold';
const runId=new Date().toISOString().replace(/[:.]/g,'-')+`-${mode}-${route}`;
const out=join(root,runId);mkdirSync(out,{recursive:true});
const built=existsSync(join(root,'latest-tauri-build.json'))?JSON.parse(readFileSync(join(root,'latest-tauri-build.json'),'utf8')):null;
const exe=resolve(process.env.PD_PROBE_EXE??built?.exe??join(root,'build-target/release/personal-day.exe'));
const hash=p=>createHash('sha256').update(readFileSync(p)).digest('hex');
const ps=args=>execFileSync('powershell.exe',['-NoProfile',...args],{encoding:'utf8',windowsHide:true}).trim();
const realState=join(process.env.APPDATA,'com.personalday.widget/state.json');
const startup=()=>ps(['-Command',"[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false); (Get-Item 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Run').GetValue('Personal Day')"]);
const before={stateHash:hash(realState),startup:startup()};
const manifest={runId,mode,route,command:`node scripts/backdrop-ab.mjs ${mode} ${route}`,exe,exeSha256:hash(exe),before,observations:[],config:JSON.parse(readFileSync(join(root,'probe-config.json'),'utf8')),sourceHashes:{}};
if(built){manifest.buildManifest=built.manifest;copyFileSync(built.manifest,join(out,'build-manifest.json'));manifest.config=JSON.parse(readFileSync(join(resolve(built.manifest,'..'),'effective-config.json'),'utf8'));}
for(const p of ['src-tauri/Cargo.lock','package-lock.json','src-tauri/src/backdrop.rs','src-tauri/src/lib.rs','src-tauri/tauri.windows.conf.json','.cargo/config.toml']) {manifest.sourceHashes[p]=hash(p);copyFileSync(p,join(out,p.replaceAll('/','_')));}
writeFileSync(join(out,'effective-config.json'),JSON.stringify(manifest.config,null,2));
copyFileSync(exe,join(out,'personal-day.exe'));
writeFileSync(join(out,'state.json'),JSON.stringify({preferences:{wake:'08:00',sleep:'00:00',theme:'system',desktopMode:false,alwaysOnTop:true,positionLocked:false,widgetSize:'six',startAtLogin:false},position:[140,240],anchor:{monitor:'\\\\.\\DISPLAY1',right:24,bottom:24}},null,2));
let page,browser,background,child;
const log=s=>{console.log(s);};
const invoke=(cmd,args={})=>page.evaluate(({cmd,args})=>window.__TAURI_INTERNALS__.invoke(cmd,args),{cmd,args});
const control=join(out,'pattern.txt'); const meta=join(out,'pattern-window.json');writeFileSync(control,'#e0e0e0');
const env={...process.env,PD_PROBE_ROOT:out,PD_PROBE_HOST:mode,WEBVIEW2_ADDITIONAL_BROWSER_ARGUMENTS:'--remote-debugging-port=9224'};
try {
 child=spawn(join(out,'personal-day.exe'),[],{env,windowsHide:true,stdio:['ignore','pipe','pipe']});
 child.stdout.on('data',d=>writeFileSync(join(out,'stdout.log'),d,{flag:'a'}));child.stderr.on('data',d=>writeFileSync(join(out,'stderr.log'),d,{flag:'a'}));
 for(let n=0;n<60;n++){try{browser=await chromium.connectOverCDP('http://127.0.0.1:9224');break;}catch{await delay(500);}}
 if(!browser)throw Error('No isolated WebView on port 9224');
 page=browser.contexts().flatMap(c=>c.pages()).find(p=>!p.url().includes('settings='));
 await page.locator('#personal-time').filter({hasText:/\d{2}:\d{2}/}).waitFor();
 await invoke('load_preferences');
 await delay(300);
 manifest.bootError=await page.locator('#error').isVisible()?await page.locator('#error').innerText():null;
 if(manifest.bootError)throw Error('Startup UI error: '+manifest.bootError);
 manifest.dom=await page.evaluate(()=>({dpr:devicePixelRatio,styles:['html','body','#app'].map(s=>[s,getComputedStyle(document.querySelector(s)).backgroundColor])}));
 if(route==='roundtrip'){
  const p=await invoke('load_preferences');await invoke('save_preferences',{preferences:{...p,desktopMode:true,alwaysOnTop:false}});await delay(300);
  await invoke('save_preferences',{preferences:{...p,desktopMode:false,alwaysOnTop:true}});
 }
 const h=(await invoke('platform_info')).windowHandle;
 ps(['-ExecutionPolicy','Bypass','-File',resolve('scripts/window-probe.ps1'),'-Handle',String(h),'-Action','move','-X','140','-Y','240']);
 background=spawn('powershell.exe',['-NoProfile','-ExecutionPolicy','Bypass','-File',resolve('scripts/backdrop-pattern-window.ps1'),'-ControlFile',control,'-MetadataFile',meta],{windowsHide:true,stdio:['ignore','pipe','pipe']});
 background.stderr.on('data',d=>writeFileSync(join(out,'background-stderr.log'),d,{flag:'a'}));
 await delay(1800);
 const bgHandle=JSON.parse(readFileSync(meta,'utf8').replace(/^\uFEFF/,'')).hwnd;
 ps(['-ExecutionPolicy','Bypass','-File',resolve('scripts/window-probe.ps1'),'-Handle',String(bgHandle),'-Action','focus-test-app']);
 manifest.backgroundRect=JSON.parse(ps(['-ExecutionPolicy','Bypass','-File',resolve('scripts/window-probe.ps1'),'-Handle',String(bgHandle),'-Action','inspect']));
 ps(['-ExecutionPolicy','Bypass','-File',resolve('scripts/capture-widget.ps1'),'-WidgetProcessId',String(background.pid),'-Path',join(out,'background-window.png')]);
 ps(['-ExecutionPolicy','Bypass','-File',resolve('scripts/window-probe.ps1'),'-Handle',String(h),'-Action','top']);
 async function capture(name){
  await delay(1000);
  const native=await invoke('probe_control',{action:'inspect'});
  const rect=JSON.parse(ps(['-ExecutionPolicy','Bypass','-File',resolve('scripts/capture-widget.ps1'),'-WidgetProcessId',String(child.pid),'-Path',join(out,name+'.png')]));
  let patternWindow=null;try{patternWindow=JSON.parse(readFileSync(meta,'utf8').replace(/^\uFEFF/,''));}catch{}
  const item={name,native,rect,brightness:await invoke('backdrop_brightness'),ink:await page.evaluate(()=>document.documentElement.dataset.ink),patternWindow};
  manifest.observations.push(item);log(JSON.stringify(item));
  if(name!=='underneath'&&(native.foreground!==bgHandle||patternWindow?.foreground!==bgHandle))throw Error('Pattern application lost foreground; invalidate this visual observation');
 }
 for(const [name,color] of [['dark','#202020'],['gray','#999999'],['light','#e0e0e0'],['red','#ff0000'],['stripes','stripes'],['bw','bw']]){writeFileSync(control,color);await capture(name);}
 await invoke('probe_control',{action:'marker'});await capture('marker-webview-shown');
 await invoke('probe_control',{action:'webview-hide'});await capture('marker-webview-hidden');
 await invoke('probe_control',{action:'host'});await capture('host-webview-hidden');
 await invoke('probe_control',{action:'webview-show'});
 await invoke('hide_window');await capture('underneath');
 manifest.completed=true;
} catch(error){manifest.error=String(error);process.exitCode=1;}
finally {
 if(page)try{await invoke('probe_exit');}catch{}
 await browser?.close();
 if(background){writeFileSync(control,'exit');await delay(400);if(background.exitCode===null)background.kill();}
 await delay(600);if(child&&child.exitCode===null){child.kill();manifest.forcedProbeCleanup=true;}
 manifest.after={stateHash:hash(realState),startup:startup()};
 manifest.productionStateUnchanged=manifest.before.stateHash===manifest.after.stateHash;
 manifest.productionStartupUnchanged=manifest.before.startup===manifest.after.startup;
 // Do not overwrite user changes while the installed app remains running.
 writeFileSync(join(out,'manifest.json'),JSON.stringify(manifest,null,2));
 log('RUN '+out);
}
