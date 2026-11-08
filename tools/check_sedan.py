#!/usr/bin/env python3
"""Checks the emitted GLB's structure, metre dimensions, pivots and finite vertex data."""
import json, math, struct
from pathlib import Path
p=Path(__file__).resolve().parents[1]/'app/src/main/assets/models/sedan.glb'
b=p.read_bytes(); magic,version,size=struct.unpack_from('<III',b)
assert magic==0x46546c67 and version==2 and size==len(b)
js,kind=struct.unpack_from('<II',b,12);assert kind==0x4e4f534a
g=json.loads(b[20:20+js]);bs,kind=struct.unpack_from('<II',b,20+js);assert kind==0x004e4942
binary=b[28+js:];assert len(binary)==bs
for v in g['bufferViews']: assert v.get('byteOffset',0)%4==0 and v.get('byteOffset',0)+v['byteLength']<=bs
for a in g['accessors']:
 v=g['bufferViews'][a['bufferView']]; count=a['count']*3
 vals=struct.unpack_from('<%sf'%count,binary,v.get('byteOffset',0)+a.get('byteOffset',0))
 assert all(math.isfinite(x) for x in vals)
 if 'min' in a:
  for k in range(3):
   assert abs(min(vals[k::3])-a['min'][k])<1e-5
   assert abs(max(vals[k::3])-a['max'][k])<1e-5
names={n['name']:n for n in g['nodes']}
for axle,x in [('rear',0),('front',2.895)]:
 for side,z in [('left',-.83),('right',.83)]:
  assert all(abs(a-b)<1e-9 for a,b in zip(names[f'{axle}_{side}_steer']['translation'],[x,.34,z]))
  assert f'{axle}_{side}_spin' in names
wheel=names['steering_wheel']['translation']
assert wheel[0]>1.16
# The dashboard must lie farther forward than the wheel, never between wheel and driver.
dash=g['meshes'][names['dashboard']['mesh']]
dash_bounds=g['accessors'][dash['primitives'][0]['attributes']['POSITION']]
assert dash_bounds['min'][0]>wheel[0]+.05, 'dashboard obscures steering wheel'

mesh=g['meshes'][names['sculpted_body']['mesh']]
a=g['accessors'][mesh['primitives'][0]['attributes']['POSITION']]
assert abs(a['min'][0]+1.10)<1e-6 and abs(a['max'][0]-3.935)<1e-6
assert abs(a['max'][2]-a['min'][2]-1.88)<1e-6
# No body cap may cut across the passenger compartment at waist level.
v=g['bufferViews'][a['bufferView']]
values=struct.unpack_from('<%sf'%(a['count']*3),binary,v.get('byteOffset',0))
for i in range(0,len(values),9):
 triangle=[values[i+j:i+j+3] for j in (0,3,6)]
 if all(-.25<=p[0]<=1.9 and p[1]>.9 for p in triangle):
  assert not (min(p[2] for p in triangle)<-.5 and max(p[2] for p in triangle)>.5), 'solid cap fills cabin'

for mesh in g['meshes']:
 for prim in mesh['primitives']:
  assert g['accessors'][prim['attributes']['POSITION']]['count']%3==0
  assert g['accessors'][prim['attributes']['POSITION']]['count']==g['accessors'][prim['attributes']['NORMAL']]['count']
print(f'OK: {p.name}, {len(g["nodes"])} nodes; 5.035 x 1.880m body, 2.895m wheelbase; rear-axle pivots valid')
