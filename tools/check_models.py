#!/usr/bin/env python3
"""Validate metre-scale vehicle files and moving node pivots against the simulator's dimensions."""
import json, math, struct
from pathlib import Path
root=Path(__file__).resolve().parents[1]/'app/src/main/assets/models'
expected={'avante':(4.710,1.825,2.720,.95),'grandeur':(5.035,1.880,2.895,1.10),'tucson':(4.630,1.865,2.755,.95),'santafe':(4.830,1.900,2.815,1.05)}
for model,(length,width,wb,rear) in expected.items():
 b=(root/(model+'.glb')).read_bytes();magic,version,total=struct.unpack_from('<III',b);assert magic==0x46546c67 and version==2 and total==len(b)
 js,kind=struct.unpack_from('<II',b,12);assert kind==0x4e4f534a;g=json.loads(b[20:20+js]);bs,kind=struct.unpack_from('<II',b,20+js);assert kind==0x004e4942;binary=b[28+js:];assert len(binary)==bs
 p=g['extras'];assert (p['lengthM'],p['widthM'],p['wheelBaseM'],p['rearOverhangM'])==(length,width,wb,rear)
 names={n['name']:n for n in g['nodes']}
 for a in g['accessors']:
  v=g['bufferViews'][a['bufferView']];values=struct.unpack_from('<%sf'%(a['count']*3),binary,v.get('byteOffset',0));assert all(math.isfinite(x) for x in values)
  if 'min' in a:
   for axis in range(3):assert abs(min(values[axis::3])-a['min'][axis])<1e-5 and abs(max(values[axis::3])-a['max'][axis])<1e-5
 body=g['meshes'][names['sculpted_body']['mesh']];bounds=g['accessors'][body['primitives'][0]['attributes']['POSITION']]
 assert abs(bounds['min'][0]+rear)<1e-6 and abs(bounds['max'][0]-(length-rear))<1e-6
 assert abs(bounds['max'][2]-bounds['min'][2]-width)<1e-6
 for axle,x in [('rear',0),('front',wb)]:
  for side,z in [('left',-width/2+.11),('right',width/2-.11)]:
   pivot=names[f'{axle}_{side}_steer']['translation'];assert pivot==[x,p['wheelRadiusM'],z]
   assert f'{axle}_{side}_spin' in names
 wheel=names['steering_wheel']['translation'];assert wheel[0]>p['eyeForwardM'] and wheel[1]<p['eyeHeightM']
 dash=g['meshes'][names['dashboard']['mesh']];a=g['accessors'][dash['primitives'][0]['attributes']['POSITION']];assert a['min'][0]>wheel[0]+.05
 # The dashboard's upper front corner must remain inside the windshield plane.
 window=g['meshes'][names['windshield']['mesh']]
 wa=g['accessors'][window['primitives'][0]['attributes']['POSITION']]
 wv=g['bufferViews'][wa['bufferView']]
 wp=struct.unpack_from('<%sf'%(wa['count']*3),binary,wv.get('byteOffset',0))
 points=[wp[i:i+3] for i in range(0,len(wp),3)]
 bottom=min(points,key=lambda p:p[1]);top=max(points,key=lambda p:p[1])
 if a['max'][1]>bottom[1]:
  windshield_x=bottom[0]+(top[0]-bottom[0])*(a['max'][1]-bottom[1])/(top[1]-bottom[1])
  assert a['max'][0]<windshield_x, 'dashboard crosses windshield'
 assert names['windshield'] and names['rear_window'] and names['room_mirror_housing']
 print(f'OK: {model}, {length:.3f} x {width:.3f} m, roof {p["roofHeightM"]:.2f} m, eye {p["eyeHeightM"]:.2f} m')
