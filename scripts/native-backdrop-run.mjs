import { spawn,execFileSync } from 'node:child_process';
import { readFileSync,writeFileSync,mkdirSync,copyFileSync } from 'node:fs';
import { resolve,join } from 'node:path';
import { createHash } from 'node:crypto';
import { setTimeout as delay } from 'node:timers/promises';
const root=resolve('output/playwright/backdrop-runs');
const args=process.argv.slice(2),id=new Date().toISOString().replace(/[:.]/g,'-')+'-native'+args.join('-');
const out=join(root,id);mkdirSync(out,{recursive:true});
const built=JSON.parse(readFileSync(join(root,'latest-native-build.json'),'utf8'));
const sourceExe=built.exe;
const exe=join(out,'native-backdrop-probe.exe');copyFileSync(sourceExe,exe);
const hash=p=>createHash('sha256').update(readFileSync(p)).digest('hex');
const report={id,args,exe,exeSha256:hash(exe),command:['node','scripts/native-backdrop-run.mjs',...args],observations:[]};
report.buildManifest=built.manifest;copyFileSync(built.manifest,join(out,'build-manifest.json'));
for(const p of ['diagnostics/native-backdrop/Cargo.toml','diagnostics/native-backdrop/Cargo.lock','diagnostics/native-backdrop/src/main.rs','.cargo/config.toml'])copyFileSync(p,join(out,p.replaceAll('/','_')));
const ps=a=>execFileSync('powershell.exe',['-NoProfile',...a],{encoding:'utf8',windowsHide:true}).trim();
let native,background;
const read=p=>JSON.parse(readFileSync(join(out,p),'utf8').replace(/^\uFEFF/,''));
const control=join(out,'pattern.txt'),meta=join(out,'pattern-window.json');writeFileSync(control,'#e0e0e0');
async function snapshot(name){
 await delay(1100);const state=read('native-state.json');
 const rect=JSON.parse(ps(['-ExecutionPolicy','Bypass','-File',resolve('scripts/capture-widget.ps1'),'-WidgetProcessId',String(native.pid),'-Path',join(out,name+'.png')]));
 const pattern=read('pattern-window.json');
 report.observations.push({name,state,rect,pattern});
 console.log(JSON.stringify({name,state,pattern}));
 if(name!=='underneath'&&(state.foreground!==pattern.hwnd||pattern.foreground!==pattern.hwnd))throw Error('Background test window must remain foreground');
}
try{
 native=spawn(exe,[out,...args],{windowsHide:true,stdio:['ignore','pipe','pipe']});
 native.stderr.on('data',d=>writeFileSync(join(out,'stderr.log'),d,{flag:'a'}));
 for(let i=0;i<50;i++){try{read('native-state.json');break;}catch{await delay(200);}}
 const h=read('native-state.json').hwnd;
 background=spawn('powershell.exe',['-NoProfile','-ExecutionPolicy','Bypass','-File',resolve('scripts/backdrop-pattern-window.ps1'),'-ControlFile',control,'-MetadataFile',meta],{windowsHide:true,stdio:['ignore','pipe','pipe']});
 background.stderr.on('data',d=>writeFileSync(join(out,'background-stderr.log'),d,{flag:'a'}));
 await delay(1800);
 const bg=read('pattern-window.json').hwnd;
 ps(['-ExecutionPolicy','Bypass','-File',resolve('scripts/window-probe.ps1'),'-Handle',String(bg),'-Action','focus-test-app']);
 ps(['-ExecutionPolicy','Bypass','-File',resolve('scripts/window-probe.ps1'),'-Handle',String(h),'-Action','top']);
 await snapshot('marker');
 writeFileSync(join(out,'native-control.txt'),'host');
 for(const [name,color] of [['dark','#202020'],['gray','#999999'],['light','#e0e0e0'],['red','#ff0000'],['stripes','stripes'],['bw','bw']]){writeFileSync(control,color);await snapshot(name);}
 writeFileSync(join(out,'native-control.txt'),'hide');await snapshot('underneath');
 report.completed=true;
}catch(e){report.error=String(e);process.exitCode=1;}
finally{
 writeFileSync(join(out,'native-control.txt'),'exit');writeFileSync(control,'exit');await delay(500);
 if(native?.exitCode===null){native.kill();report.nativeForcedCleanup=true;}
 if(background?.exitCode===null){background.kill();report.backgroundForcedCleanup=true;}
 report.nativeExitCode=native?.exitCode;
 writeFileSync(join(out,'manifest.json'),JSON.stringify(report,null,2));console.log('RUN '+out);
}
