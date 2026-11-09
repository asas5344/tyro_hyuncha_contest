#!/usr/bin/env python3
"""Validate the shared eye-relative cockpit and its clearance to the instrument display."""
import json,math,struct,hashlib
from pathlib import Path
root=Path(__file__).resolve().parents[1]
raw=(root/'app/src/main/assets/models/cockpit.glb').read_bytes()
assert struct.unpack_from('<III',raw)==(0x46546c67,2,len(raw))
size=struct.unpack_from('<I',raw,12)[0];g=json.loads(raw[20:20+size]);binary=raw[28+size:]
for a in g['accessors']:
 v=g['bufferViews'][a['bufferView']]
 assert v.get('byteOffset',0)+v['byteLength']<=len(binary)
 assert all(math.isfinite(x) for x in struct.unpack_from('<%sf'%(a['count']*3),binary,v.get('byteOffset',0)))
nodes={n['name']:n for n in g['nodes']}
wheel=nodes['steering_wheel']['translation']
rim=g['meshes'][nodes['steering_rim']['mesh']]['primitives'][0]
bounds=g['accessors'][rim['attributes']['POSITION']]
# Even the top of the rim must project below the live instrument panel's lower edge.
assert -(wheel[1]+bounds['max'][1])/(wheel[0]+bounds['max'][0]) > .165/.595
assert wheel[2]==-.37 and g['extras']['sharedAcrossVehicles']
profiles=json.loads((root/'app/src/main/assets/models/vehicle_visuals.json').read_text())
heights={p['id']:p['eyeHeightM'] for p in profiles}
assert max(heights['avante'],heights['grandeur'])<min(heights['tucson'],heights['santafe'])
# The approved exterior and its physical / camera profiles must remain intact.
manifest=json.loads((root/'verification/astra/baseline/manifest.json').read_text())
for name,digest in manifest.items():
 if name.startswith('app/'):
  assert hashlib.sha256((root/name).read_bytes()).hexdigest()==digest,name
print('OK: shared cockpit, finite geometry, instrument clearance, sedan/SUV heights, approved exterior preserved')
