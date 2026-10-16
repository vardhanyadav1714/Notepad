"""Opt-in live QA with two ADB devices running matching Notes Preview and test APKs.

Uses isolated in-memory documents; never reads or modifies the user's library.
Example: python tools/verify-live-collaboration.py --adb PATH --phone SERIAL --tablet SERIAL
"""
import json, subprocess, time, pathlib, secrets, concurrent.futures, argparse
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--adb',required=True)
parser.add_argument('--phone',required=True)
parser.add_argument('--tablet',required=True)
args=parser.parse_args()
if args.phone==args.tablet:parser.error('Two distinct devices are required')
ROOT=pathlib.Path(__file__).resolve().parents[1]
(ROOT/'.ui-review').mkdir(exist_ok=True)
ADB=args.adb
DEV={'phone':args.phone,'tablet':args.tablet}
REMOTE='/sdcard/Android/data/in.innovaticshub.notepad.preview/files/collaboration-review/'
results=[]
processes={}
def adb(device,*args):
 r=subprocess.run([ADB,'-s',DEV[device],*args],capture_output=True,text=True,timeout=25)
 if r.returncode: raise RuntimeError(r.stderr or r.stdout)
 return r.stdout

def state(device):
 raw=adb(device,'shell','cat',REMOTE+'state.json')
 s=json.loads(raw)
 if s.get('failure'):raise AssertionError(s['failure'])
 return s

def wait(predicate,description,timeout=25):
 end=time.monotonic()+timeout
 error=None
 while time.monotonic()<end:
  try:
   if predicate():return
  except (RuntimeError,ValueError,AssertionError) as e:error=e
  time.sleep(.25)
 raise AssertionError(description+': '+str(error))

def command(device,action,**kwargs):
 ident=secrets.token_hex(5)
 file=ROOT/'.ui-review'/f'pair-{device}-command.json'
 file.write_text(json.dumps(dict(id=ident,action=action,**kwargs)))
 adb(device,'push',str(file),REMOTE+'command.tmp')
 adb(device,'shell','mv',REMOTE+'command.tmp',REMOTE+'command.json')
 wait(lambda:state(device)['command']==ident,f'{device} {action}')

def same(count):
 a,b=state('phone'),state('tablet')
 if len(a['strokes'])!=count or len(b['strokes'])!=count:return False
 # Exact stored geometry, ink settings, IDs and page assignments must agree.
 def ink(s):return sorted(s['strokes'],key=lambda x:x['id'])
 assert len({s['id'] for s in a['strokes']})==count,'Duplicate ink after replay'
 return ink(a)==ink(b)

def check(label,count):
 wait(lambda:same(count),label)
 results.append(dict(check=label,passed=True,strokes=count))
 print('PASS:',label,flush=True)
 (ROOT/'.ui-review/live-pair-results.json').write_text(json.dumps(results,indent=2))

room='qa-'+secrets.token_hex(8)
(ROOT/'.ui-review/live-automated-room.txt').write_text(room)
try:
 for device in DEV:
  adb(device,'shell','rm','-f',REMOTE+'state.json')
  log=open(ROOT/f'build-collaboration-live-{device}.log','w')
  processes[device]=subprocess.Popen([ADB,'-s',DEV[device],'shell','am','instrument','-w','-e','class','in.innovaticshub.notepad.LiveCollaborationDeviceTest','-e','liveCollabRoom',room,'in.innovaticshub.notepad.preview.test/androidx.test.runner.AndroidJUnitRunner'],stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
 wait(lambda:all(state(d)['connected'] for d in DEV),'Both live WebSockets connect',40)
 print('Both Samsung devices connected to isolated live QA room',flush=True)
 command('phone','draw');check('Phone to tablet: exact pen settings, pressure, tilt and geometry',1)
 command('tablet','draw',y=500);check('Tablet to phone: exact ink and geometry',2)
 guest_id=state('tablet')['strokes'][-1]['id']
 command('phone','undo');check('Undo preserves the other participant ink',1)
 assert state('phone')['strokes'][0]['id']==guest_id
 command('phone','redo');check('Redo reaches the other device',2)
 command('tablet','move');check('Selection movement synchronizes',2)
 command('phone','draw',newPage=True,y=600);check('Ink on a second page stays on that page',3)
 assert len({s['pageId'] for s in state('tablet')['strokes']})==2
 command('tablet','erase');check('Stroke eraser synchronizes',2)
 command('tablet','undo');check('Undo eraser restores remote ink',3)
 command('tablet','leave');command('phone','draw',y=800)
 command('tablet','join');check('Rejoin recovers missed strokes without duplicates',4)
 command('tablet','interrupt')
 wait(lambda:not state('tablet')['connected'],'Transport interruption detected')
 command('tablet','draw',y=1000)
 command('tablet','resume');check('Offline ink is retained and sent after automatic reconnect',5)
 with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
  futures=[pool.submit(command,'phone','draw',y=1100),pool.submit(command,'tablet','draw',y=1200)]
  for future in futures:future.result()
 check('Simultaneous ink additions converge',7)
 command('tablet','freshJoin');check('A fresh client recovers all ink from server storage',7)
 for d in DEV:command(d,'finish')
 for d,p in processes.items():
  p.wait(timeout=20)
  text=(ROOT/f'build-collaboration-live-{d}.log').read_text()
  assert 'OK (1 test)' in text,text
 print('PASS: both device instrumentation sessions completed',flush=True)
except Exception as e:
 print('FAIL:',str(e),flush=True)
 for d in DEV:
  try:
   s=state(d);(ROOT/f'.ui-review/live-pair-failure-{d}.json').write_text(json.dumps(s,indent=2))
   command(d,'finish')
  except Exception:pass
 raise
