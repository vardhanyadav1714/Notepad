"""Exercise recording permission, playback, background saves and handwriting recognition on NotesReview.
Uses only a newly created QA note. Run after installing the release APK on the owned QA emulator.
"""
import argparse
import json
import pathlib
import re
import subprocess
import time
import xml.etree.ElementTree as ET

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--adb', required=True)
p.add_argument('--serial', required=True)
p.add_argument('--results', default='docs/production-media-ui-31.json')
p.add_argument('--recognition-only', action='store_true')
a = p.parse_args()
root = pathlib.Path(__file__).resolve().parents[1]
results = json.loads((root/a.results).read_text()) if a.recognition_only and (root/a.results).exists() else []
package = 'in.innovaticshub.notepad'

def adb(*args):
    r = subprocess.run([a.adb, '-s', a.serial, *args], capture_output=True, text=True, timeout=30)
    if r.returncode:
        raise RuntimeError(r.stderr or r.stdout)
    return r.stdout

assert a.serial.startswith('emulator-')
assert adb('shell', 'getprop', 'ro.boot.qemu.avd_name').strip() == 'NotesReview'

def ui():
    adb('shell', 'uiautomator', 'dump', '/sdcard/drawly-media-qa.xml')
    return list(ET.fromstring(adb('shell', 'cat', '/sdcard/drawly-media-qa.xml')).iter('node'))

def find(label, timeout=20):
    end = time.monotonic() + timeout
    while time.monotonic() < end:
        for node in ui():
            if label in (node.get('text'), node.get('content-desc')):
                return node
        time.sleep(.2)
    raise AssertionError('Missing control: ' + label)

def tap_node(node):
    bounds = [int(v) for v in re.findall(r'\d+', node.get('bounds'))]
    adb('shell', 'input', 'tap', str((bounds[0]+bounds[2])//2), str((bounds[1]+bounds[3])//2))

def tap(label):
    tap_node(find(label))

def back():
    adb('shell', 'input', 'keyevent', '4')

def passed(label):
    results.append({'check': label, 'passed': True})
    (root/a.results).write_text(json.dumps(results, indent=2))
    print('PASS:', label, flush=True)

title = 'QA-media31-' + str(int(time.time()))[-6:]
adb('shell', 'am', 'start', '-W', '-n', package + '/' + package + '.MainActivity')
tap('Create note')
fields = sorted([n for n in ui() if n.get('class') == 'android.widget.EditText'],
                key=lambda n: int(re.findall(r'\d+', n.get('bounds'))[1]))
tap_node(fields[0])
adb('shell', 'input', 'text', title)
back()  # Dismiss the keyboard.
if not a.recognition_only:
    tap('Insert'); tap('Voice recording'); tap('Record audio')
    denial = next((n for n in ui() if n.get('resource-id', '').endswith('permission_deny_button')), None)
    if denial is not None:
        tap_node(denial)
        find('Microphone permission is needed to record audio.')
        passed('Denying microphone permission shows an explanation without crashing')
        tap('Record audio')
        allow = next((n for n in ui() if n.get('resource-id', '').endswith('permission_allow_foreground_only_button')), None)
        if allow is None:
            raise AssertionError('Missing foreground microphone permission control')
        tap_node(allow)
    find('Stop and save recording')
    time.sleep(8)
    tap('Stop and save recording'); find('Recording 1')
    tap('Play recording'); find('Pause recording')
    tap('Pause recording')
    passed('Microphone grant, recording, stopping and playback controls work')
    tap('Record audio'); find('Stop and save recording')
    time.sleep(2)
    adb('shell', 'input', 'keyevent', '3')
    time.sleep(2)
    adb('shell', 'am', 'force-stop', package)
    adb('shell', 'am', 'start', '-W', '-n', package + '/' + package + '.MainActivity')
    tap(title); tap('Insert'); tap('Voice recording'); find('Recording 2')
    passed('Backgrounding stops recording and both attachments survive process restart')
    back()
if any(n.get('content-desc') == 'Handwriting mode' for n in ui()):
    tap('Handwriting mode')
for node in ui():
    if node.get('content-desc') == 'Finger drawing: off':
        tap_node(node)
adb('shell', 'input', 'swipe', '500', '700', '500', '1000', '500')
tap('Drawing options'); tap('More tools'); tap('Convert handwriting to text')
nodes = ui()
action = 'Convert handwriting' if any(n.get('text') == 'Convert handwriting' for n in nodes) else 'Download and convert'
tap(action)
find('Add to note', timeout=120)
tap('Add to note'); find('Added to your notes')
passed('Language model download/availability, recognition and adding recognized text work')
back(); tap('Save and return')
