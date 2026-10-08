import { chromium } from 'playwright';
import { spawn, execFileSync } from 'node:child_process';
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { resolve, join } from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';
import assert from 'node:assert/strict';
const baseline=process.argv.includes('--baseline');
const build=JSON.parse(readFileSync('output/hotkey-toggle/latest-tauri-build.json','utf8'));
const out=resolve('output/placement-045',`${baseline?'baseline':'fixed'}-${Date.now()}`);mkdirSync(out,{recursive:true});
writeFileSync(join(out,'state.json'),JSON.stringify({preferences:{wake:'08:00',sleep:'00:00',theme:'system',desktopMode:false,alwaysOnTop:true,positionLocked:false,widgetSize:'six',startAtLogin:false},position:[500,300],anchor:{monitor:'\\\\.\\DISPLAY1',right:24,bottom:24}}));
const ps=args=>execFileSync('powershell.exe',['-NoProfile',...args],{encoding:'utf8',windowsHide:true}).trim();
const action=(h,a,...rest)=>JSON.parse(ps(['-ExecutionPolicy','Bypass','-File',resolve('scripts/window-probe.ps1'),'-Handle',String(h),'-Action',a,...rest]));
const areas=JSON.parse(ps(['-Command',`Add-Type -TypeDefinition 'using System; using System.Runtime.InteropServices; public class Dpi { [DllImport("user32.dll")] public static extern bool SetProcessDpiAwarenessContext(IntPtr p); }'; [void][Dpi]::SetProcessDpiAwarenessContext([IntPtr]::new(-4)); Add-Type -AssemblyName System.Windows.Forms; [System.Windows.Forms.Screen]::AllScreens | ForEach-Object { @{x=$_.WorkingArea.X;y=$_.WorkingArea.Y;width=$_.WorkingArea.Width;height=$_.WorkingArea.Height} } | ConvertTo-Json -Compress`]));
const area=Array.isArray(areas)?areas[0]:areas;
const result={build,area,baseline,checks:[]};let browser,page;
const child=spawn(build.exe,[],{windowsHide:true,stdio:'ignore',env:{...process.env,PD_PROBE_ROOT:out,PD_PROBE_HOTKEY:'alternate',WEBVIEW2_ADDITIONAL_BROWSER_ARGUMENTS:'--remote-debugging-port=9224'}});
const invoke=(cmd,args={})=>page.evaluate(({cmd,args})=>window.__TAURI_INTERNALS__.invoke(cmd,args),{cmd,args});
try{
 for(let i=0;i<60;i++){try{browser=await chromium.connectOverCDP('http://127.0.0.1:9224');break;}catch{await delay(500);}}
 for(let i=0;i<60&&!page;i++){for(const p of browser.contexts().flatMap(c=>c.pages()))try{if(await p.evaluate(()=>document.documentElement.dataset.page==='widget'))page=p;}catch{}if(!page)await delay(100);}
 await page.locator('#personal-time').filter({hasText:/\d{2}:\d{2}/}).waitFor();await delay(1000);
 const h=(await invoke('platform_info')).windowHandle;
 const initial=action(h,'inspect');
 const scenarios=[['right-edge',area.x+area.width-initial.width+12,area.y+200],['bottom-edge',area.x+400,area.y+area.height-initial.height+12],['inaccessible',area.x+area.width+400,area.y+200]];
 for(const [name,x,y] of scenarios){
  action(h,'move','-X',String(x),'-Y',String(y));await delay(4500);const actual=action(h,'inspect');
  result.checks.push({name,requested:{x,y},actual});
  if(baseline){assert(Math.abs(actual.x-(area.x+Math.floor((area.width-actual.width)/2)))<=1);assert(Math.abs(actual.y-(area.y+Math.floor((area.height-actual.height)/2)))<=1);}
  else {assert.equal(actual.x,name==='inaccessible'?area.x+area.width-actual.width:x);assert.equal(actual.y,y);}
 }
 if(!baseline){
  action(h,'move','-X','500','-Y','300');await delay(300);
  const before=action(h,'inspect');const released=action(h,'drag','-X','80','-Y','40');await delay(4500);const settled=action(h,'inspect');
  result.checks.push({name:'native-mouse-drag-stable',before,released,settled});
  assert(released.x>before.x+30);assert(released.y>before.y+10);
  assert.equal(settled.x,released.x);assert.equal(settled.y,released.y);
  const glass=await invoke('live_glass_status');result.glass=glass;assert.equal(glass.borderlessAccess,4);assert.equal(glass.borderRequired,0);
 }
 result.passed=true;
}catch(e){result.error=String(e);process.exitCode=1;}
finally{try{await invoke('probe_exit');}catch{}await browser?.close();await delay(500);if(child.exitCode===null)child.kill();writeFileSync(join(out,'result.json'),JSON.stringify(result,null,2));console.log(JSON.stringify({out,passed:result.passed,error:result.error,checks:result.checks},null,2));}
