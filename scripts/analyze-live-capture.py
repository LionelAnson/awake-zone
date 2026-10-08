"""Analyze only persisted synthetic-pattern captures from the isolated clock."""
from pathlib import Path
import json
import numpy as np
from PIL import Image

root=Path(__file__).resolve().parents[1]
run=Path((root/'output/live-capture/latest.txt').read_text(encoding='utf-8-sig').strip())
data=json.loads((run/'result.json').read_text(encoding='utf-8'))
assert data.get('completed'), data.get('error')
rounds={r['pattern']:r for r in data['rounds']}
def pixels(pattern,kind='dd'):
    return np.array(Image.open(rounds[pattern]['prefix']+'-'+kind+'.ppm')).astype(float)
checks={}
for pattern in ['#101010','#808080','#f0f0f0','#ff0000','#00ff00','#0000ff']:
    color=np.array([int(pattern[i:i+2],16) for i in (1,3,5)])
    checks['gray-veil-'+pattern]=bool(np.abs(pixels(pattern)-(color*.9+216*.1)).mean()<2)
fine=pixels('bw-8-0');coarse=pixels('bw-40-0')
checks['fine-stripes-attenuated']=bool(fine.mean((0,2)).std()<15)
checks['coarse-structure-retained']=bool(coarse.mean((0,2)).std()>80)
checks['coarse-edges-blurred']=bool(.15<((coarse[:,:,0]>16)&(coarse[:,:,0]<239)).mean()<.85)
checks['stripe-phase-response']=bool(np.abs(coarse-pixels('bw-40-20')).mean()>40)
checks['rgb-phase-response']=bool(np.abs(pixels('rgb-80-0')-pixels('rgb-80-40')).mean()>30)
checks['desktop-backends-agree']=all(np.abs(pixels(p)-pixels(p,'gdi')).mean()<1 for p in rounds)
checks['dark-white-ink']=rounds['#101010']['ink']=='white'
checks['gray-light-black-ink']=all(rounds[p]['ink']=='black' for p in ['#808080','#f0f0f0'])
checks['native-no-error']=all(r['status']['error']==0 for r in rounds.values())
checks['capture-paused-when-hidden']=data['hiddenCapturePaused']
checks['desktop-roundtrip']=data.get('desktopRoundtrip',False)
for f in run.glob('*.ppm'):
    Image.open(f).save(f.with_suffix('.png'))
report={'checks':checks,'passed':all(checks.values()),'physicalHumanVerification':False,
        'fineColumnStd':float(fine.mean((0,2)).std()),'coarseColumnStd':float(coarse.mean((0,2)).std())}
(run/'analysis.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps(report,indent=2))
if not report['passed']:raise SystemExit(1)
