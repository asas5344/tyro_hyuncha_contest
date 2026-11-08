#!/usr/bin/env python3
"""Keep approved car assets intact; replace only own-car mirror geometry in driver variants."""
import json,math,struct,hashlib
from pathlib import Path
from glb_geometry import Glb

root=Path(__file__).resolve().parents[1];out=root/'app/src/main/assets/models'
for profile in json.loads((out/'vehicle_visuals.json').read_text()):
 source=out/profile['asset'];raw=source.read_bytes();n=struct.unpack_from('<I',raw,12)[0]
 g=json.loads(raw[20:20+n]);data=bytearray(raw[28+n:]);w=profile['widthM']/2
 mx=profile['mirrorForwardM'];my=profile['mirrorHeightM'];mz=profile['mirrorRightM'];removed=0
 def values(index):
  a=g['accessors'][index];v=g['bufferViews'][a['bufferView']]
  return struct.unpack_from('<%sf'%(a['count']*3),data,v.get('byteOffset',0)+a.get('byteOffset',0))
 def append(values):
  while len(data)%4:data.append(0)
  offset=len(data);data.extend(struct.pack('<%sf'%len(values),*values))
  vi=len(g['bufferViews']);g['bufferViews'].append({'buffer':0,'byteOffset':offset,'byteLength':len(values)*4})
  ai=len(g['accessors']);g['accessors'].append({'bufferView':vi,'componentType':5126,'count':len(values)//3,'type':'VEC3'})
  return ai
 emptied=set()
 for mi,mesh in enumerate(g['meshes']):
  for primitive in mesh['primitives']:
   attr=primitive['attributes'];p=values(attr['POSITION']);keep=[]
   for i in range(0,len(p),9):
    triangle=[p[j:j+3] for j in range(i,i+9,3)]
    mirror=all(abs(x-mx)<.48 and abs(y-my)<.22 and abs(z)<mz+.25 for x,y,z in triangle) and any(abs(z)>w+.005 for x,y,z in triangle)
    if mirror:removed+=1
    else:keep.extend(range(i,i+9))
   if len(keep)==len(p):continue
   if not keep:emptied.add(mi);continue
   # Existing generated meshes are non-indexed triangles, with POSITION and NORMAL only.
   assert set(attr)=={'POSITION','NORMAL'} and 'indices' not in primitive
   for semantic,index in list(attr.items()):
    v=values(index);new=[v[i] for i in keep];ai=append(new);attr[semantic]=ai
    if semantic=='POSITION':g['accessors'][ai].update(min=[min(new[k::3]) for k in range(3)],max=[max(new[k::3]) for k in range(3)])
 for node in g['nodes']:
  if node.get('mesh') in emptied:node.pop('mesh')
 assert removed>0
 g['buffers'][0]['byteLength']=len(data)
 g['extras']['driverVariantOf']=profile['asset'];g['extras']['sourceSha256']=hashlib.sha256(raw).hexdigest()
 j=json.dumps(g,separators=(',',':'),ensure_ascii=False).encode();j+=b' '*(-len(j)%4)
 dest=out/(source.stem+'_driver.glb')
 dest.write_bytes(struct.pack('<III',0x46546c67,2,28+len(j)+len(data))+struct.pack('<II',len(j),0x4e4f534a)+j+struct.pack('<II',len(data),0x004e4942)+data)
 print(dest.name,': removed',removed,'old mirror triangles; approved source unchanged')
 stalk=Glb()
 for side in [-1,1]:
  center_x=mx-.18
  yaw=math.atan2(side*mz+.37,center_x-profile['eyeForwardM'])
  start=(center_x+.08*math.cos(yaw),my+.08-.076,side*mz+.08*math.sin(yaw))
  end=(center_x+.10,profile['bodyHeightM']-.02,side*w*.90)
  stalk.beam('mirror_stalk',start,end,.022,2)
 stalk.save(out/(source.stem+'_mirror_stalks.glb'),{'raisedCenterM':.08})


# One consistent landscape contour for both video glass and its housing. +X is behind the glass.
g=Glb();width=.25;height=width*9/16
def ring(x,sx,sy):
 points=[]
 for i in range(64):
  a=i*math.tau/64;c=math.cos(a);s=math.sin(a)
  points.append((x,math.copysign(math.sqrt(abs(s)),s)*height/2*sy,math.copysign(math.sqrt(abs(c)),c)*width/2*sx))
 return points
inner=ring(0,1,1);outer=ring(0,1.08,1.13)
g.mesh('mirror_bezel',[[inner[i],outer[i],outer[(i+1)%64],inner[(i+1)%64]] for i in range(64)],2,merge=False,smooth=True)
rings=[outer,ring(.025,1.14,1.18),ring(.085,1.06,1.08),ring(.13,.65,.65),ring(.145,.02,.02)]
upper=[];lower=[]
for a,b in zip(rings,rings[1:]):
 for i in range(64):
  face=[a[i],b[i],b[(i+1)%64],a[(i+1)%64]]
  (upper if sum(p[1] for p in face)/4>-.018 else lower).append(face)
g.mesh('mirror_housing_white',upper,0,merge=False,smooth=True)
g.mesh('mirror_housing_lower',lower,2,merge=False,smooth=True)
g.save(out/'driver_mirror.glb',{'glassWidthM':width,'glassHeightM':height,'contour':'superellipse exponent 4','normal':[-1,0,0]})
