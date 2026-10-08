import { spawn } from 'node:child_process';
import { mkdirSync,readFileSync,writeFileSync,copyFileSync,readdirSync,statSync,openSync,closeSync } from 'node:fs';
import { resolve,join } from 'node:path';
import { createHash } from 'node:crypto';
const kind='tauri';
if(!['native','tauri'].includes(kind))throw Error('Expected native or tauri');
const base=resolve('output/live-capture-release');
const out=join(base,new Date().toISOString().replace(/[:.]/g,'-')+'-build-'+kind);mkdirSync(out,{recursive:true});
const hash=p=>createHash('sha256').update(readFileSync(p)).digest('hex');
const sources=kind==='native'?['diagnostics/native-backdrop/Cargo.toml','diagnostics/native-backdrop/Cargo.lock','diagnostics/native-backdrop/src','.cargo/config.toml']:['src','src-tauri/src','src-tauri/native','src-tauri/icons','src-tauri/capabilities','src-tauri/Cargo.toml','src-tauri/Cargo.lock','src-tauri/tauri.conf.json','src-tauri/tauri.windows.conf.json','src-tauri/build.rs','package.json','package-lock.json','index.html','vite.config.ts','tsconfig.json','.cargo/config.toml'];
function snapshot(from,to){
 if(statSync(from).isDirectory()){mkdirSync(to,{recursive:true});for(const name of readdirSync(from))snapshot(join(from,name),join(to,name));}
 else {mkdirSync(resolve(to,'..'),{recursive:true});writeFileSync(to,readFileSync(from));}
}
for(const p of sources)snapshot(p,join(out,'source',p));
let command,args;
if(kind==='tauri'){
 const config=JSON.parse(readFileSync('src-tauri/tauri.conf.json','utf8'));
 config.bundle.active=true;

 writeFileSync(join(base,'probe-config.json'),JSON.stringify(config,null,2));
 const platform=JSON.parse(readFileSync('src-tauri/tauri.windows.conf.json','utf8'));
 const effective={...config,bundle:{...config.bundle,...platform.bundle,active:true}};
 writeFileSync(join(out,'effective-config.json'),JSON.stringify(effective,null,2));
 command=process.execPath;args=[resolve('node_modules/@tauri-apps/cli/tauri.js'),'build','--config',join(out,'effective-config.json')];
}else{command=join(process.env.USERPROFILE,'.cargo/bin/cargo.exe');args=['build','--manifest-path','diagnostics/native-backdrop/Cargo.toml','--release','--locked','-j','1'];}
const target=resolve('output/playwright/backdrop-runs/build-target');
const report={kind,time:new Date().toISOString(),cwd:process.cwd(),command,args,features:kind==='tauri'?['live-capture (default)']:[],target,jobs:1};
writeFileSync(join(out,'build-manifest.json'),JSON.stringify(report,null,2));
const fd=openSync(join(out,'build.log'),'w');
const child=spawn(command,args,{windowsHide:true,stdio:['ignore',fd,fd],env:{...process.env,PATH:join(process.env.USERPROFILE,'.cargo/bin')+';'+process.env.PATH,CARGO_TARGET_DIR:target}});
const code=await new Promise((res,rej)=>{child.on('error',rej);child.on('exit',res);});closeSync(fd);report.exitCode=code;
if(code===0){const name=kind==='tauri'?'personal-day.exe':'native-backdrop-probe.exe';report.exe=join(out,name);copyFileSync(join(target,'release',name),report.exe);report.sha256=hash(report.exe);writeFileSync(join(base,`latest-${kind}-build.json`),JSON.stringify({...report,manifest:join(out,'build-manifest.json')},null,2));}
writeFileSync(join(out,'build-manifest.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));process.exitCode=code??1;
