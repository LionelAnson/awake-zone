import ctypes as c,time,json,os,pathlib,subprocess,threading,shutil,datetime,argparse
ap=argparse.ArgumentParser();ap.add_argument('--label',default='baseline');args=ap.parse_args()
root=pathlib.Path.cwd(); build=json.loads((root/'output/hotkey-toggle/latest-tauri-build.json').read_text(encoding='utf-8'));out=root/'output/hotkey-reliability'/(args.label+'-'+datetime.datetime.now().strftime('%H%M%S'));out.mkdir(parents=True)
state={'preferences':{'wake':'08:00','sleep':'00:00','theme':'system','desktopMode':True,'alwaysOnTop':False,'positionLocked':True,'widgetSize':'six','startAtLogin':False},'position':[1560,800],'anchor':{'monitor':'\\\\.\\DISPLAY1','right':24,'bottom':24}}
p=out/'state.json';p.write_text(json.dumps(state),encoding='utf-8');exe=out/'personal-day.exe';shutil.copyfile(build['exe'],exe)
env=dict(os.environ,PD_PROBE_ROOT=str(out),PD_PROBE_HOTKEY='alternate');proc=subprocess.Popen([str(exe)],env=env);time.sleep(4)
u=c.windll.user32
records=[];running=True
start=time.monotonic()
def monitor():
 last=None
 while running:
  try:
   mode=json.loads(p.read_text(encoding='utf-8'))['preferences']['desktopMode']
   if mode!=last:records.append({'time':time.monotonic()-start,'desktop':mode});last=mode
  except:pass
  time.sleep(.004)
th=threading.Thread(target=monitor);th.start();sent=[]
def key(k,up=False):u.keybd_event(k,0,2 if up else 0,0)
try:
 for stage,n,gap,heldmods in [('normal',10,.4,False),('rapid',20,.06,False),('held-modifiers',20,.1,True)]:
  if heldmods:
   for k in [0x11,0x10,0x12]:key(k)
  for i in range(n):
   if not heldmods:
    for k in [0x11,0x10,0x12]:key(k)
   sent.append({'time':time.monotonic()-start,'stage':stage,'i':i});key(0x5a);time.sleep(.04);key(0x5a,True)
   if not heldmods:
    for k in [0x12,0x10,0x11]:key(k,True)
   time.sleep(gap)
  if heldmods:
   for k in [0x12,0x10,0x11]:key(k,True)
  time.sleep(4)
 result={'build':build,'sent':sent,'transitions':records,'expected':len(sent),'observed':len(records)-1};(out/'result.json').write_text(json.dumps(result,indent=2),encoding='utf-8');print(json.dumps({'out':str(out),'expected':len(sent),'observed':len(records)-1}))
finally:
 for k in [0x5a,0x12,0x10,0x11]:key(k,True)
 running=False;th.join();proc.terminate();proc.wait()