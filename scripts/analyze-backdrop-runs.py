"""Assess synthetic-pattern screen captures; opaque fills cannot pass as blur."""
import json, statistics, pathlib
from PIL import Image, ImageStat

base = pathlib.Path(__file__).resolve().parents[1] / 'output/playwright/backdrop-runs'
invalid = {
    '2026-09-30T04-37-44-208Z-legacy-cold': 'Pattern window not proven visible; screenshots discarded.',
    '2026-09-30T04-38-16-287Z-clear-cold': 'Pattern window not proven visible; screenshots discarded.',
    '2026-09-30T04-41-17-012Z-clear-cold': 'Pattern window hidden; startup IPC error also altered footprint; screenshots discarded.',
    '2026-09-30T04-42-57-387Z-legacy-cold': 'Foreground changed during experiment; screenshots discarded.',
    '2026-09-30T04-44-09-078Z-native': 'Pattern window hidden by process startup show flags; screenshots discarded.',
}
summaries = []
for path in sorted(base.glob('*/manifest.json')):
    folder = path.parent
    data = json.loads(path.read_text(encoding='utf8'))
    if 'observations' not in data:
        continue
    if folder.name in invalid:
        # Only generated captures of invalid runs; do not preserve accidental app content.
        for png in folder.glob('*.png'):
            assert png.resolve().is_relative_to(base.resolve())
            png.unlink()
        report = {'run':folder.name,'valid':False,'reason':invalid[folder.name]}
    elif not data.get('completed'):
        report = {'run':folder.name,'valid':False,'reason':data.get('error','incomplete')}
    else:
        metrics = {}
        for item in data['observations']:
            name = item['name']; png = folder / (name+'.png')
            if not png.exists(): continue
            image = Image.open(png).convert('RGB')
            if 'state' in item:
                x,y,w,h = item['state']['client']; r=item['rect']
                x-=r['x']; y-=r['y']
                image=image.crop((x+12,y+12,x+w-12,y+h-12))
            else:
                image=image.crop((10,10,image.width-10,image.height-10))
            stat=ImageStat.Stat(image)
            gray=image.convert('L')
            profile=[statistics.mean(gray.getpixel((x,y)) for y in range(gray.height)) for x in range(gray.width)]
            metrics[name]={'meanRGB':[round(v,3) for v in stat.mean],
                'meanGray':round(statistics.mean(profile),3),
                'spatialStd':round(statistics.pstdev(profile),3),
                'edgeEnergy':round(statistics.mean(abs(a-b) for a,b in zip(profile,profile[1:])),3)}
        live_delta=abs(metrics.get('light',{}).get('meanGray',0)-metrics.get('dark',{}).get('meanGray',0))
        underlying=metrics.get('underneath',{}).get('spatialStd',0)
        # Tauri's text is not part of the backdrop. Its later host/hidden frame
        # has the same BW pattern behind it, with all WebView foreground hidden.
        background_frame='host-webview-hidden' if 'mode' in data else 'bw'
        variation=metrics.get(background_frame,{}).get('spatialStd',0)
        sharp=metrics.get('underneath',{}).get('edgeEnergy',0)
        blurred=metrics.get(background_frame,{}).get('edgeEnergy',0)
        ratio=blurred/sharp if sharp else None
        foreground=all((o.get('native') or o.get('state'))['foreground']==o['patternWindow' if 'native' in o else 'pattern']['hwnd']==o['patternWindow' if 'native' in o else 'pattern']['foreground'] for o in data['observations'] if o['name']!='underneath' and o.get('patternWindow' if 'native' in o else 'pattern'))
        passes=live_delta>50 and underlying>60 and variation>8 and ratio is not None and .01<ratio<.8 and foreground
        report={'run':folder.name,'valid':underlying>60 and foreground,'nativeOrTauri':'native' if 'args' in data else 'tauri',
            'liveChangeGray':round(live_delta,3),'underlyingStripeStd':underlying,'blurredStripeStd':variation,'backgroundFrame':background_frame,
            'edgeEnergyRatio':round(ratio,4) if ratio is not None else None,'backgroundForeground':foreground,
            'passesLiveBlur':passes,'reason':'No live response / no retained spatial variation' if not passes else 'Pattern response and edge attenuation present',
            'metrics':metrics,'exeSha256':data.get('exeSha256')}
    (folder/'visual-analysis.json').write_text(json.dumps(report,indent=2),encoding='utf8')
    summaries.append({k:v for k,v in report.items() if k!='metrics'})
(base/'visual-summary.json').write_text(json.dumps(summaries,indent=2),encoding='utf8')
print(json.dumps(summaries,indent=2))
