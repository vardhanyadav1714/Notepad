"""Verify optimized APK UI on an explicitly selected, isolated QA emulator.
Requires adb root on that emulator for inspection of the newly created QA document only.
Never use against a physical device or a shared/user emulator.
"""
import argparse,pathlib,subprocess,time,re,json,xml.etree.ElementTree as ET
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--adb',required=True);p.add_argument('--serial',required=True);p.add_argument('--results',default='docs/play-release-ui-results-30.json');a=p.parse_args()
assert a.serial.startswith('emulator-'),'This probe is restricted to an owned QA emulator'
root=pathlib.Path(__file__).resolve().parents[1]
results=[]
def adb(*args):
 r=subprocess.run([a.adb,'-s',a.serial,*args],capture_output=True,text=True,timeout=30)
 if r.returncode:raise RuntimeError(r.stderr or r.stdout)
 return r.stdout
assert adb('shell','getprop','ro.kernel.qemu').strip()=='1','Not an emulator'
def ui():
 adb('shell','uiautomator','dump','/sdcard/drawly-release-probe.xml')
 return list(ET.fromstring(adb('shell','cat','/sdcard/drawly-release-probe.xml')).iter('node'))
def find(label,timeout=20):
 end=time.monotonic()+timeout
 while time.monotonic()<end:
  for n in ui():
   if n.get('text')==label or n.get('content-desc')==label:return n
  time.sleep(.2)
 raise AssertionError('Control missing: '+label)
def tap_node(n):
 b=[int(v) for v in re.findall(r'\d+',n.get('bounds'))]
 adb('shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2))
def tap(label):tap_node(find(label))
def passed(label):
 results.append(dict(check=label,passed=True));print('PASS:',label,flush=True)
 (root/a.results).write_text(json.dumps(results,indent=2))
def back():adb('shell','input','keyevent','4')
title='QA31-'+str(int(time.time()))[-6:]
adb('shell','am','start','-W','-n','in.innovaticshub.notepad/in.innovaticshub.notepad.MainActivity')
tap('Create note')
fields=sorted([n for n in ui() if n.get('class')=='android.widget.EditText'],key=lambda n:int(re.findall(r'\d+',n.get('bounds'))[1]))
assert fields,'Missing title field'
tap_node(fields[0]);ui();time.sleep(.4)
adb('shell','input','text',title);find(title)
tap('Handwriting mode')
for n in ui():
 if n.get('content-desc')=='Finger drawing: off':tap_node(n);break
adb('shell','input','swipe','250','700','750','900','400')
assert find('Undo').get('enabled')=='true'
tap('Undo');assert find('Redo').get('enabled')=='true';tap('Redo')
passed('Optimized app launches, creates a note, draws, and supports undo/redo')
tap('Insert');tap('Add page');time.sleep(.8)
adb('shell','input','swipe','250','700','750','900','400')
passed('Adds a second page and writes on it')
tap('Drawing options');tap('More tools');tap('Convert handwriting to text');find('Handwriting to text');find('English');back()
passed('Handwriting recognition sheet and language controls load')
tap('Drawing options');tap('Full screen');find('Exit full screen');back();find('Drawing options')
passed('Full screen and Back restore editor controls')
tap('Collaboration');tap('Create room');find('Connected');back()
tap('Save and return');tap(title)
tap('Collaboration');find('Connected')
passed('Live collaboration connects and reconnects after reopening')
files=adb('shell','ls','-t','/data/data/in.innovaticshub.notepad/files/drawings').splitlines()
assert files and re.fullmatch(r'[a-f0-9-]+\.json',files[0])
s=json.loads(adb('shell','cat','/data/data/in.innovaticshub.notepad/files/drawings/'+files[0]))
assert s['title']==title,'Refusing to inspect an unexpected note'
assert len(s['pages'])==2 and len(s['strokes'])==2
assert len({x['id'] for x in s['strokes']})==2
assert len({x['pageId'] for x in s['strokes']})==2
json.dumps(s,allow_nan=False)
passed('Saved and reopened document preserves two unique strokes on separate pages')
tap('Leave room');back();tap('Save and return')
tap('Open navigation');tap('Settings')
adb('shell','input','swipe','500','1700','500','700','350')
find('Privacy policy');find('Contact support')
tap('Drawly');find('About Drawly');tap('Close');tap('Back to notes')
passed('Settings exposes privacy policy, support contact and app information')
print('All optimized-release UI checks passed',flush=True)
